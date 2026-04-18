package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.recommendation.model.Confidence
import com.sleepyio.sleepyio.recommendation.model.Factor
import com.sleepyio.sleepyio.recommendation.model.FactorDirection
import com.sleepyio.sleepyio.recommendation.model.Rationale
import com.sleepyio.sleepyio.recommendation.model.StartSitRecommendation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Head-to-head start/sit analyzer.
 *
 * Implements the weighted-factor comparison from `ANALYSIS_FRAMEWORK.md`:
 * for each dimension (projection, matchup, health, usage, game environment)
 * we compare player A to player B and emit a [Factor] whose sign indicates
 * whether that dimension favors starting A (positive) or B (negative). Factor
 * weights default to the framework's Section 1 weights (position-agnostic for
 * this surface — per-player position overrides live in the per-insight math,
 * not the comparison). The final score is recentered so >= 50 always means
 * "start A", < 50 means "start B", and the returned recommendation's score is
 * recalibrated to 0..100 with higher = more confidence in the winner.
 */
class StartSitAnalyzer(
    private val aggregator: InsightAggregator,
) {

    suspend fun analyze(playerAId: String, playerBId: String, week: Int): StartSitRecommendation {
        val both = aggregator.insightFor(listOf(playerAId, playerBId), week)
        val a = both[playerAId] ?: aggregator.insightFor(playerAId, week)
        val b = both[playerBId] ?: aggregator.insightFor(playerBId, week)

        val factors = buildFactors(a, b)

        // Short-circuits from framework section 4.2.
        val shortCircuitA = shortCircuitScore(a)
        val shortCircuitB = shortCircuitScore(b)

        val rawScore = if (shortCircuitA != null || shortCircuitB != null) {
            // If A is OUT, start B decisively (score 5 meaning "start A" → flip to "start B").
            when {
                shortCircuitA != null && shortCircuitB == null -> 5 // start B
                shortCircuitB != null && shortCircuitA == null -> 95 // start A
                else -> 50 // both out / on bye: no meaningful pick
            }
        } else {
            weightedScore(factors)
        }

        val startsA = rawScore >= 50
        val winnerId = if (startsA) playerAId else playerBId

        // Recalibrate: distance from 50 mapped to 50..100 (higher = more confidence in winner).
        val confidenceScore = (50 + abs(rawScore - 50)).coerceIn(0, 100)

        val summary = buildSummary(a, b, startsA, factors)
        val rationale = Rationale(
            factors = factors.ensureMinimumFactors(a, b),
            summary = summary,
        )

        return StartSitRecommendation(
            playerAId = playerAId,
            playerBId = playerBId,
            startPlayerId = winnerId,
            score = confidenceScore,
            confidence = Confidence.fromScore(confidenceScore),
            rationale = rationale,
        )
    }

    /** Always-present factors — projection + matchup — plus any others that differ. */
    private fun buildFactors(a: PlayerInsight, b: PlayerInsight): List<Factor> {
        val out = mutableListOf<Factor>()

        // Projection (weight 25 — analog to "opportunity" at the top of the table,
        // since projection is the single crispest summary of all opportunity signals).
        val medianDelta = a.projection.median - b.projection.median
        out += Factor(
            label = "Projection",
            weight = WEIGHT_PROJECTION,
            evidence = "${a.fullName} projects ${fmt(a.projection.median)} vs " +
                "${b.fullName} at ${fmt(b.projection.median)} (median)",
            direction = direction(medianDelta),
        )

        // Matchup (weight 15).
        val matchupScoreA = matchupScore(a.matchup.grade)
        val matchupScoreB = matchupScore(b.matchup.grade)
        out += Factor(
            label = "Matchup",
            weight = WEIGHT_MATCHUP,
            evidence = "${a.fullName} ${a.matchup.grade} matchup vs " +
                "${b.fullName} ${b.matchup.grade}",
            direction = direction(matchupScoreA - matchupScoreB),
        )

        // Health (weight 12).
        val healthA = healthScore(a)
        val healthB = healthScore(b)
        if (healthA != healthB || a.injury.designation != null || b.injury.designation != null) {
            out += Factor(
                label = "Health",
                weight = WEIGHT_HEALTH,
                evidence = "Injury status: ${a.fullName}=${a.injury.designation ?: "healthy"}, " +
                    "${b.fullName}=${b.injury.designation ?: "healthy"}",
                direction = direction(healthA - healthB),
            )
        }

        // Usage (weight 10 — recent-form proxy).
        val usageA = (a.usage.snapPctLast3 ?: 0.0) + (a.usage.targetShareLast3 ?: 0.0) +
            (a.usage.carryShareLast3 ?: 0.0)
        val usageB = (b.usage.snapPctLast3 ?: 0.0) + (b.usage.targetShareLast3 ?: 0.0) +
            (b.usage.carryShareLast3 ?: 0.0)
        if (usageA != 0.0 || usageB != 0.0) {
            out += Factor(
                label = "Usage",
                weight = WEIGHT_USAGE,
                evidence = "Recent usage index: ${fmt(usageA)} vs ${fmt(usageB)}",
                direction = direction(usageA - usageB),
            )
        }

        // Game environment (weight 10, game script + weather fused).
        val envA = environmentScore(a)
        val envB = environmentScore(b)
        if (envA != envB) {
            out += Factor(
                label = "Game environment",
                weight = WEIGHT_ENV,
                evidence = "Team totals and conditions favor ${if (envA >= envB) a.fullName else b.fullName}",
                direction = direction(envA - envB),
            )
        }

        return out
    }

    /** Guarantee the Rationale invariant: at least 2 factors. */
    private fun List<Factor>.ensureMinimumFactors(a: PlayerInsight, b: PlayerInsight): List<Factor> {
        if (size >= 2) return this
        val padded = toMutableList()
        if (none { it.label == "Matchup" }) {
            padded += Factor(
                label = "Matchup",
                weight = WEIGHT_MATCHUP,
                evidence = "Matchup data unavailable; defaulting to neutral",
                direction = FactorDirection.NEUTRAL,
            )
        }
        return padded
    }

    private fun weightedScore(factors: List<Factor>): Int {
        // Formula from framework 4.1: round( sum(weight * signedStrength) / 2 ) + 50.
        val signed = factors.sumOf { f ->
            val strength = when (f.direction) {
                FactorDirection.POSITIVE -> 1.0
                FactorDirection.NEGATIVE -> -1.0
                FactorDirection.NEUTRAL -> 0.0
            }
            f.weight * strength
        }
        val score = (signed / 2.0).roundToInt() + 50
        return score.coerceIn(0, 100)
    }

    private fun shortCircuitScore(p: PlayerInsight): Int? {
        val raw = p.injury.designation?.uppercase()?.trim()
        return when {
            raw == "OUT" || raw == "O" || raw == "IR" -> 5
            else -> null
        }
    }

    private fun buildSummary(
        a: PlayerInsight, b: PlayerInsight, startsA: Boolean, factors: List<Factor>,
    ): String {
        val winner = if (startsA) a else b
        val loser = if (startsA) b else a
        val lead = factors.filter { it.direction != FactorDirection.NEUTRAL }
            .maxByOrNull { it.weight }
            ?.label
            ?: "projection"
        return "Start ${winner.fullName} over ${loser.fullName} — the $lead edge is the decisive factor."
    }

    private fun direction(delta: Double): FactorDirection = when {
        delta > 0.25 -> FactorDirection.POSITIVE
        delta < -0.25 -> FactorDirection.NEGATIVE
        else -> FactorDirection.NEUTRAL
    }

    private fun matchupScore(grade: MatchupGrade): Int = when (grade) {
        MatchupGrade.ELITE -> 2
        MatchupGrade.GOOD -> 1
        MatchupGrade.NEUTRAL -> 0
        MatchupGrade.TOUGH -> -1
        MatchupGrade.NIGHTMARE -> -2
    }

    private fun healthScore(p: PlayerInsight): Int = when (p.injury.designation?.uppercase()?.trim()) {
        null, "", "H" -> 0
        "Q", "QUESTIONABLE" -> -1
        "D", "DOUBTFUL" -> -2
        "O", "OUT", "IR" -> -3
        else -> 0
    }

    private fun environmentScore(p: PlayerInsight): Double {
        val total = p.gameEnvironment.impliedTeamTotal ?: 22.0
        val windPenalty = if (!p.gameEnvironment.dome && (p.gameEnvironment.windMph ?: 0) >= 15) 2.0 else 0.0
        return total - windPenalty
    }

    private fun fmt(v: Double): String {
        // Avoid java.text on commonMain by rounding to one decimal manually.
        val scaled = (v * 10).roundToInt()
        return "${scaled / 10}.${(scaled % 10 + 10) % 10}"
    }

    companion object {
        internal const val WEIGHT_PROJECTION = 25.0
        internal const val WEIGHT_MATCHUP = 15.0
        internal const val WEIGHT_HEALTH = 12.0
        internal const val WEIGHT_USAGE = 10.0
        internal const val WEIGHT_ENV = 10.0
    }
}
