package dev.wholivedwhen.web

import org.mapstruct.Mapper
import org.mapstruct.Mapping
import dev.wholivedwhen.domain.Artwork
import dev.wholivedwhen.domain.CardType
import dev.wholivedwhen.domain.Domain
import dev.wholivedwhen.domain.Era
import dev.wholivedwhen.domain.Event
import dev.wholivedwhen.domain.EventParticipant
import dev.wholivedwhen.domain.Life
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.domain.Region
import dev.wholivedwhen.domain.StoryCard
import dev.wholivedwhen.domain.WorldAround

data class RegionDto(val code: String, val name: String)

data class ArtworkDto(val url: String, val credit: String, val sourceUrl: String)

data class WorldAroundDto(val governs: String, val everyday: String, val arts: String, val meanwhile: String)

data class EraDto(
    val id: String,
    val label: String,
    val regionCode: String,
    val region: String,
    val governedBy: String,
    val startYear: Int,
    val endYear: Int,
)

data class PersonDto(
    val slug: String,
    val name: String,
    val birthYear: Int,
    val deathYear: Int?,
    val datesApproximate: Boolean,
    val regionCode: String,
    val region: String,
    val domain: Domain,
    val occupation: String,
    val bioShort: String?,
    val portraitUrl: String?,
)

data class LifeDto(
    val id: String,
    val label: String,
    val description: String,
    val startYear: Int,
    val endYear: Int,
)

/** Someone in an event, with their part in it, e.g. "author". */
data class ParticipantDto(val person: PersonDto, val role: String)

data class EventDto(
    val id: String,
    val title: String,
    val year: Int,
    val description: String,
    val sourceUrl: String,
    val participants: List<ParticipantDto>,
)

data class EraDetailDto(
    val era: EraDto,
    val people: Map<Domain, List<PersonDto>>,
    val events: List<EventDto>,
    /** The moments set under this regime, to step into. */
    val moments: List<MomentSummaryDto>,
)

/** What a moment shows on a card: enough to choose it, not to explore it. */
data class MomentSummaryDto(
    val id: String,
    val regionCode: String,
    val region: String,
    val place: String,
    val period: String,
    val startYear: Int,
    val endYear: Int,
    val focusYear: Int,
    val hook: String,
    val art: ArtworkDto?,
    /** Who governed the place in the focus year. */
    val governedBy: String?,
    /** Number of cards in the story; 0 when no story is written yet. */
    val storyCards: Int,
    /** The people the story features. */
    val cast: List<PersonDto>,
    val featured: Boolean,
)

data class DoorDto(val kind: String, val text: String, val target: MomentSummaryDto, val faces: List<PersonDto>)

/** Everything the moment page needs; scrubbing through its years happens in the browser. */
data class MomentDetailDto(
    val moment: MomentSummaryDto,
    val world: WorldAroundDto?,
    val eras: List<EraDto>,
    val people: List<PersonDto>,
    val lives: List<LifeDto>,
    val events: List<EventDto>,
    val doors: List<DoorDto>,
)

data class StoryCardDto(
    val type: CardType,
    val kicker: String?,
    val title: String?,
    val text: String?,
    val year: Int?,
    val person: PersonDto?,
    val life: LifeDto?,
    val event: EventDto?,
    val art: ArtworkDto?,
)

data class StoryDto(val moment: MomentSummaryDto, val cards: List<StoryCardDto>, val doors: List<DoorDto>)

enum class LifeLineKind { BIRTH, ERA, EVENT, OWN_EVENT, DEATH }

/** One line of "their life in their time": a dated change around them, with their age. */
data class LifeLineDto(val year: Int, val age: Int?, val kind: LifeLineKind, val text: String, val role: String?)

/** Someone a person was linked to, how (a pair like "tea master and lord", or their role in a shared event), and when. */
data class ConnectionDto(
    val person: PersonDto,
    val kind: String,
    val year: Int,
    val text: String,
    val sourceUrl: String,
)

data class PersonDetailDto(
    val person: PersonDto,
    val about: String?,
    val wikipediaUrl: String?,
    /** The year the rest of the page looks at: the one asked for if they were alive then, else their prime. */
    val year: Int,
    val age: Int,
    val world: WorldAroundDto?,
    val worldMoment: MomentSummaryDto?,
    val lifeline: List<LifeLineDto>,
    /** Sourced links to other people: curated ones first, then shared documented events. */
    val connections: List<ConnectionDto>,
    val aroundPeople: List<PersonDto>,
    val aroundLives: List<LifeDto>,
    val elsewhere: List<PersonDto>,
    val moments: List<MomentSummaryDto>,
)

data class SearchResultsDto(val people: List<PersonDto>, val moments: List<MomentSummaryDto>)

@Mapper
interface ApiMapper {
    fun toDto(region: Region): RegionDto
    fun toDto(life: Life): LifeDto
    fun toDto(event: Event): EventDto
    fun toDto(artwork: Artwork): ArtworkDto
    fun toDto(world: WorldAround): WorldAroundDto
    fun toDto(card: StoryCard): StoryCardDto

    @Mapping(target = "regionCode", source = "region.code")
    @Mapping(target = "region", source = "region.name")
    fun toDto(era: Era): EraDto

    @Mapping(target = "regionCode", source = "region.code")
    @Mapping(target = "region", source = "region.name")
    fun toDto(person: Person): PersonDto

    fun toDto(participant: EventParticipant): ParticipantDto
}
