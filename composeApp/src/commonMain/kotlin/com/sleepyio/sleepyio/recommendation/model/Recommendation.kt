package com.sleepyio.sleepyio.recommendation.model

/**
 * Sealed polymorphic recommendation type. Every concrete recommendation carries
 * a 0..100 [score], a discrete [confidence] tier, and a [rationale] that lists
 * the factors driving the call.
 *
 * New recommendation shapes (trade proposals, playoff-schedule advice, etc.)
 * slot in as additional sealed implementors without touching existing code.
 */
sealed interface Recommendation {
    val score: Int
    val confidence: Confidence
    val rationale: Rationale
}

/**
 * Head-to-head start/sit call between two rostered players. The UI uses this
 * for the "Who should I start: A or B?" surface and also for FLEX tiebreakers.
 *
 * Invariant: [startPlayerId] must equal either [playerAId] or [playerBId].
 */
data class StartSitRecommendation(
    val playerAId: String,
    val playerBId: String,
    val startPlayerId: String,
    override val score: Int,
    override val confidence: Confidence,
    override val rationale: Rationale,
) : Recommendation

/**
 * A single slot in a proposed starting lineup.
 *
 * [slot] is the roster slot label ("QB", "RB", "WR", "FLEX", "TE", "K",
 * "DEF", "SUPER_FLEX", etc.) — it mirrors Sleeper's slot vocabulary so the
 * UI and the Sleeper write-back path stay in sync.
 */
data class LineupSlot(
    val slot: String,
    val playerId: String,
    val reason: String,
)

/**
 * Full weekly lineup recommendation for a single roster. [benchings] is the
 * list of player IDs the analyzer is explicitly recommending the user bench
 * (players on the roster who did not make the starting lineup, ordered by
 * how close they came to starting).
 */
data class LineupRecommendation(
    val rosterId: Long,
    val week: Int,
    val starters: List<LineupSlot>,
    val benchings: List<String>,
    override val score: Int,
    override val confidence: Confidence,
    override val rationale: Rationale,
) : Recommendation

/**
 * A waiver-wire add recommendation.
 *
 * [priorityScore] is the 0..100 breakout priority from the waiver analyzer
 * (separate from the confidence [score] on the recommendation itself, which
 * is about how sure we are of the add). [suggestedFaabPct] is a percentage of
 * REMAINING FAAB budget, 0..100. [dropCandidates] are ordered worst-first —
 * the UI presents the first as the default drop.
 */
data class WaiverTarget(
    val playerId: String,
    val priorityScore: Int,
    val suggestedFaabPct: Int,
    val dropCandidates: List<String>,
    override val score: Int,
    override val confidence: Confidence,
    override val rationale: Rationale,
) : Recommendation
