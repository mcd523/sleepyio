package com.sleepyio.sleepyio.insight.model

/**
 * A fused, UI-ready view of everything we know about a player for a given week.
 *
 * This is the pure domain type — it is NOT a wire format. Data sources produce
 * their own DTOs and the [com.sleepyio.sleepyio.insight.InsightAggregator] is
 * responsible for collapsing those into this shape. Any field that a data
 * source could not supply should arrive as `null` or an empty collection
 * (fail-soft: missing data is a loss of a factor, not an error).
 */
data class PlayerInsight(
    val playerId: String,
    val fullName: String,
    val position: String,
    val team: String?,
    val opponent: String?,
    val week: Int,
    val projection: ProjectionRange,
    val injury: InjuryStatus,
    val usage: UsageTrend,
    val matchup: MatchupRating,
    val gameEnvironment: GameEnvironment,
    val recentNews: List<NewsBlurb>,
)

/**
 * Fantasy point projection as a distribution summary. Floor/median/ceiling are
 * the 20th / 50th / 80th percentile outcomes respectively.
 */
data class ProjectionRange(
    val floor: Double,
    val median: Double,
    val ceiling: Double,
)

/**
 * Current injury state from the NFL official injury report plus practice
 * participation. [designation] is the raw NFL status string (Q, D, O, IR,
 * "Questionable", etc.) — callers normalise as needed.
 */
data class InjuryStatus(
    val designation: String?,
    val bodyPart: String?,
    val practiceStatus: String?,
    val lastUpdatedEpochMs: Long?,
)

/**
 * Rolling 3-week usage snapshot. Percentages are in 0.0..1.0 (NOT 0..100) so
 * downstream math is unambiguous. Any field may be `null` if the source did
 * not report it for this player.
 */
data class UsageTrend(
    val snapPctLast3: Double?,
    val targetShareLast3: Double?,
    val carryShareLast3: Double?,
    val redZoneOppLast3: Int?,
)

/** Discrete matchup difficulty tier used for UI badges and filtering. */
enum class MatchupGrade { ELITE, GOOD, NEUTRAL, TOUGH, NIGHTMARE }

/**
 * Matchup against the player's opponent for this week. [opponentRankVsPos] is
 * the defense's rank against this position (1 = toughest, 32 = softest — NFL
 * convention). [notes] is free-form human-readable context that ends up in
 * the rationale (e.g. "CB1 in coverage on WR1 all game").
 */
data class MatchupRating(
    val grade: MatchupGrade,
    val opponentRankVsPos: Int?,
    val notes: String?,
)

/**
 * Weather + Vegas context for the game. [dome] short-circuits weather
 * reasoning — if true, ignore wind / temp / precipitation in scoring.
 */
data class GameEnvironment(
    val impliedTeamTotal: Double?,
    val spread: Double?,
    val windMph: Int?,
    val precipitationPct: Int?,
    val temperatureF: Int?,
    val dome: Boolean,
)

/**
 * A recent news item that materially affects this player's outlook. The
 * analyzer uses [fantasyImpact] to decide whether to surface the blurb as a
 * green flag, a red flag, or context.
 */
data class NewsBlurb(
    val headline: String,
    val body: String,
    val source: String,
    val publishedEpochMs: Long,
    val fantasyImpact: FantasyImpact,
)

/** Directional impact of a news item on this player's fantasy outlook. */
enum class FantasyImpact { POSITIVE, NEUTRAL, NEGATIVE }
