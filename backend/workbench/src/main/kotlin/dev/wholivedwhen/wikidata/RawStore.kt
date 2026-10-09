package dev.wholivedwhen.wikidata

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/** The steps of an import, in order. */
enum class ImportPhase { DISCOVER, PEOPLE, LINKED, DONE }

/** The entities an import fetches: people first, then what their claims link to. */
enum class EntityKind { PEOPLE, LINKED }

data class ImportRun(val id: Long, val startedAt: Instant, val phase: ImportPhase, val notBefore: Instant?) {
    val started: LocalDate get() = startedAt.atOffset(ZoneOffset.UTC).toLocalDate()
}

/** What one batch of entities came to. */
data class EntityBatch(
    val ids: List<String>,
    val requests: Int,
    val fetched: List<JsonNode>,
    val unchanged: List<String>,
    val missing: List<String>,
)

/**
 * The workbench's schema `raw`: what it fetched from Wikimedia, and how far each import got. Its own migrations
 * (`db/raw`), apart from the release schema's: the release is built again on every start, this is never dropped.
 */
@Component
class RawStore(dataSource: DataSource, private val transactions: TransactionTemplate) {

    private val jdbc = JdbcTemplate(dataSource)

    init {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/raw")
            .schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(true)
            .load().migrate()
    }

    /** The import that has not finished, if one stopped half way, else a new one. */
    fun currentRun(): ImportRun =
        jdbc.query("select * from raw.import_run where finished_at is null order by id desc limit 1", ::toRun).firstOrNull()
            ?: jdbc.queryForObject("insert into raw.import_run (phase) values (?) returning *", ::toRun, ImportPhase.DISCOVER.name)!!

    fun findRun(id: Long): ImportRun = jdbc.queryForObject("select * from raw.import_run where id = ?", ::toRun, id)!!

    fun counts(run: ImportRun): Map<String, Any?> =
        jdbc.queryForMap("select requests, busy, fetched, unchanged, missing from raw.import_run where id = ?", run.id)

    fun countRequest(run: ImportRun) {
        jdbc.update("update raw.import_run set requests = requests + 1 where id = ?", run.id)
    }

    /** Wikimedia asked to wait [wait]: another request, and a wait any resumed run honours too. */
    fun busy(run: ImportRun, wait: Duration) {
        jdbc.update(
            """
            update raw.import_run set requests = requests + 1, busy = busy + 1,
                not_before = greatest(not_before, now() + ? * interval '1 millisecond')
            where id = ?
            """.trimIndent(),
            wait.toMillis(), run.id,
        )
    }

    fun doneSlices(run: ImportRun): List<BirthSlice> =
        jdbc.query("select born_from, born_until from raw.discovery_slice where run_id = ?", { rs, _ ->
            BirthSlice(LocalDate.parse(rs.getString(1)), LocalDate.parse(rs.getString(2)))
        }, run.id)

    /** Stores the people one slice found, with the slice, so a resumed run does not ask for it again. */
    fun saveSlice(run: ImportRun, slice: BirthSlice, people: Map<String, Int>) {
        transactions.executeWithoutResult {
            jdbc.batchUpdate(
                """
                insert into raw.discovered (qid, sitelinks, discovered_at, seen_at) values (?, ?, now(), now())
                on conflict (qid) do update set sitelinks = excluded.sitelinks, seen_at = excluded.seen_at
                """.trimIndent(),
                people.map { (qid, sitelinks) -> arrayOf<Any>(qid, sitelinks) },
            )
            jdbc.update(
                "insert into raw.discovery_slice (run_id, born_from, born_until, people) values (?, ?, ?, ?)",
                run.id, slice.from.toString(), slice.until.toString(), people.size,
            )
            jdbc.update("update raw.import_run set requests = requests + 1 where id = ?", run.id)
        }
    }

    fun discoveredCount(run: ImportRun): Int =
        jdbc.queryForObject("select count(*) from raw.discovered where seen_at >= ?", Int::class.java, run.startedAt.utc())!!

    /** Discovery is done: every person it found this run is to be fetched, or checked for a new revision. */
    fun queuePeople(run: ImportRun) = transactions.executeWithoutResult {
        jdbc.update(
            """
            insert into raw.import_item (run_id, qid, kind)
            select ?, qid, ? from raw.discovered where seen_at >= ?
            on conflict do nothing
            """.trimIndent(),
            run.id, EntityKind.PEOPLE.name, run.startedAt.utc(),
        )
        phase(run, ImportPhase.PEOPLE)
    }

    /** The people are fetched: the entities their claims in [properties] point to are next. */
    fun queueLinked(run: ImportRun, properties: List<String>) = transactions.executeWithoutResult {
        jdbc.update(
            """
            insert into raw.import_item (run_id, qid, kind)
            select distinct i.run_id, claim -> 'mainsnak' -> 'datavalue' -> 'value' ->> 'id', ?
            from raw.import_item i
            join raw.entity e on e.qid = i.qid
            cross join unnest(string_to_array(?, ',')) as p(property)
            cross join jsonb_array_elements(coalesce(e.json -> 'claims' -> p.property, '[]')) as claim
            where i.run_id = ? and i.kind = ? and claim -> 'mainsnak' ->> 'snaktype' = 'value'
            on conflict do nothing
            """.trimIndent(),
            EntityKind.LINKED.name, properties.joinToString(","), run.id, EntityKind.PEOPLE.name,
        )
        phase(run, ImportPhase.LINKED)
    }

    fun finish(run: ImportRun) {
        jdbc.update("update raw.import_run set phase = ?, finished_at = now() where id = ?", ImportPhase.DONE.name, run.id)
    }

    /** The next [size] entities of [kind] this run has not done yet, in a fixed order. */
    fun nextBatch(run: ImportRun, kind: EntityKind, size: Int): List<String> =
        jdbc.query(
            "select qid from raw.import_item where run_id = ? and kind = ? and done_at is null order by qid limit ?",
            { rs, _ -> rs.getString(1) }, run.id, kind.name, size,
        )

    /** The revisions stored for those of [ids] already fetched once. */
    fun revisions(ids: List<String>): Map<String, Long> =
        jdbc.query(
            "select qid, revision from raw.entity where qid = any (?)",
            { rs, _ -> rs.getString(1) to rs.getLong(2) }, ids.toTypedArray(),
        ).toMap()

    /** Stores a batch and marks its entities done, in one transaction: a crash never leaves half a batch. */
    fun saveBatch(run: ImportRun, batch: EntityBatch) = transactions.executeWithoutResult {
        jdbc.batchUpdate(
            """
            insert into raw.entity (qid, revision, fetched_at, json) values (?, ?, now(), ?::jsonb)
            on conflict (qid) do update set revision = excluded.revision, fetched_at = excluded.fetched_at, json = excluded.json
            """.trimIndent(),
            batch.fetched.map { arrayOf<Any>(it.path("id").asString(), it.path("lastrevid").asLong(), it.toString()) },
        )
        jdbc.batchUpdate("delete from raw.entity where qid = ?", batch.missing.map { arrayOf<Any>(it) })
        jdbc.batchUpdate(
            "update raw.import_item set done_at = now() where run_id = ? and qid = ?",
            batch.ids.map { arrayOf<Any>(run.id, it) },
        )
        jdbc.update(
            """
            update raw.import_run set requests = requests + ?, fetched = fetched + ?, unchanged = unchanged + ?,
                missing = missing + ?
            where id = ?
            """.trimIndent(),
            batch.requests, batch.fetched.size, batch.unchanged.size, batch.missing.size, run.id,
        )
    }

    private fun phase(run: ImportRun, phase: ImportPhase) {
        jdbc.update("update raw.import_run set phase = ? where id = ?", phase.name, run.id)
    }

    private fun toRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = ImportRun(
        rs.getLong("id"),
        rs.getObject("started_at", OffsetDateTime::class.java).toInstant(),
        ImportPhase.valueOf(rs.getString("phase")),
        rs.getObject("not_before", OffsetDateTime::class.java)?.toInstant(),
    )

    private fun Instant.utc() = atOffset(ZoneOffset.UTC)

    private companion object {
        const val SCHEMA = "raw"
    }
}
