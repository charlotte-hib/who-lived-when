package dev.wholivedwhen.dataset

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper
import dev.wholivedwhen.testing.PostgresTestConfiguration
import dev.wholivedwhen.wikidata.BirthSlice
import dev.wholivedwhen.wikidata.EntityBatch
import dev.wholivedwhen.wikidata.RawStore
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * The mapping from `raw` to `dataset`, on what an import of the 28 people born from 2000 to 1901 BCE stored: the
 * responses recorded from Wikidata on 2026-10-09, put in `raw` as the import puts them.
 */
@SpringBootTest(properties = ["app.wikipedia.enrich=false"])
@Import(PostgresTestConfiguration::class)
class DatasetMappingTests(
    @Autowired private val mapping: DatasetMapping,
    @Autowired private val raw: RawStore,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val jsonMapper: JsonMapper,
) {

    @BeforeEach
    fun emptyCache() {
        jdbc.execute("truncate raw.discovered, raw.entity, raw.import_item, raw.discovery_slice, raw.import_run restart identity")
    }

    private fun recorded(file: String) = jsonMapper.readTree(ClassPathResource("wiremock/__files/wikidata/$file").inputStream)

    /** Stores the recorded people, places, occupations and classes, as a finished import would. */
    private fun importRecorded() {
        val run = raw.currentRun()
        val people = recorded("sparql-born-2000-to-1901-bce.json").path("results").path("bindings").associate {
            it.path("person").path("value").asString().substringAfterLast('/') to it.path("sitelinks").path("value").asString().toInt()
        }
        raw.saveSlice(run, BirthSlice(LocalDate.of(-1999, 1, 1), LocalDate.of(-1899, 1, 1)), people)
        val files = listOf("born", "linked-to-born", "classes-1-of-born", "classes-2-of-born", "classes-3-of-born")
        files.map { "wbgetentities-$it-2000-to-1901-bce.json" }.forEach { file ->
            val entities = recorded(file).path("entities").values().toList()
            raw.saveBatch(run, EntityBatch(entities.map { it.path("id").asString() }, 1, entities, emptyList(), emptyList()))
        }
        raw.finish(run)
    }

    private fun rows(sql: String) = jdbc.queryForList(sql).map { it.values.toList() }

    @Test
    fun `maps every person the last import found, and their occupations`() {
        importRecorded()

        val counts = mapping.map()

        // The 9 occupations and the classes above them, but for 2 of the 25 fetched, only reached through deprecated statements.
        assertEquals(DatasetCounts(people = 28, leftOut = emptyMap(), occupations = 9 + 23), counts)
        assertEquals(
            listOf<Any?>("Sobekneferu", "Néférousobek", 47, -1900, 7, -1793, 9, false, true, "Q6581072", "Sobekneferu", "Néférousobek"),
            rows(
                """
                select label, label_fr, sitelinks, born, born_precision, died, died_precision, died_estimated,
                    dates_approximate, gender, enwiki, frwiki
                from dataset.person where qid = 'Q228951'
                """,
            ).single(),
        )
        // Amenemhat III's preferred occupation, pharaoh of the 12th dynasty, comes first, though Wikidata lists it second.
        assertEquals(
            listOf("Q110550681", "Q82955", "Q110550696", "Q37110"),
            jdbc.queryForList("select occupation from dataset.person_occupation where person = 'Q19244' order by position", String::class.java),
        )
        assertEquals(
            listOf(listOf("BIRTH_IMPRECISE", "POORLY_DOCUMENTED"), listOf("OCCUPATION_UNREFERENCED", "POORLY_DOCUMENTED")),
            rows("select reason, flag from dataset.person_flag where person = 'Q228951' order by reason"),
        )
        assertEquals(
            listOf<Any?>("monarch", "monarque", null),
            rows("select label, label_fr, female_label_fr from dataset.occupation where qid = 'Q116'").single(),
        )
        assertEquals(
            listOf<Any?>("statesperson", "personne d'État", "femme d'État"),
            rows("select label, label_fr, female_label_fr from dataset.occupation where qid = 'Q372436'").single(),
        )
        // A pharaoh is a ruler and a monarch.
        assertEquals(
            listOf("Q1097498", "Q116"),
            jdbc.queryForList("select parent from dataset.occupation_parent where occupation = 'Q37110' order by position", String::class.java),
        )
        // Three levels up, "profession" is kept without its own class, "occupation", which the import did not fetch.
        assertEquals("profession", jdbc.queryForObject("select label from dataset.occupation where qid = 'Q28640'", String::class.java))
        assertEquals(0, jdbc.queryForObject("select count(*) from dataset.occupation_parent where occupation = 'Q28640'", Int::class.java))
    }

    @Test
    fun `mapping again replaces the dataset`() {
        importRecorded()
        mapping.map()

        mapping.map()

        assertEquals(28, jdbc.queryForObject("select count(*) from dataset.person", Int::class.java))
    }

    @Test
    fun `there is nothing to map before an import has finished`() {
        assertThrows<IllegalStateException> { mapping.map() }
    }
}
