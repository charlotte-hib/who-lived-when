package dev.wholivedwhen.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy

enum class Domain { POWER, ARTS, EVERYDAY }

/** Column length for prose and URLs, which outgrow the 255-character default. */
const val LONG_TEXT = 2000

/** A place, seen through today's borders, e.g. "FR" for France. Who governed it changes over time: see [Era]. */
@Entity
class Region(
    @Id val code: String,
    val name: String,
)

/** A region at a time span, named after who governed it. Never a bare year. Both years are inclusive. */
@Entity
class Era(
    @Id val id: String,
    @ManyToOne(fetch = FetchType.LAZY) val region: Region,
    val label: String,
    val governedBy: String,
    val startYear: Int,
    val endYear: Int,
)

@Entity
class Person(
    @Id val id: String,
    @Column(unique = true) val slug: String,
    val name: String,
    val birthYear: Int,
    val deathYear: Int?,
    /** True when the birth or death year is only known approximately. */
    val datesApproximate: Boolean,
    @ManyToOne(fetch = FetchType.LAZY) val region: Region,
    @Enumerated(EnumType.STRING) val domain: Domain,
    val occupation: String,
    val wikipediaTitle: String?,
    /** One or two sentences: hand-written, or the lead of the Wikipedia article. */
    @Column(length = LONG_TEXT) var bioShort: String? = null,
    /** The first paragraph of the Wikipedia article. Filled by [dev.wholivedwhen.jobs.WikipediaJob]. */
    @Column(length = LONG_TEXT) var about: String? = null,
    @Column(length = LONG_TEXT) var wikipediaUrl: String? = null,
    @Column(length = LONG_TEXT) var portraitUrl: String? = null,
)

/** A typical existence in an era, shown as representative of the period rather than as a real person. */
@Entity
class Life(
    @Id val id: String,
    @ManyToOne(fetch = FetchType.LAZY) val era: Era,
    val label: String,
    @Column(length = LONG_TEXT) val description: String,
    val startYear: Int,
    val endYear: Int,
)

/** A dated, sourced event. The only way two people get connected. */
@Entity
class Event(
    @Id val id: String,
    @ManyToOne(fetch = FetchType.LAZY) val era: Era,
    val title: String,
    @Column(name = "event_year") val year: Int,
    @Column(length = LONG_TEXT) val description: String,
    @Column(length = LONG_TEXT) val sourceUrl: String,
) {
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableList<EventParticipant> = mutableListOf()

    fun participant(person: Person, role: String) = apply {
        participants += EventParticipant(event = this, person = person, role = role)
    }
}

@Entity
class EventParticipant(
    @Id @GeneratedValue val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) val event: Event,
    @ManyToOne(fetch = FetchType.LAZY) val person: Person,
    /** Specific role in the event, e.g. "author", "placed 1st". */
    val role: String,
)

/**
 * How two people knew each other, in one sourced sentence: friends, rivals, a portrait, a commission.
 * Stored once per pair; [year] is when the link is documented.
 */
@Entity
class Connection(
    @Id @GeneratedValue val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) val first: Person,
    @ManyToOne(fetch = FetchType.LAZY) val second: Person,
    /** A few words, e.g. "school friends". */
    val kind: String,
    @Column(name = "connection_year") val year: Int,
    @Column(length = LONG_TEXT) val text: String,
    @Column(length = LONG_TEXT) val sourceUrl: String,
) {
    /** The person on the other side of the link from [person]. */
    fun other(person: Person) = if (first.slug == person.slug) second else first
}

/** Draft content comes from the drafting job and is only served once a curator publishes it. */
enum class PublicationStatus { DRAFT, PUBLISHED }

/** A public-domain image with its credit and the page it comes from. */
@Embeddable
data class Artwork(
    @Column(length = LONG_TEXT) val url: String,
    val credit: String,
    @Column(length = LONG_TEXT) val sourceUrl: String,
)

/** The world around a moment: one line per domain, and what is happening elsewhere at the same time. */
@Embeddable
data class WorldAround(
    @Column(length = LONG_TEXT) val governs: String,
    @Column(length = LONG_TEXT) val everyday: String,
    @Column(length = LONG_TEXT) val arts: String,
    @Column(length = LONG_TEXT) val meanwhile: String,
)

/** A place over a few years, small enough to tell as one story. The unit people step into. */
@Entity
class Moment(
    @Id val id: String,
    @ManyToOne(fetch = FetchType.LAZY) val region: Region,
    /** Where, e.g. "Paris". */
    val place: String,
    /** When, as people say it, e.g. "1870s" or "c. 1000". */
    val period: String,
    val startYear: Int,
    val endYear: Int,
    /** The year the moment opens on. */
    val focusYear: Int,
    @Column(length = LONG_TEXT) var hook: String,
    @Embedded var art: Artwork?,
    @Embedded var world: WorldAround?,
    @Enumerated(EnumType.STRING) var status: PublicationStatus,
    /** Shown first on the start page. */
    var featured: Boolean = false,
) {
    @OneToMany(mappedBy = "moment", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position")
    val cards: MutableList<StoryCard> = mutableListOf()

    @OneToMany(mappedBy = "origin", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position")
    val doors: MutableList<Door> = mutableListOf()
}

enum class CardType { SCENE, PERSON, LIFE, EVENT }

/** One screen of a moment's story. Person, life and event cards point at the records they are about. */
@Entity
class StoryCard(
    @Id @GeneratedValue val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) val moment: Moment,
    val position: Int,
    @Enumerated(EnumType.STRING) val type: CardType,
    val kicker: String? = null,
    val title: String? = null,
    @Column(length = LONG_TEXT) val text: String? = null,
    /** The year the card is set in, used for ages. */
    @Column(name = "card_year") val year: Int? = null,
    @ManyToOne(fetch = FetchType.LAZY) val person: Person? = null,
    @ManyToOne(fetch = FetchType.LAZY) val life: Life? = null,
    @ManyToOne(fetch = FetchType.LAZY) val event: Event? = null,
    /** Replaces the moment's artwork while this card is shown. */
    @Embedded val art: Artwork? = null,
)

/** A curated way out of a moment's story into another moment, e.g. "Meanwhile, elsewhere". */
@Entity
class Door(
    @Id @GeneratedValue val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) val origin: Moment,
    @ManyToOne(fetch = FetchType.LAZY) val target: Moment,
    val position: Int,
    val kind: String,
    val text: String,
    /** People to show on the door, when it follows someone. */
    @ManyToMany
    @JoinTable(name = "door_face")
    val faces: List<Person> = emptyList(),
)
