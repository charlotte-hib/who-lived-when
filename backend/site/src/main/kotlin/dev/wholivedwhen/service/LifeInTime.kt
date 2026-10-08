package dev.wholivedwhen.service

import dev.wholivedwhen.domain.Era
import dev.wholivedwhen.domain.Event
import dev.wholivedwhen.domain.Person
import dev.wholivedwhen.support.currentYear
import dev.wholivedwhen.support.yearsBetween
import dev.wholivedwhen.api.model.LifeLineDto
import dev.wholivedwhen.api.model.LifeLineKindDto

/** How many events the person is not part of may appear, so their own moments stand out. */
private const val MAX_BACKGROUND_EVENTS = 4

/**
 * "Their life in their time": who governed when they were born, each change of regime during their life,
 * the documented events around them and their own, each with their age. Built only from dated records.
 */
fun lifeInTime(person: Person, regionEras: List<Era>, regionEvents: List<Event>): List<LifeLineDto> {
    val end = person.deathYear ?: currentYear()
    val age = { year: Int -> yearsBetween(person.birthYear, year) }
    val lines = mutableListOf<LifeLineDto>()

    val bornUnder = regionEras.lastOrNull { person.birthYear in it.startYear..it.endYear }
    lines += LifeLineDto(person.birthYear, null, LifeLineKindDto.BIRTH, "Born" + (bornUnder?.let { " ${regimePhrase(it.label)}" } ?: ""), null)

    regionEras
        .filter { it.startYear > person.birthYear && it.startYear <= end }
        .forEach { lines += LifeLineDto(it.startYear, age(it.startYear), LifeLineKindDto.ERA, "${withArticle(it.label)} begins", null) }

    var background = 0
    regionEvents
        .filter { it.year in person.birthYear..end }
        .sortedBy { it.year }
        .forEach { event ->
            val own = event.participants.firstOrNull { it.person.slug == person.slug }
            when {
                own != null -> lines += LifeLineDto(event.year, age(event.year), LifeLineKindDto.OWN_EVENT, event.title, own.role)
                background++ < MAX_BACKGROUND_EVENTS -> lines += LifeLineDto(event.year, age(event.year), LifeLineKindDto.EVENT, event.title, null)
            }
        }

    person.deathYear?.let { lines += LifeLineDto(it, age(it), LifeLineKindDto.DEATH, "Dies", null) }
    return lines.sortedWith(compareBy({ it.year }, { it.kind.ordinal }))
}

// Place names read without an article ("in Roman Gaul"); regimes take one ("under the Third Republic").
private val PLACE_LIKE = "^(Celtic|Roman|Iron Age|Anglo-Saxon|Frankish|West Francia|Modern)".toRegex()
private val REGIME = "(republic|empire|monarchy|restoration|government|regime|shogunate)".toRegex(RegexOption.IGNORE_CASE)

private fun withArticle(label: String) =
    if (PLACE_LIKE.containsMatchIn(label) && !REGIME.containsMatchIn(label)) label else "The $label"

private fun regimePhrase(label: String) = when {
    REGIME.containsMatchIn(label) -> "under the $label"
    PLACE_LIKE.containsMatchIn(label) -> "in $label"
    else -> "in the $label"
}
