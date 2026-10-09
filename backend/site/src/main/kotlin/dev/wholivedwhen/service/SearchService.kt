package dev.wholivedwhen.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.repository.PersonRepository
import dev.wholivedwhen.support.foldForSearch
import dev.wholivedwhen.web.ApiMapper
import dev.wholivedwhen.api.model.SearchResultsDto

private const val MAX_PEOPLE = 6
private const val MAX_MOMENTS = 4

/**
 * Accent-insensitive search over names and moments, so "zola" finds Émile Zola and "kyoto" finds Kyoto, 1590s.
 * People are searched in Postgres; names with a typo only fill the list when too few names contain the query, as
 * typo matching is slow on short, common queries. Moments, few and curated, are searched in memory.
 */
@Service
@Transactional(readOnly = true)
class SearchService(
    private val people: PersonRepository,
    private val momentService: MomentService,
    private val mapper: ApiMapper,
) {
    fun search(query: String): SearchResultsDto {
        val trimmed = query.trim()
        val q = foldForSearch(trimmed)
        if (q.length < 2) return SearchResultsDto(emptyList(), emptyList())

        return SearchResultsDto(
            people = findPeople(trimmed).map(mapper::toDto),
            moments = momentService.list()
                .filter { foldForSearch("${it.place} ${it.region} ${it.period}").contains(q) }
                .take(MAX_MOMENTS),
        )
    }

    private fun findPeople(query: String): List<Person> {
        val containing = people.findNameContaining(escapeLike(query), MAX_PEOPLE)
        if (containing.size == MAX_PEOPLE) return containing
        val ids = containing.map { it.id }.toSet()
        return containing + people.findNameSimilar(query, MAX_PEOPLE).filter { it.id !in ids }.take(MAX_PEOPLE - containing.size)
    }

    private fun escapeLike(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
