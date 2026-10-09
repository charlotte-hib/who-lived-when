package dev.wholivedwhen.dataset

/**
 * One person of the fetched dataset: facts as Wikidata states them, with no decision applied (corrections, regions and
 * domains come from `data/`, at compile time). Years count as the site does: negative for BCE, no year 0.
 */
data class PersonRow(
    val qid: String,
    /** The English label, else the English Wikipedia title, else the label shared by all languages, else any. */
    val label: String,
    /** The French label, else the label shared by all languages. */
    val labelFr: String?,
    val sitelinks: Int,
    val born: Int,
    /** Wikidata's precision: 9 a year, 8 a decade, 7 a century, 10 and 11 a month and a day. */
    val bornPrecision: Int,
    val died: Int,
    /** Null when the year of death is estimated. */
    val diedPrecision: Int?,
    val diedEstimated: Boolean,
    /** A date coarser than a year, a "circa", or an estimated death. */
    val datesApproximate: Boolean,
    /** Sex or gender (P21), as a Q-id: only to choose the feminine form of an occupation in French. */
    val gender: String?,
    val enwiki: String?,
    val frwiki: String?,
    val occupations: List<PersonOccupation>,
    val places: List<PersonPlace>,
    val flags: Set<PersonFlag>,
)

/** One of a person's occupations (P106), in Wikidata's order, best rank first. */
data class PersonOccupation(val occupation: String, val referenced: Boolean)

/** A place of birth (P19), death (P20), work (P937) or residence (P551), with its start and end years when stated. */
data class PersonPlace(val property: String, val place: String, val startYear: Int?, val endYear: Int?, val referenced: Boolean)

/** Why a person's facts need a person's eye: the flag of the review policy, and the reason for it. */
enum class PersonFlag(val flag: String) {
    BIRTH_UNREFERENCED(POORLY_DOCUMENTED),
    DEATH_UNREFERENCED(POORLY_DOCUMENTED),
    /** The first occupation, the one the site shows. */
    OCCUPATION_UNREFERENCED(POORLY_DOCUMENTED),
    BIRTH_IMPRECISE(POORLY_DOCUMENTED),
    DEATH_IMPRECISE(POORLY_DOCUMENTED),
    DEATH_ESTIMATED(POORLY_DOCUMENTED),
    /** Fewer than 25 sitelinks, and no article in English or French Wikipedia. */
    FEW_SITELINKS_NO_ARTICLE(POORLY_DOCUMENTED),
    /** Several dates of birth that disagree: the earliest was taken. */
    BIRTH_DISAGREES(CONFLICT),
    DEATH_DISAGREES(CONFLICT),
}

private const val POORLY_DOCUMENTED = "POORLY_DOCUMENTED"
private const val CONFLICT = "CONFLICT"

/** An occupation, or a class above one (P279), with its labels and its classes. */
data class OccupationRow(
    val qid: String,
    /** The English label, else the label shared by all languages, else any. */
    val label: String,
    val labelFr: String?,
    /** The French feminine form (P2521), for women. */
    val femaleLabelFr: String?,
    /** The classes it belongs to, best rank first. */
    val parents: List<String>,
)
