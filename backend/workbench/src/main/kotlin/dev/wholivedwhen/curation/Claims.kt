package dev.wholivedwhen.curation

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import dev.wholivedwhen.support.slugify
import dev.wholivedwhen.workbench.api.model.AnecdoteDto
import dev.wholivedwhen.workbench.api.model.ClaimDetailDto
import dev.wholivedwhen.workbench.api.model.ClaimStatusDto
import dev.wholivedwhen.workbench.api.model.ClaimTypeDto
import dev.wholivedwhen.workbench.api.model.ConnectionDto
import dev.wholivedwhen.workbench.api.model.DecisionDto
import dev.wholivedwhen.workbench.api.model.EventDto
import dev.wholivedwhen.workbench.api.model.FlagDto
import dev.wholivedwhen.workbench.api.model.FlagReasonDto
import dev.wholivedwhen.workbench.api.model.NewClaimDto
import dev.wholivedwhen.workbench.api.model.NewDecisionDto
import dev.wholivedwhen.workbench.api.model.OriginDto
import dev.wholivedwhen.workbench.api.model.PayloadDto
import dev.wholivedwhen.workbench.api.model.PersonFactsDto
import dev.wholivedwhen.workbench.api.model.SourceDto
import java.security.MessageDigest

/** A claim the workbench proposes: its record as fetched or drafted, where it came from, its flags and sources. */
data class Proposal(val payload: PayloadDto, val origin: OriginDto, val flags: List<FlagReasonDto>, val sources: List<SourceDto>)

/** What proposing came to: claims added, claims whose proposal changed, and claims left as they were. */
data class ProposalCounts(val added: Int, val refreshed: Int, val unchanged: Int) {
    override fun toString() = "$added added, $refreshed refreshed, $unchanged unchanged"
}

/**
 * Claims and the curator's decisions on them. Every change of status or payload is logged with who made it: the
 * curator (through the API), or the workbench's rules when it proposes or refreshes a claim.
 */
@Service
class Claims(private val store: CurationStore, private val transactions: TransactionTemplate) {

    fun detail(id: String): ClaimDetailDto = detail(find(id))

    /** Adds a claim the curator found, waiting for review. Only one source: flagged, for support to be looked for. */
    fun add(claim: NewClaimDto, by: String): ClaimDetailDto = transactions.execute {
        if (claim.payload is PersonFactsDto) badRequest("A person's facts are fetched from Wikidata, not added by hand")
        check(claim.payload)
        claim.sources.forEach(::check)
        val flags = if (claim.sources.size == 1) listOf(FlagReasonDto(FlagDto.SINGLE_SOURCE, "ONE_SOURCE")) else emptyList()
        val stored = StoredClaim(idOf(claim.payload), 0, typeOf(claim.payload), ClaimStatusDto.CANDIDATE, OriginDto.MANUAL, claim.payload, claim.payload, flags)
        if (!store.insert(stored)) throw ResponseStatusException(HttpStatus.CONFLICT, "${stored.id} exists already")
        store.addSources(stored.id, claim.sources)
        store.log(stored.id, Decision(by, null, ClaimStatusDto.CANDIDATE, null, claim.payload, claim.note))
        detail(stored.id)
    }!!

    /** Approves (the edited payload, if given), rejects or keeps the claim for later. */
    fun decide(id: String, decision: NewDecisionDto, by: String): ClaimDetailDto = transactions.execute {
        val claim = find(id)
        val payload = decision.payload ?: claim.payload
        if (decision.payload != null) {
            if (typeOf(payload) != claim.type || subjectOf(payload) != subjectOf(claim.payload)) {
                badRequest("An edit must stay a ${claim.type.value} about ${subjectOf(claim.payload)}")
            }
            check(payload)
        }
        val status = when (decision.action) {
            NewDecisionDto.Action.APPROVE -> ClaimStatusDto.APPROVED
            NewDecisionDto.Action.REJECT -> ClaimStatusDto.REJECTED
            NewDecisionDto.Action.NEEDS_WORK -> ClaimStatusDto.NEEDS_WORK
        }
        if (status != ClaimStatusDto.APPROVED && decision.note.isNullOrBlank()) badRequest("Say why, in a note")
        // Deciding settles the conflicts a refresh found.
        store.update(claim.copy(status = status, payload = payload, flags = fetchedFlags(claim)))
        store.log(id, Decision(by, claim.status, status, claim.payload, payload, decision.note))
        detail(id)
    }!!

    /** Puts back the claim as it was before the curator's last decision, if that is the claim's last decision. */
    fun undo(id: String, by: String): ClaimDetailDto = transactions.execute {
        val claim = find(id)
        val (last, decision) = store.decisions(id).last().let { it.id to it.decision }
        if (decision.by != by || decision.from == null || decision.undoes != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "The claim's last decision is not one to undo")
        }
        val before = decision.payloadBefore!!
        store.update(claim.copy(status = decision.from, payload = before))
        store.log(id, Decision(by, claim.status, decision.from, claim.payload, before, "Undone", undoes = last))
        detail(id)
    }!!

    /**
     * Adds the claims of [proposals] that are new, and refreshes those whose proposal changed. A claim not yet decided
     * takes the new proposal, unless the curator edited it. An approved one goes back to review, flagged: what it
     * rests on changed. A rejected one stays rejected.
     */
    fun propose(proposals: List<Proposal>): ProposalCounts {
        var added = 0
        var refreshed = 0
        for (proposal in proposals) transactions.executeWithoutResult {
            val id = idOf(proposal.payload)
            val claim = store.find(id)
            when {
                claim == null -> {
                    store.insert(
                        StoredClaim(id, 0, typeOf(proposal.payload), ClaimStatusDto.CANDIDATE, proposal.origin, proposal.payload, proposal.payload, proposal.flags),
                    )
                    store.log(id, Decision(RULES, null, ClaimStatusDto.CANDIDATE, null, proposal.payload, "Proposed"))
                    added++
                }
                claim.proposal != proposal.payload || fetchedFlags(claim) != proposal.flags -> {
                    refreshed++
                    store.update(refresh(claim, proposal))
                    val after = store.find(id)!!
                    if (after.status != claim.status || after.payload != claim.payload) {
                        store.log(id, Decision(RULES, claim.status, after.status, claim.payload, after.payload, "Refreshed"))
                    }
                }
            }
            store.addSources(id, proposal.sources)
        }
        return ProposalCounts(added, refreshed, proposals.size - added - refreshed)
    }

    /** The claim after [proposal], keeping the conflicts earlier refreshes found until the curator decides again. */
    private fun refresh(claim: StoredClaim, proposal: Proposal): StoredClaim {
        val conflicts = claim.flags - fetchedFlags(claim).toSet()
        val flags = proposal.flags + conflicts
        if (claim.proposal == proposal.payload) return claim.copy(flags = flags)
        return when {
            claim.status == ClaimStatusDto.REJECTED -> claim.copy(proposal = proposal.payload, flags = flags)
            claim.status == ClaimStatusDto.APPROVED ->
                claim.copy(status = ClaimStatusDto.CANDIDATE, proposal = proposal.payload, flags = (flags + CHANGED_SINCE_APPROVED).distinct())
            claim.payload != claim.proposal ->
                claim.copy(proposal = proposal.payload, flags = (flags + CHANGED_SINCE_EDITED).distinct())
            else -> claim.copy(payload = proposal.payload, proposal = proposal.payload, flags = flags)
        }
    }

    /** The flags the claim's proposal brought, without the conflicts refreshes found. */
    private fun fetchedFlags(claim: StoredClaim) = claim.flags - setOf(CHANGED_SINCE_APPROVED, CHANGED_SINCE_EDITED)

    private fun find(id: String): StoredClaim =
        store.find(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No claim $id")

    private fun detail(claim: StoredClaim) = ClaimDetailDto(
        id = claim.id,
        seq = claim.seq,
        type = claim.type,
        status = claim.status,
        origin = claim.origin,
        flags = claim.flags,
        payload = claim.payload,
        proposal = claim.proposal,
        sources = store.sources(claim.id),
        decisions = store.decisions(claim.id).map { (_, decidedAt, it) ->
            DecisionDto(
                by = it.by, to = it.to, decidedAt = decidedAt, from = it.from,
                edited = it.payloadBefore != null && it.payloadBefore != it.payloadAfter, note = it.note,
            )
        },
    )

    /** What the spec cannot say: no year 0, and two different people in a connection. */
    private fun check(payload: PayloadDto) {
        val years = when (payload) {
            is PersonFactsDto -> listOf(payload.born, payload.died)
            is ConnectionDto -> listOf(payload.year)
            is EventDto -> listOf(payload.year)
            is AnecdoteDto -> listOf(payload.year)
        }
        if (0 in years) badRequest("There is no year 0: 1 BCE is followed by 1 CE")
        if (payload is ConnectionDto && payload.people.distinct().size != 2) badRequest("A connection links two different people")
    }

    private fun check(source: SourceDto) {
        val complete = when (source.kind) {
            SourceDto.Kind.WIKIDATA -> source.entity != null
            SourceDto.Kind.WIKIPEDIA -> source.language != null && source.title != null
            SourceDto.Kind.BOOK -> source.title != null && source.page != null
            SourceDto.Kind.WEB -> source.url != null
            SourceDto.Kind.MEDIA -> source.title != null && (source.url != null || source.episode != null)
        }
        if (!complete) badRequest("A ${source.kind.value} source needs ${REQUIRED.getValue(source.kind)}")
    }

    private fun badRequest(reason: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    companion object {
        /** Who the workbench's own decisions are logged as: proposing and refreshing claims. */
        const val RULES = "rule"

        private val CHANGED_SINCE_APPROVED = FlagReasonDto(FlagDto.CONFLICT, "CHANGED_SINCE_APPROVED")
        private val CHANGED_SINCE_EDITED = FlagReasonDto(FlagDto.CONFLICT, "CHANGED_SINCE_EDITED")

        private val REQUIRED = mapOf(
            SourceDto.Kind.WIKIDATA to "an entity",
            SourceDto.Kind.WIKIPEDIA to "a language and a title",
            SourceDto.Kind.BOOK to "a title and a page",
            SourceDto.Kind.WEB to "a URL",
            SourceDto.Kind.MEDIA to "a title, and a URL or an episode",
        )

        fun typeOf(payload: PayloadDto): ClaimTypeDto = when (payload) {
            is PersonFactsDto -> ClaimTypeDto.PERSON_FACTS
            is ConnectionDto -> ClaimTypeDto.CONNECTION
            is EventDto -> ClaimTypeDto.EVENT
            is AnecdoteDto -> ClaimTypeDto.ANECDOTE
        }

        /**
         * A claim's id, from what it is about, so the same thing proposed again lands on the same claim:
         * `person-facts:Q535`, `connection:Q296|Q535:friends`, `event:FR:-52:vercingetorix-surrenders-at-alesia`,
         * `anecdote:Q535:1851:` and the start of the text's SHA-256.
         */
        fun idOf(payload: PayloadDto): String = when (payload) {
            is PersonFactsDto -> "person-facts:${payload.person}"
            is ConnectionDto -> "connection:${payload.people.sorted().joinToString("|")}:${slugify(payload.kind)}"
            is EventDto -> "event:${payload.region}:${payload.year}:${slugify(payload.title)}"
            is AnecdoteDto -> "anecdote:${payload.person}:${payload.year}:${sha256(payload.text.trim()).take(12)}"
        }

        /** Whom or where a claim is about: an edit must not change it. */
        private fun subjectOf(payload: PayloadDto): String = when (payload) {
            is PersonFactsDto -> payload.person
            is ConnectionDto -> payload.people.sorted().joinToString(" and ")
            is EventDto -> payload.region
            is AnecdoteDto -> payload.person
        }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
