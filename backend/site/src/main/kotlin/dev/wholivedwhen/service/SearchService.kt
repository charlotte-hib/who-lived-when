package dev.wholivedwhen.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import dev.wholivedwhen.repository.PersonRepository
import dev.wholivedwhen.support.foldForSearch
import dev.wholivedwhen.web.ApiMapper
import dev.wholivedwhen.web.SearchResultsDto

private const val MAX_PEOPLE = 6
private const val MAX_MOMENTS = 4

/**
 * Accent-insensitive search over names and moments, so "zola" finds Émile Zola and "kyoto" finds Kyoto, 1590s.
 * Done in memory: the curated catalogue is small. A database index takes over when it grows.
 */
@Service
@Transactional(readOnly = true)
class SearchService(
    private val people: PersonRepository,
    private val momentService: MomentService,
    private val mapper: ApiMapper,
) {
    fun search(query: String): SearchResultsDto {
        val q = foldForSearch(query.trim())
        if (q.length < 2) return SearchResultsDto(emptyList(), emptyList())

        return SearchResultsDto(
            people = people.findAll()
                .filter { foldForSearch(it.name).contains(q) }
                // Names that start with the query first, then in order of birth.
                .sortedWith(compareBy({ !foldForSearch(it.name).startsWith(q) }, { it.birthYear }, { it.id }))
                .take(MAX_PEOPLE)
                .map(mapper::toDto),
            moments = momentService.list()
                .filter { foldForSearch("${it.place} ${it.region} ${it.period}").contains(q) }
                .take(MAX_MOMENTS),
        )
    }
}
