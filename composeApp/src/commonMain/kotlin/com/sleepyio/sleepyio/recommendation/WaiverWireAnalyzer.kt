package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.recommendation.model.Confidence
import com.sleepyio.sleepyio.recommendation.model.Factor
import com.sleepyio.sleepyio.recommendation.model.FactorDirection
import com.sleepyio.sleepyio.recommendation.model.Rationale
import com.sleepyio.sleepyio.recommendation.model.WaiverTarget
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.min

/**
 * Waiver-wire analyzer.
 *
 * Union-rolls every league roster into an "owned" set, pulls trending adds
 * from Sleeper, keeps only not-owned players, then scores each candidate
 * against the breakout indicators from `ANALYSIS_FRAMEWORK.md` section 3.1.
 * FAAB recommendations map priority score onto the tier table in section 3.2.
 */
class WaiverWireAnalyzer(
    private val aggregator: InsightAggregator,
    private val sleeper: SleeperClient = SleeperClient,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun findTargets(
        leagueId: Long,
        rosterId: Long,
        week: Int,
        limit: Int = 5,
    ): List<WaiverTarget> {
        val ownedIds: Set<String> = try {
            sleeper.getRostersInLeague(leagueId).flatMap { it.players }.toSet()
        } catch (e: Exception) {
            logger.error(e) { "WaiverWireAnalyzer failed to load rosters for league=$leagueId" }
            emptySet()
        }

        val trending = try {
            sleeper.getTrendingPlayers("nfl", "add", 24, 50)
        } catch (e: Exception) {
            logger.error(e) { "WaiverWireAnalyzer failed to load trending adds" }
            emptyList()
        }

        val candidates = trending.map { it.playerId }.filter { it !in ownedIds }
        if (candidates.isEmpty()) return emptyList()

        val insights = aggregator.insightFor(candidates, week)

        return candidates.mapNotNull { id ->
            val insight = insights[id] ?: return@mapNotNull null
            val (priority, factors) = score(insight)
            if (factors.size < 2) return@mapNotNull null
            val faab = faabPctFor(priority, week)
            val confidenceScore = min(100, 40 + priority / 2)
            WaiverTarget(
                playerId = id,
                priorityScore = priority,
                suggestedFaabPct = faab,
                dropCandidates = emptyList(),
                score = confidenceScore,
                confidence = Confidence.fromScore(confidenceScore),
                rationale = Rationale(
                    factors = factors,
                    summary = "Add ${insight.fullName}: priority $priority / 100, bid ~$faab% FAAB.",
                ),
            )
        }.sortedByDescending { it.priorityScore }.take(limit)
    }

    private fun score(p: PlayerInsight): Pair<Int, List<Factor>> {
        var raw = 0
        val factors = mutableListOf<Factor>()

        val snap = p.usage.snapPctLast3 ?: 0.0
        if (snap >= 0.55) {
            raw += 30
            factors += Factor(
                label = "Snap share",
                weight = 30.0,
                evidence = "Snap share trending at ${(snap * 100).toInt()}% over last 3 weeks",
                direction = FactorDirection.POSITIVE,
            )
        }

        val targetShare = p.usage.targetShareLast3 ?: 0.0
        if (targetShare >= 0.18) {
            raw += 25
            factors += Factor(
                label = "Target share",
                weight = 25.0,
                evidence = "Target share ${(targetShare * 100).toInt()}% (>= 18% breakout threshold)",
                direction = FactorDirection.POSITIVE,
            )
        }

        val rz = p.usage.redZoneOppLast3 ?: 0
        if (rz >= 3) {
            raw += 18
            factors += Factor(
                label = "Red zone",
                weight = 18.0,
                evidence = "$rz red-zone opportunities over trailing window",
                direction = FactorDirection.POSITIVE,
            )
        }

        val matchup = p.matchup.grade
        if (matchup == MatchupGrade.ELITE || matchup == MatchupGrade.GOOD) {
            raw += 10
            factors += Factor(
                label = "Upcoming matchup",
                weight = 10.0,
                evidence = "Matchup grade $matchup",
                direction = FactorDirection.POSITIVE,
            )
        }

        if (p.projection.median >= 10.0) {
            raw += 8
            factors += Factor(
                label = "Projection",
                weight = 8.0,
                evidence = "Median projection ${p.projection.median} pts",
                direction = FactorDirection.POSITIVE,
            )
        }

        // Negative factor: active injury designation on the candidate themselves.
        val designation = p.injury.designation?.uppercase()
        if (designation == "OUT" || designation == "O" || designation == "IR") {
            raw -= 40
            factors += Factor(
                label = "Injury risk",
                weight = 40.0,
                evidence = "Candidate currently $designation — delay the add",
                direction = FactorDirection.NEGATIVE,
            )
        }

        return raw.coerceIn(0, 100) to factors
    }

    /**
     * Priority-score → FAAB % of remaining budget, per section 3.2 of the
     * framework. Applies week-based scaling: weeks 1-3 reduce 25%.
     */
    private fun faabPctFor(priority: Int, week: Int): Int {
        val base = when {
            priority >= 80 -> 50 // Tier 1
            priority >= 60 -> 24 // Tier 2 midpoint
            priority >= 30 -> 7  // Tier 3 midpoint
            else -> 1            // Tier 0
        }
        return if (week in 1..3) (base * 3 / 4).coerceAtLeast(0) else base
    }
}
