package dev.wholivedwhen.wikidata

import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import dev.wholivedwhen.wikimedia.WikimediaClient
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Fetches Wikidata's people into the workbench's `raw` schema: discovery (who is above the cut-off), then each
 * person's entity, then the places and occupations their claims point to. Entities already stored are only fetched
 * again when their revision has changed, so running it again a month later refreshes the cache cheaply.
 *
 * Every request goes through [WikimediaClient]'s shared pace. Progress is in the database: a run that stopped resumes
 * at the next slice or batch, and first waits out any `Retry-After` Wikimedia gave before it stopped.
 */
@Component
class WikidataImport(
    private val wikimedia: WikimediaClient,
    private val discovery: Discovery,
    private val store: RawStore,
    private val properties: WikidataProperties,
    retries: RetryRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var current: ImportRun? = null

    init {
        // Each retry is another request, after Wikimedia asked to wait.
        retries.retry(WikimediaClient.PACE).eventPublisher.onRetry { event -> current?.let { store.busy(it, event.waitInterval) } }
    }

    fun run(): ImportRun {
        var run = store.currentRun()
        if (run.phase != ImportPhase.DISCOVER || store.doneSlices(run).isNotEmpty()) log.info("Resuming import {} at {}", run.id, run.phase)
        waitUntil(run.notBefore)
        current = run
        try {
            if (run.phase == ImportPhase.DISCOVER) {
                discovery.discover(run)
                store.queuePeople(run)
                run = store.findRun(run.id)
            }
            if (run.phase == ImportPhase.PEOPLE) {
                fetchAll(run, EntityKind.PEOPLE)
                store.queueLinked(run, properties.linkedProperties)
                run = store.findRun(run.id)
            }
            if (run.phase == ImportPhase.LINKED) {
                fetchAll(run, EntityKind.LINKED)
                store.finish(run)
            }
        } finally {
            current = null
        }
        log.info("Import {} done: {}", run.id, store.counts(run))
        return store.findRun(run.id)
    }

    private fun fetchAll(run: ImportRun, kind: EntityKind) {
        var done = 0
        while (true) {
            val ids = store.nextBatch(run, kind, WikimediaClient.MAX_ENTITIES).ifEmpty { break }
            store.saveBatch(run, fetch(kind, ids))
            done += ids.size
            if (done % PROGRESS_EVERY < ids.size) log.info("{}: {} entities checked or fetched", kind, done)
        }
    }

    /**
     * One batch: asks for the latest revision of the entities already stored, then fetches in full those that are
     * new or changed. Two requests at most, of 50 entities each.
     */
    private fun fetch(kind: EntityKind, ids: List<String>): EntityBatch {
        val stored = store.revisions(ids)
        var requests = 0
        val unchanged = mutableListOf<String>()
        val missing = mutableListOf<String>()
        if (stored.isNotEmpty()) {
            val latest = wikimedia.entities(stored.keys.toList(), listOf("info")).path("entities")
            requests++
            stored.forEach { (qid, revision) ->
                val entity = latest.path(qid)
                when {
                    entity.has("missing") -> missing += qid
                    entity.path("lastrevid").asLong() == revision -> unchanged += qid
                }
            }
        }

        val wanted = ids - unchanged.toSet() - missing.toSet()
        if (wanted.isEmpty()) return EntityBatch(ids, requests, emptyList(), unchanged, missing)
        val entities = when (kind) {
            EntityKind.PEOPLE -> wikimedia.entities(wanted, PEOPLE_PROPS, properties.languages, properties.sites)
            EntityKind.LINKED -> wikimedia.entities(wanted, LINKED_PROPS, properties.languages)
        }.path("entities")
        requests++
        val (gone, found) = wanted.partition { entities.path(it).isMissingNode || entities.path(it).has("missing") }
        return EntityBatch(ids, requests, found.map { entities.path(it) }, unchanged, missing + gone)
    }

    private fun waitUntil(notBefore: Instant?) {
        val wait = notBefore?.let { Duration.between(Instant.now(), it) } ?: return
        if (wait.isNegative) return
        log.info("Wikimedia asked to wait until {}: waiting {}", notBefore, wait)
        Thread.sleep(wait)
    }

    private companion object {
        /** A person's labels and aliases, statements and Wikipedia articles, and the revision. */
        val PEOPLE_PROPS = listOf("info", "labels", "aliases", "claims", "sitelinks")
        /** A place's or an occupation's labels and statements (coordinates, country, the classes it belongs to). */
        val LINKED_PROPS = listOf("info", "labels", "claims")
        const val PROGRESS_EVERY = 1000
    }
}

/**
 * Runs the Wikidata import, or resumes the one that stopped, then exits:
 * `./gradlew :workbench:bootRun --args='--app.wikidata.import=true --app.wikipedia.enrich=false'`. Enrichment off:
 * it calls Wikipedia on its own, outside the shared pace.
 */
@Component
@Order(3)
@ConditionalOnBooleanProperty("app.wikidata.import")
class WikidataImportJob(
    private val wikidataImport: WikidataImport,
    private val context: ConfigurableApplicationContext,
) : CommandLineRunner {

    override fun run(vararg args: String) {
        wikidataImport.run()
        exitProcess(SpringApplication.exit(context))
    }
}
