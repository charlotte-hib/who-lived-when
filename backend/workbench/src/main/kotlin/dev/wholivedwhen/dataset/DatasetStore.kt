package dev.wholivedwhen.dataset

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * The workbench's schema `dataset`: the rows mapped from `raw`, rebuilt on every mapping run. Its own migrations
 * (`db/dataset`), like `raw`'s.
 */
@Component
class DatasetStore(dataSource: DataSource) {

    private val jdbc = JdbcTemplate(dataSource)

    init {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/dataset")
            .schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(true)
            .load().migrate()
    }

    fun clear() {
        jdbc.execute(
            """
            truncate dataset.person, dataset.person_occupation, dataset.person_place, dataset.person_flag,
                dataset.occupation, dataset.occupation_parent, dataset.place, dataset.place_country, dataset.place_flag
            """.trimIndent(),
        )
    }

    fun savePeople(people: List<PersonRow>) {
        jdbc.batchUpdate(
            """
            insert into dataset.person (qid, label, label_fr, sitelinks, born, born_precision, died, died_precision,
                died_estimated, dates_approximate, gender, enwiki, frwiki)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            people.map {
                arrayOf<Any?>(
                    it.qid, it.label, it.labelFr, it.sitelinks, it.born, it.bornPrecision, it.died, it.diedPrecision,
                    it.diedEstimated, it.datesApproximate, it.gender, it.enwiki, it.frwiki,
                )
            },
        )
        jdbc.batchUpdate(
            "insert into dataset.person_occupation (person, position, occupation, referenced) values (?, ?, ?, ?)",
            people.flatMap { person -> person.occupations.mapIndexed { i, it -> arrayOf<Any?>(person.qid, i, it.occupation, it.referenced) } },
        )
        jdbc.batchUpdate(
            """
            insert into dataset.person_place (person, property, position, place, start_year, end_year, referenced)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            people.flatMap { person ->
                person.places.groupBy { it.property }.flatMap { (property, places) ->
                    places.mapIndexed { i, it -> arrayOf<Any?>(person.qid, property, i, it.place, it.startYear, it.endYear, it.referenced) }
                }
            },
        )
        jdbc.batchUpdate(
            "insert into dataset.person_flag (person, reason, flag) values (?, ?, ?)",
            people.flatMap { person -> person.flags.map { arrayOf<Any?>(person.qid, it.name, it.flag) } },
        )
    }

    fun savePlaces(places: List<PlaceRow>) {
        jdbc.batchUpdate(
            """
            insert into dataset.place (qid, label, label_fr, latitude, longitude, coordinates_country, disputed_area)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            places.map {
                arrayOf<Any?>(
                    it.qid, it.label, it.labelFr, it.coordinates?.latitude, it.coordinates?.longitude, it.coordinatesCountry,
                    it.disputedArea,
                )
            },
        )
        jdbc.batchUpdate(
            "insert into dataset.place_country (place, position, country, iso) values (?, ?, ?, ?)",
            places.flatMap { place -> place.countries.mapIndexed { i, it -> arrayOf<Any?>(place.qid, i, it.country, it.iso) } },
        )
        jdbc.batchUpdate(
            "insert into dataset.place_flag (place, reason, flag) values (?, ?, ?)",
            places.flatMap { place -> place.flags.map { arrayOf<Any?>(place.qid, it.name, it.flag) } },
        )
    }

    /** Saves [occupations], with only those of their parents that are among them. */
    fun saveOccupations(occupations: List<OccupationRow>) {
        jdbc.batchUpdate(
            "insert into dataset.occupation (qid, label, label_fr, female_label_fr) values (?, ?, ?, ?)",
            occupations.map { arrayOf<Any?>(it.qid, it.label, it.labelFr, it.femaleLabelFr) },
        )
        val saved = occupations.map { it.qid }.toSet()
        jdbc.batchUpdate(
            "insert into dataset.occupation_parent (occupation, position, parent) values (?, ?, ?)",
            occupations.flatMap { occupation ->
                occupation.parents.filter { it in saved }.distinct().mapIndexed { i, it -> arrayOf<Any?>(occupation.qid, i, it) }
            },
        )
    }

    private companion object {
        const val SCHEMA = "dataset"
    }
}
