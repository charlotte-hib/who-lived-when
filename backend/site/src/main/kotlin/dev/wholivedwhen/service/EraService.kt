package dev.wholivedwhen.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import dev.wholivedwhen.repository.EraRepository
import dev.wholivedwhen.repository.EventRepository
import dev.wholivedwhen.repository.PersonRepository
import dev.wholivedwhen.web.ApiMapper
import dev.wholivedwhen.api.model.EraDetailDto
import dev.wholivedwhen.api.model.EraDto

@Service
@Transactional(readOnly = true)
class EraService(
    private val eras: EraRepository,
    private val people: PersonRepository,
    private val events: EventRepository,
    private val moments: MomentService,
    private val mapper: ApiMapper,
) {
    fun list(): List<EraDto> = eras.findAllByOrderByStartYearAscIdAsc().map(mapper::toDto)

    fun detail(id: String): EraDetailDto {
        val era = eras.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown era: $id")
        }
        return EraDetailDto(
            era = mapper.toDto(era),
            people = people.findAliveBetween(era.region.code, era.startYear, era.endYear)
                .map(mapper::toDto)
                .groupBy { it.domain.value },
            events = events.findByEraIdOrderByYearAscIdAsc(id).map(mapper::toDto),
            moments = moments.overlapping(era.region.code, era.startYear, era.endYear),
        )
    }
}
