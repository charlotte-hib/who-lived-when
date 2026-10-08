package dev.wholivedwhen.release

import dev.wholivedwhen.domain.Artwork
import dev.wholivedwhen.domain.Connection
import dev.wholivedwhen.domain.Door
import dev.wholivedwhen.domain.Era
import dev.wholivedwhen.domain.Event
import dev.wholivedwhen.domain.Life
import dev.wholivedwhen.domain.Moment
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.domain.Region
import dev.wholivedwhen.domain.StoryCard
import dev.wholivedwhen.domain.WorldAround
import dev.wholivedwhen.support.currentYear
import dev.wholivedwhen.support.slugify

/**
 * A release as the site's entities, new and not yet stored. Store them in this order. Doors come last, apart from
 * their moments, because they point at other moments.
 */
class CompiledRelease(
    val regions: List<Region>,
    val eras: List<Era>,
    val people: List<Person>,
    val lives: List<Life>,
    val events: List<Event>,
    val connections: List<Connection>,
    val moments: List<Moment>,
    val doors: List<Door>,
)

/**
 * Turns a release into the site's entities: resolves every reference by id, sets each life and event in its era,
 * and fills in what the records leave out (slugs, eras that last until today, Wikipedia titles).
 * Fails on the first problem, naming the file and the record.
 */
object ReleaseCompiler {

    fun compile(release: Release): CompiledRelease {
        val regions = release.regions.map { Region(it.id, it.name) }
        val regionsById = regions.associateBy { it.code }

        val eras = release.eras.map {
            Era(
                id = it.id,
                region = regionsById.find(it.region, "region", "eras.jsonl ${it.id}"),
                label = it.label,
                governedBy = it.governedBy ?: it.label,
                startYear = it.start,
                endYear = it.end ?: currentYear(),
            )
        }

        // Consecutive eras share their boundary year; the one that starts later wins it.
        fun eraAt(region: String, year: Int, where: String): Era =
            eras.filter { it.region.code == region && year in it.startYear..it.endYear }.maxByOrNull { it.startYear }
                ?: throw InvalidReleaseException("$where: no era in $region covers $year")

        val people = release.people.map {
            Person(
                id = it.id,
                slug = slugify(it.name),
                name = it.name,
                birthYear = it.born,
                deathYear = it.died,
                datesApproximate = it.approximate,
                region = regionsById.find(it.region, "region", "people.jsonl ${it.id}"),
                domain = it.domain,
                occupation = it.occupation,
                wikipediaTitle = it.wikipedia ?: it.name.replace(' ', '_'),
                bioShort = it.bio,
            )
        }
        people.groupBy { it.slug }.values.firstOrNull { it.size > 1 }?.let { same ->
            throw InvalidReleaseException("people.jsonl: ${same.joinToString(" and ") { it.id }} have the same slug, ${same[0].slug}")
        }
        val peopleById = people.associateBy { it.id }

        val lives = release.lives.map {
            Life(it.id, eraAt(it.region, it.start, "lives.jsonl ${it.id}"), it.label, it.description, it.start, it.end)
        }
        val livesById = lives.associateBy { it.id }

        val events = release.events.map { event ->
            val where = "events.jsonl ${event.id}"
            Event(event.id, eraAt(event.region, event.year, where), event.title, event.year, event.description, event.source).apply {
                event.participants.forEach { participant(peopleById.find(it.person, "person", where), it.role) }
            }
        }
        val eventsById = events.associateBy { it.id }

        val connections = release.connections.map {
            val where = "connections.jsonl ${it.people.joinToString(" ")} ${it.kind}"
            if (it.people.size != 2 || it.people[0] == it.people[1]) {
                throw InvalidReleaseException("$where: a connection links two different people")
            }
            Connection(
                first = peopleById.find(it.people[0], "person", where),
                second = peopleById.find(it.people[1], "person", where),
                kind = it.kind,
                year = it.year,
                text = it.text,
                sourceUrl = it.source,
            )
        }

        val moments = release.moments.map { record ->
            val where = "moments/${record.id}.json"
            Moment(
                id = record.id,
                region = regionsById.find(record.region, "region", where),
                place = record.place,
                period = record.period,
                startYear = record.start,
                endYear = record.end,
                focusYear = record.focus,
                hook = record.hook,
                art = record.art?.toArtwork(),
                world = record.world?.let { WorldAround(it.governs, it.everyday, it.arts, it.meanwhile) },
                status = record.status,
                featured = record.featured,
            ).apply {
                record.cards.forEachIndexed { position, card ->
                    val cardWhere = "$where card ${position + 1}"
                    cards += StoryCard(
                        moment = this,
                        position = position,
                        type = card.type,
                        kicker = card.kicker,
                        title = card.title,
                        text = card.text,
                        year = card.year,
                        person = card.person?.let { peopleById.find(it, "person", cardWhere) },
                        life = card.life?.let { livesById.find(it, "life", cardWhere) },
                        event = card.event?.let { eventsById.find(it, "event", cardWhere) },
                        art = card.art?.toArtwork(),
                    )
                }
            }
        }
        val momentsById = moments.associateBy { it.id }

        val doors = release.moments.flatMap { record ->
            record.doors.mapIndexed { position, door ->
                val where = "moments/${record.id}.json door ${position + 1}"
                Door(
                    origin = momentsById.getValue(record.id),
                    target = momentsById.find(door.to, "moment", where),
                    position = position,
                    kind = door.kind,
                    text = door.text,
                    faces = door.faces.map { peopleById.find(it, "person", where) },
                )
            }
        }

        return CompiledRelease(regions, eras, people, lives, events, connections, moments, doors)
    }

    private fun <T> Map<String, T>.find(id: String, kind: String, where: String): T =
        this[id] ?: throw InvalidReleaseException("$where: no $kind $id")

    private fun ArtRecord.toArtwork() = Artwork(url, credit, source)
}
