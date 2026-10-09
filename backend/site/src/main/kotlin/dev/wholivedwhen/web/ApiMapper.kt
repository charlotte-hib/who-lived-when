package dev.wholivedwhen.web

import org.mapstruct.Mapper
import org.mapstruct.Mapping
import dev.wholivedwhen.api.model.ArtworkDto
import dev.wholivedwhen.api.model.EraDto
import dev.wholivedwhen.api.model.EventDto
import dev.wholivedwhen.api.model.LifeArtDto
import dev.wholivedwhen.api.model.LifeDto
import dev.wholivedwhen.api.model.ParticipantDto
import dev.wholivedwhen.api.model.PersonDto
import dev.wholivedwhen.api.model.RegionDto
import dev.wholivedwhen.api.model.StoryCardDto
import dev.wholivedwhen.api.model.WorldAroundDto
import dev.wholivedwhen.domain.Artwork
import dev.wholivedwhen.domain.Era
import dev.wholivedwhen.domain.Event
import dev.wholivedwhen.domain.EventParticipant
import dev.wholivedwhen.domain.Life
import dev.wholivedwhen.domain.LifeArt
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.domain.Region
import dev.wholivedwhen.domain.StoryCard
import dev.wholivedwhen.domain.WorldAround

/** Entities to the API's models, which are generated from api/openapi.yaml (package dev.wholivedwhen.api.model). */
@Mapper
interface ApiMapper {
    fun toDto(region: Region): RegionDto
    fun toDto(life: Life): LifeDto
    fun toDto(art: LifeArt): LifeArtDto
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
