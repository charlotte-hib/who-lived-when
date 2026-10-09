package dev.wholivedwhen.wikimedia

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import tools.jackson.databind.JsonNode
import java.time.Duration

@ConfigurationProperties("app.wikimedia")
data class WikimediaProperties(
    /** Wikidata's Action API. */
    val wikidataApi: String,
    /** Wikipedia's Action API, with `{language}` for the edition, e.g. `en`. */
    val wikipediaApi: String,
    /** Wikimedia asks API clients for a contact in the User-Agent. */
    val userAgent: String,
    /** Seconds of replication lag past which the servers answer with the `maxlag` error rather than work. */
    val maxlag: Int,
    /** How long to wait when Wikimedia asks to retry later without saying how long. */
    val defaultRetryWait: Duration,
    /** The longest wait the client accepts. Asked to wait longer, it gives up rather than retry any sooner. */
    val maxRetryWait: Duration,
)

/** Wikimedia asked to retry later: HTTP 429, or the Action API's `maxlag` error. Retried after [retryAfter]. */
class WikimediaBusyException(message: String, val retryAfter: Duration?) : RuntimeException(message)

/** An error the Action API answered with, other than `maxlag`, or a wait longer than the client accepts. Not retried. */
class WikimediaException(message: String) : RuntimeException(message)

/**
 * The workbench's way to Wikimedia: Wikidata's and Wikipedia's Action APIs, which answer many entities or pages per
 * request. Returns their JSON as it came.
 *
 * Every call shares one pace, the Resilience4j instances named `wikimedia` in application.yaml: one request at a time,
 * two a second at most. When Wikimedia asks to retry later (HTTP 429, or the `maxlag` error when its servers are
 * behind), the call waits as long as `Retry-After` says, holding back every other call meanwhile, then tries again.
 */
@Component
class WikimediaClient(
    builder: RestClient.Builder,
    private val properties: WikimediaProperties,
    rateLimiters: RateLimiterRegistry,
    bulkheads: BulkheadRegistry,
    retries: RetryRegistry,
) {

    private val restClient = builder
        .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent)
        .defaultStatusHandler({ it == HttpStatus.TOO_MANY_REQUESTS }) { _, response -> throw busy("HTTP 429", response.headers) }
        .build()

    private val rateLimiter: RateLimiter = rateLimiters.rateLimiter(PACE)
    private val bulkhead: Bulkhead = bulkheads.bulkhead(PACE)
    private val retry: Retry = retries.retry(PACE)

    /** Entities by Q-id, [MAX_ENTITIES] at most (`wbgetentities`). Returns the whole answer, entities under `entities`. */
    fun entities(ids: List<String>, props: List<String>, languages: List<String>, sites: List<String>): JsonNode {
        require(ids.size in 1..MAX_ENTITIES) { "Between 1 and $MAX_ENTITIES ids a request, not ${ids.size}" }
        return get(
            properties.wikidataApi,
            emptyMap(),
            mapOf(
                "action" to "wbgetentities",
                "ids" to ids.joinToString("|"),
                "props" to props.joinToString("|"),
                "languages" to languages.joinToString("|"),
                "sitefilter" to sites.joinToString("|"),
            ),
        )
    }

    /**
     * The lead as plain text, the thumbnail and the latest revision of Wikipedia pages, [MAX_PAGES] at most, in the
     * [language] edition (`action=query`). Returns the whole answer, pages under `query.pages`, following redirects.
     */
    fun pages(language: String, titles: List<String>): JsonNode {
        require(titles.size in 1..MAX_PAGES) { "Between 1 and $MAX_PAGES titles a request, not ${titles.size}" }
        return get(
            properties.wikipediaApi,
            mapOf("language" to language),
            mapOf(
                "action" to "query",
                "prop" to "extracts|pageimages|info",
                "exintro" to "1",
                "explaintext" to "1",
                "piprop" to "thumbnail",
                "pithumbsize" to "400",
                "titles" to titles.joinToString("|"),
                "redirects" to "1",
                "formatversion" to "2",
            ),
        )
    }

    private fun get(api: String, apiVariables: Map<String, String>, parameters: Map<String, String>): JsonNode {
        val query = parameters + mapOf("maxlag" to properties.maxlag.toString(), "format" to "json")
        // Every value goes in as a URI variable, so the client encodes it: the pipes between ids, accents in titles.
        val uri = api + query.keys.joinToString("&", prefix = "?") { "$it={$it}" }
        return paced {
            val response = restClient.get().uri(uri, apiVariables + query).retrieve().toEntity<JsonNode>()
            val body = checkNotNull(response.body) { "Empty answer from $api" }
            val error = body.path("error")
            when {
                error.isMissingNode -> body
                error.path("code").asString() == "maxlag" -> throw busy("maxlag: ${error.path("info").asString()}", response.headers)
                else -> throw WikimediaException("${error.path("code").asString()}: ${error.path("info").asString()}")
            }
        }
    }

    // The bulkhead outermost: a call that waits out Retry-After keeps every other call waiting too. Each attempt then
    // takes its own permit from the rate limiter.
    private fun <T> paced(call: () -> T): T =
        Bulkhead.decorateSupplier(bulkhead, Retry.decorateSupplier(retry, RateLimiter.decorateSupplier(rateLimiter, call))).get()

    private fun busy(reason: String, headers: HttpHeaders): RuntimeException {
        val retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER)?.trim()?.toLongOrNull()?.let(Duration::ofSeconds)
        if (retryAfter != null && retryAfter > properties.maxRetryWait) {
            return WikimediaException("$reason, asked to wait $retryAfter, longer than ${properties.maxRetryWait}")
        }
        return WikimediaBusyException(reason, retryAfter)
    }

    companion object {
        /** The name of the Resilience4j rate limiter, bulkhead and retry every call to Wikimedia shares. */
        const val PACE = "wikimedia"
        const val MAX_ENTITIES = 50
        const val MAX_PAGES = 20
    }
}
