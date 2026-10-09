package dev.wholivedwhen.wikidata

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.and
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.HttpServerErrorException
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import org.wiremock.spring.InjectWireMock
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import dev.wholivedwhen.testing.PostgresTestConfiguration
import dev.wholivedwhen.wikimedia.WikimediaException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The import against a fake Wikimedia (WireMock), answering with responses recorded from the real one on 2026-10-09:
 * the 28 people born from 2000 to 1901 BCE with at least 10 sitelinks, their entities, and the 11 places and
 * occupations they link to (Egypt's statements cut down to three, to keep the file small).
 */
@SpringBootTest(
    properties = [
        "app.wikipedia.enrich=false",
        "app.wikimedia.wikidata-api=\${wikimedia.base-url}/w/api.php",
        "app.wikimedia.wikidata-sparql=\${wikimedia.base-url}/sparql",
        // One slice, the 20th century BCE, as the default plan asks for it.
        "app.wikidata.born-from=-1999",
        "app.wikidata.born-until=-1899",
    ],
)
@EnableWireMock(ConfigureWireMock(baseUrlProperties = ["wikimedia.base-url"], filesUnderClasspath = "wiremock"))
@Import(PostgresTestConfiguration::class)
class WikidataImportTests(
    @Autowired private val wikidataImport: WikidataImport,
    @Autowired private val store: RawStore,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val jsonMapper: JsonMapper,
) {

    @InjectWireMock
    private lateinit var server: WireMockServer

    private val sparql = urlPathEqualTo("/sparql")
    private val wikidata = urlPathEqualTo("/w/api.php")

    private fun json(file: String): ResponseDefinitionBuilder =
        aResponse().withHeader("Content-Type", "application/json").withBodyFile("wikidata/$file")

    private fun entities(props: String): MappingBuilder =
        get(wikidata).withQueryParam("action", equalTo("wbgetentities")).withQueryParam("props", equalTo(props))

    private fun stubFirstImport() {
        server.stubFor(get(sparql).willReturn(json("sparql-born-2000-to-1901-bce.json").withHeader("Content-Type", "application/sparql-results+json")))
        server.stubFor(entities("info|labels|aliases|claims|sitelinks").willReturn(json("wbgetentities-born-2000-to-1901-bce.json")))
        server.stubFor(entities("info|labels|claims").willReturn(json("wbgetentities-linked-to-born-2000-to-1901-bce.json")))
    }

    private fun count(table: String) = jdbc.queryForObject("select count(*) from raw.$table", Int::class.java)!!

    /** The requests the fake Wikimedia received on [path], in the order they came. */
    private fun requests(path: String) =
        server.allServeEvents.map { it.request }.filter { it.url.startsWith(path) }.sortedBy { it.loggedDate }

    @BeforeEach
    fun emptyCache() {
        jdbc.execute("truncate raw.discovered, raw.entity, raw.import_item, raw.discovery_slice, raw.import_run restart identity")
    }

    @Test
    fun `discovers people, fetches their entities, then the places and occupations they link to`() {
        stubFirstImport()

        val run = wikidataImport.run()

        assertEquals(ImportPhase.DONE, run.phase)
        assertEquals(28, count("discovered"))
        assertEquals(28 + 11, count("entity"))
        assertEquals(48, jdbc.queryForObject("select sitelinks from raw.discovered where qid = 'Q19244'", Int::class.java))
        assertEquals(
            listOf("Amenemhat III", "2553827743"),
            jdbc.queryForList("select json -> 'labels' -> 'en' ->> 'value', revision::text from raw.entity where qid = 'Q19244'")
                .single().values.toList(),
        )
        assertEquals("Egypt", jdbc.queryForObject("select json -> 'labels' -> 'en' ->> 'value' from raw.entity where qid = 'Q79'", String::class.java))
        assertEquals(mapOf<String, Any?>("requests" to 3, "busy" to 0, "fetched" to 39, "unchanged" to 0, "missing" to 0), store.counts(run))

        val query = requests("/sparql").single().queryParams["query"]!!.firstValue()
        assertTrue("?sitelinks >= 10" in query, query)
        assertTrue("\"-1999-01-01T00:00:00Z\"^^xsd:dateTime <= ?born && ?born < \"-1899-01-01T00:00:00Z\"^^xsd:dateTime" in query, query)
        val (people, linked) = requests("/w/api.php")
        assertEquals(28, people.queryParams["ids"]!!.firstValue().split("|").size)
        assertEquals("en|fr|ja|mul", people.queryParams["languages"]!!.firstValue())
        assertEquals("enwiki|frwiki", people.queryParams["sitefilter"]!!.firstValue())
        assertEquals(
            "Q110550681|Q110550696|Q116|Q19643|Q2304859|Q37110|Q372436|Q501259|Q79|Q82955|Q863048",
            linked.queryParams["ids"]!!.firstValue(),
        )
        assertEquals(null, linked.queryParams["sitefilter"])
    }

    @Test
    fun `a later run fetches again only the entities whose revision changed`() {
        stubFirstImport()
        wikidataImport.run()
        server.resetRequests()

        // Since then, Amenemhat III was edited, and one of the linked entities was deleted.
        val people = recorded("wbgetentities-born-2000-to-1901-bce-info.json")
        (people.path("entities").path("Q19244") as ObjectNode).put("lastrevid", 2553827743 + 1)
        val linked = recorded("wbgetentities-linked-to-born-2000-to-1901-bce-info.json")
        (linked.path("entities") as ObjectNode).putObject("Q110550696").put("id", "Q110550696").put("missing", "")
        server.stubFor(entities("info").withQueryParam("ids", containing("Q19244")).willReturn(okJson(people.toString())))
        server.stubFor(entities("info").withQueryParam("ids", containing("Q79")).willReturn(okJson(linked.toString())))

        val run = wikidataImport.run()

        // The query, the people's revisions, Amenemhat III, the linked entities' revisions.
        assertEquals(mapOf<String, Any?>("requests" to 4, "busy" to 0, "fetched" to 1, "unchanged" to 37, "missing" to 1), store.counts(run))
        val fetchedAgain = requests("/w/api.php").filter { it.queryParams["props"]!!.firstValue() != "info" }
        assertEquals(listOf("Q19244"), fetchedAgain.map { it.queryParams["ids"]!!.firstValue() })
        assertEquals(28 + 10, count("entity"))
    }

    @Test
    fun `a slice the query service stops is asked for in halves, and a run that failed resumes with the half it missed`() {
        stubFirstImport()
        val wholeSlice = and(containing(FROM_2000_BCE), containing(UNTIL_1900_BCE))
        server.stubFor(get(sparql).atPriority(1).withQueryParam("query", wholeSlice).willReturn(aResponse().withStatus(500).withBody(TIMEOUT)))
        server.stubFor(get(sparql).atPriority(2).withQueryParam("query", containing(UNTIL_1900_BCE)).willReturn(aResponse().withStatus(502)))

        assertThrows<WikimediaException> { wikidataImport.run() }
        assertEquals(3, requests("/sparql").size)
        assertEquals(1, count("discovery_slice"))

        // The query service is back: only the second half is asked for again.
        server.resetRequests()
        server.stubFor(get(sparql).atPriority(2).withQueryParam("query", containing(UNTIL_1900_BCE)).willReturn(okJson(NO_ONE)))
        val run = wikidataImport.run()

        assertEquals(1L, run.id)
        assertEquals(ImportPhase.DONE, run.phase)
        assertTrue(UNTIL_1900_BCE in requests("/sparql").single().queryParams["query"]!!.firstValue())
        assertEquals(2, count("discovery_slice"))
        assertEquals(28, count("discovered"))
    }

    @Test
    fun `a run that stopped half way resumes at the next batch`() {
        stubFirstImport()
        server.stubFor(entities("info|labels|claims").atPriority(1).willReturn(aResponse().withStatus(503)))
        assertThrows<HttpServerErrorException> { wikidataImport.run() }

        server.resetRequests()
        server.stubFor(entities("info|labels|claims").atPriority(1).willReturn(json("wbgetentities-linked-to-born-2000-to-1901-bce.json")))
        val run = wikidataImport.run()

        assertEquals(1L, run.id)
        assertEquals(ImportPhase.DONE, run.phase)
        assertEquals(listOf("info|labels|claims"), requests("/").map { it.queryParams["props"]!!.firstValue() })
        assertEquals(28 + 11, count("entity"))
    }

    @Test
    fun `when Wikimedia asks to wait, the run counts it, and a resumed run waits as long too`() {
        stubFirstImport()
        server.stubFor(
            get(sparql).atPriority(1).inScenario("busy").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")).willSetStateTo("waited"),
        )
        val run = wikidataImport.run()
        assertEquals(mapOf<String, Any?>("requests" to 4, "busy" to 1, "fetched" to 39, "unchanged" to 0, "missing" to 0), store.counts(run))
        assertTrue(run.notBefore != null)

        // A run that stopped while asked to wait 2 more seconds.
        server.resetRequests()
        server.stubFor(entities("info").willReturn(json("wbgetentities-born-2000-to-1901-bce-info.json")))
        val stopped = store.currentRun()
        jdbc.update("update raw.import_run set not_before = now() + interval '2 seconds' where id = ?", stopped.id)
        val resumed = System.currentTimeMillis()
        wikidataImport.run()

        assertTrue(requests("/").first().loggedDate.time - resumed >= 1900, "resumed without waiting")
    }

    private fun recorded(file: String) =
        jsonMapper.readTree(ClassPathResource("wiremock/__files/wikidata/$file").inputStream) as ObjectNode

    private companion object {
        const val FROM_2000_BCE = "\"-1999-01-01T00:00:00Z\"^^xsd:dateTime <= ?born"
        const val UNTIL_1900_BCE = "?born < \"-1899-01-01T00:00:00Z\"^^xsd:dateTime"
        // What the query service answers when it stops a query after 60 seconds.
        const val TIMEOUT = "java.util.concurrent.ExecutionException: java.util.concurrent.TimeoutException"
        const val NO_ONE = """{"head":{"vars":["person","sitelinks"]},"results":{"bindings":[]}}"""
    }
}
