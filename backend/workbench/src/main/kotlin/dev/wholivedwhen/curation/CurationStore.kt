package dev.wholivedwhen.curation

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import dev.wholivedwhen.workbench.api.model.ClaimStatusDto
import dev.wholivedwhen.workbench.api.model.ClaimTypeDto
import dev.wholivedwhen.workbench.api.model.FlagDto
import dev.wholivedwhen.workbench.api.model.FlagReasonDto
import dev.wholivedwhen.workbench.api.model.OriginDto
import dev.wholivedwhen.workbench.api.model.PayloadDto
import dev.wholivedwhen.workbench.api.model.QueueDto
import dev.wholivedwhen.workbench.api.model.SourceDto
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/** A claim as stored: [payload] is what would ship, [proposal] what was last proposed, before any edit. */
data class StoredClaim(
    val id: String,
    val seq: Long,
    val type: ClaimTypeDto,
    val status: ClaimStatusDto,
    val origin: OriginDto,
    val payload: PayloadDto,
    val proposal: PayloadDto,
    val flags: List<FlagReasonDto>,
)

/** A decision: who moved a claim from one status and payload to another, and why. [undoes] is the decision it reverts. */
data class Decision(
    val by: String,
    val from: ClaimStatusDto?,
    val to: ClaimStatusDto,
    val payloadBefore: PayloadDto?,
    val payloadAfter: PayloadDto,
    val note: String? = null,
    val undoes: Long? = null,
)

/** A decision as logged, with its id and time. */
data class LoggedDecision(val id: Long, val decidedAt: OffsetDateTime, val decision: Decision)

/**
 * The workbench's schema `curation`: claims, their sources and the decisions taken on them. Its own migrations
 * (`db/curation`). Never dropped: this is the curator's work, the one part that cannot be fetched again.
 */
@Component
class CurationStore(dataSource: DataSource, private val jsonMapper: JsonMapper) {

    private val jdbc = JdbcTemplate(dataSource)

    init {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/curation")
            .schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(true)
            .load().migrate()
    }

    fun find(id: String): StoredClaim? =
        jdbc.query("select * from curation.claim where id = ?", ::toClaim, id).firstOrNull()

    /** Claims after [after] in the order they were proposed, at most [limit], of [type], [status] and [flag] if given. */
    fun list(type: ClaimTypeDto?, status: ClaimStatusDto?, flag: FlagDto?, after: Long, limit: Int): List<StoredClaim> =
        jdbc.query(
            """
            select * from curation.claim
            where seq > ? and (?::text is null or type = ?) and (?::text is null or status = ?)
                and (?::jsonb is null or flags @> ?::jsonb)
            order by seq
            limit ?
            """.trimIndent(),
            ::toClaim,
            after, type?.value, type?.value, status?.value, status?.value,
            flag?.let { flagFilter(it) }, flag?.let { flagFilter(it) }, limit,
        )

    /** For each claim type that has claims: how many wait, how many were kept for later, and the waiting ones' flags. */
    fun queues(): List<QueueDto> {
        val flags = mutableMapOf<String, MutableMap<String, Int>>()
        jdbc.query(
            """
            select c.type, f ->> 'flag', count(distinct c.id)
            from curation.claim c, jsonb_array_elements(c.flags) f
            where c.status = 'CANDIDATE'
            group by 1, 2
            order by 1, 2
            """.trimIndent(),
        ) { rs -> flags.getOrPut(rs.getString(1)) { sortedMapOf() }[rs.getString(2)] = rs.getInt(3) }
        return jdbc.query(
            """
            select type, count(*) filter (where status = 'CANDIDATE'), count(*) filter (where status = 'NEEDS_WORK')
            from curation.claim
            group by type
            order by type
            """.trimIndent(),
        ) { rs, _ ->
            QueueDto(ClaimTypeDto.forValue(rs.getString(1)), rs.getInt(2), rs.getInt(3), flags[rs.getString(1)].orEmpty())
        }
    }

    /** Adds [claim], unless one with its id exists: false then. */
    fun insert(claim: StoredClaim): Boolean =
        jdbc.update(
            """
            insert into curation.claim (id, type, status, origin, payload, proposal, flags)
            values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
            on conflict (id) do nothing
            """.trimIndent(),
            claim.id, claim.type.value, claim.status.value, claim.origin.value, json(claim.payload), json(claim.proposal),
            json(claim.flags),
        ) == 1

    fun update(claim: StoredClaim) {
        jdbc.update(
            "update curation.claim set status = ?, payload = ?::jsonb, proposal = ?::jsonb, flags = ?::jsonb where id = ?",
            claim.status.value, json(claim.payload), json(claim.proposal), json(claim.flags), claim.id,
        )
    }

    /** The claim's sources in the order they were added, each with the passage the claim rests on. */
    fun sources(claim: String): List<SourceDto> =
        jdbc.query(
            """
            select s.kind, s.locator, cs.quote
            from curation.claim_source cs
            join curation.source s on s.id = cs.source
            where cs.claim = ?
            order by cs.position
            """.trimIndent(),
            { rs, _ ->
                val locator = jsonMapper.readValue<Map<String, Any>>(rs.getString(2))
                jsonMapper.convertValue(locator + mapOf("kind" to rs.getString(1), "quote" to rs.getString(3)), SourceDto::class.java)
            },
            claim,
        )

    /** Adds those of [sources] the claim does not already rest on, after the ones it has. */
    fun addSources(claim: String, sources: List<SourceDto>) {
        val attached = sources(claim).toMutableList()
        for (source in sources.filter { it !in attached }) {
            val id = jdbc.queryForObject(
                """
                with added as (
                    insert into curation.source (kind, locator) values (?, ?::jsonb)
                    on conflict (kind, locator) do nothing
                    returning id
                )
                select id from added
                union all
                select id from curation.source where kind = ? and locator = ?::jsonb
                limit 1
                """.trimIndent(),
                Long::class.java,
                source.kind.value, locator(source), source.kind.value, locator(source),
            )
            jdbc.update(
                "insert into curation.claim_source (claim, source, position, quote) values (?, ?, ?, ?)",
                claim, id, attached.size, source.quote,
            )
            attached += source
        }
    }

    fun log(claim: String, decision: Decision) {
        jdbc.update(
            """
            insert into curation.decision
                (claim, decided_by, from_status, to_status, payload_before, payload_after, note, undoes)
            values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            """.trimIndent(),
            claim, decision.by, decision.from?.value, decision.to.value, decision.payloadBefore?.let(::json),
            json(decision.payloadAfter), decision.note, decision.undoes,
        )
    }

    /** The claim's decisions, oldest first. */
    fun decisions(claim: String): List<LoggedDecision> =
        jdbc.query("select * from curation.decision where claim = ? order by id", ::toDecision, claim)

    private fun toClaim(rs: ResultSet, row: Int) = StoredClaim(
        id = rs.getString("id"),
        seq = rs.getLong("seq"),
        type = ClaimTypeDto.forValue(rs.getString("type")),
        status = ClaimStatusDto.forValue(rs.getString("status")),
        origin = OriginDto.forValue(rs.getString("origin")),
        payload = readPayload(rs.getString("payload")),
        proposal = readPayload(rs.getString("proposal")),
        flags = jsonMapper.readValue<List<FlagReasonDto>>(rs.getString("flags")),
    )

    private fun toDecision(rs: ResultSet, row: Int) = LoggedDecision(
        id = rs.getLong("id"),
        decidedAt = rs.getObject("decided_at", OffsetDateTime::class.java).withOffsetSameInstant(ZoneOffset.UTC),
        decision = Decision(
            by = rs.getString("decided_by"),
            from = rs.getString("from_status")?.let(ClaimStatusDto::forValue),
            to = ClaimStatusDto.forValue(rs.getString("to_status")),
            payloadBefore = rs.getString("payload_before")?.let(::readPayload),
            payloadAfter = readPayload(rs.getString("payload_after")),
            note = rs.getString("note"),
            undoes = rs.getLong("undoes").takeUnless { rs.wasNull() },
        ),
    )

    private fun readPayload(json: String): PayloadDto = jsonMapper.readValue(json, PayloadDto::class.java)

    private fun json(value: Any): String = jsonMapper.writeValueAsString(value)

    /** A source's fields but its kind and quote, which the source's identity does not include. */
    private fun locator(source: SourceDto): String =
        json(jsonMapper.convertValue(source, Map::class.java).filterKeys { it != "kind" && it != "quote" }.filterValues { it != null })

    private fun flagFilter(flag: FlagDto) = json(listOf(mapOf("flag" to flag.value)))

    private companion object {
        const val SCHEMA = "curation"
    }
}
