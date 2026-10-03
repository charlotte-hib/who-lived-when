package dev.wholivedwhen.jobs

import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import dev.wholivedwhen.domain.Artwork
import dev.wholivedwhen.domain.CardType
import dev.wholivedwhen.domain.Domain
import dev.wholivedwhen.domain.Door
import dev.wholivedwhen.domain.Era
import dev.wholivedwhen.domain.Event
import dev.wholivedwhen.domain.Life
import dev.wholivedwhen.domain.Moment
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.domain.PublicationStatus
import dev.wholivedwhen.domain.Region
import dev.wholivedwhen.domain.StoryCard
import dev.wholivedwhen.domain.WorldAround
import dev.wholivedwhen.repository.EraRepository
import dev.wholivedwhen.repository.EventRepository
import dev.wholivedwhen.repository.LifeRepository
import dev.wholivedwhen.repository.MomentRepository
import dev.wholivedwhen.repository.PersonRepository
import dev.wholivedwhen.repository.RegionRepository
import dev.wholivedwhen.support.currentYear
import dev.wholivedwhen.support.slugify

/**
 * Loads the hand-curated pilot data from `resources/seed`, until the Wikidata import job exists.
 * Lives and events name their region and year; the matching era is looked up here.
 */
@Component
@Order(1)
class SeedData(
    private val regions: RegionRepository,
    private val eras: EraRepository,
    private val people: PersonRepository,
    private val lives: LifeRepository,
    private val events: EventRepository,
    private val moments: MomentRepository,
    private val jsonMapper: JsonMapper,
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        if (regions.count() > 0) return

        val regionsByCode = regions.saveAll(read<SeedRegion>("regions").map { Region(it.code, it.name) })
            .associateBy { it.code }

        val allEras = eras.saveAll(read<SeedEra>("eras").map {
            Era(
                id = "${it.region.lowercase()}-${slugify(it.label)}",
                region = regionsByCode.getValue(it.region),
                label = it.label,
                governedBy = it.governedBy ?: it.label,
                startYear = it.start,
                endYear = it.end ?: currentYear(),
            )
        })

        // Consecutive eras share their boundary year; the one that starts later wins it.
        fun eraAt(region: String, year: Int): Era =
            allEras.filter { it.region.code == region && year in it.startYear..it.endYear }.maxByOrNull { it.startYear }
                ?: error("No era in $region covers $year")

        val peopleBySlug = people.saveAll(read<SeedPerson>("people").map {
            val slug = slugify(it.name)
            Person(
                id = slug,
                slug = slug,
                name = it.name,
                birthYear = it.born,
                deathYear = it.died,
                datesApproximate = it.approximate,
                region = regionsByCode.getValue(it.region),
                domain = it.domain,
                occupation = it.occupation,
                wikipediaTitle = it.wikipedia ?: it.name.replace(' ', '_'),
                bioShort = it.bio,
            )
        }).associateBy { it.slug }

        val livesById = lives.saveAll(read<SeedLife>("lives").map {
            Life(slugify(it.label), eraAt(it.region, it.start), it.label, it.description, it.start, it.end)
        }).associateBy { it.id }

        val eventsById = events.saveAll(read<SeedEvent>("events").map { event ->
            Event(event.id, eraAt(event.region, event.year), event.title, event.year, event.description, event.source).apply {
                event.participants.forEach { participant(peopleBySlug.getValue(it.person), it.role) }
            }
        }).associateBy { it.id }

        val seedMoments = read<SeedMoment>("moments")
        val momentsById = moments.saveAll(seedMoments.map { seed ->
            Moment(
                id = seed.id,
                region = regionsByCode.getValue(seed.region),
                place = seed.place,
                period = seed.period,
                startYear = seed.start,
                endYear = seed.end,
                focusYear = seed.focus,
                hook = seed.hook,
                art = seed.art?.toArtwork(),
                world = seed.world?.let { WorldAround(it.governs, it.everyday, it.arts, it.meanwhile) },
                status = seed.status,
                featured = seed.featured,
            ).apply {
                seed.cards.forEachIndexed { position, card ->
                    cards += StoryCard(
                        moment = this,
                        position = position,
                        type = card.type,
                        kicker = card.kicker,
                        title = card.title,
                        text = card.text,
                        year = card.year,
                        person = card.person?.let(peopleBySlug::getValue),
                        life = card.life?.let(livesById::getValue),
                        event = card.event?.let(eventsById::getValue),
                        art = card.art?.toArtwork(),
                    )
                }
            }
        }).associateBy { it.id }

        // Doors point at other moments, so they are added once every moment exists.
        for (seed in seedMoments) {
            val origin = momentsById.getValue(seed.id)
            seed.doors.forEachIndexed { position, door ->
                origin.doors += Door(
                    origin = origin,
                    target = momentsById.getValue(door.to),
                    position = position,
                    kind = door.kind,
                    text = door.text,
                    faces = door.faces.map(peopleBySlug::getValue),
                )
            }
        }
    }

    private inline fun <reified T> read(name: String): List<T> =
        ClassPathResource("seed/$name.json").inputStream.use { jsonMapper.readValue(it) }
}

private data class SeedRegion(val code: String, val name: String)

private data class SeedEra(val region: String, val label: String, val start: Int, val end: Int?, val governedBy: String? = null)

private data class SeedPerson(
    val name: String,
    val born: Int,
    val died: Int?,
    val approximate: Boolean = false,
    val region: String,
    val domain: Domain,
    val occupation: String,
    val wikipedia: String? = null,
    val bio: String? = null,
)

private data class SeedLife(val region: String, val label: String, val description: String, val start: Int, val end: Int)

private data class SeedParticipant(val person: String, val role: String)

private data class SeedEvent(
    val id: String,
    val region: String,
    val year: Int,
    val title: String,
    val description: String,
    val source: String,
    val participants: List<SeedParticipant> = emptyList(),
)

private data class SeedArt(val url: String, val credit: String, val source: String) {
    fun toArtwork() = Artwork(url, credit, source)
}

private data class SeedWorld(val governs: String, val everyday: String, val arts: String, val meanwhile: String)

private data class SeedCard(
    val type: CardType,
    val kicker: String? = null,
    val title: String? = null,
    val text: String? = null,
    val year: Int? = null,
    val person: String? = null,
    val life: String? = null,
    val event: String? = null,
    val art: SeedArt? = null,
)

private data class SeedDoor(val to: String, val kind: String, val text: String, val faces: List<String> = emptyList())

private data class SeedMoment(
    val id: String,
    val region: String,
    val place: String,
    val period: String,
    val start: Int,
    val end: Int,
    val focus: Int,
    val hook: String,
    val status: PublicationStatus,
    val featured: Boolean = false,
    val art: SeedArt? = null,
    val world: SeedWorld? = null,
    val cards: List<SeedCard> = emptyList(),
    val doors: List<SeedDoor> = emptyList(),
)
