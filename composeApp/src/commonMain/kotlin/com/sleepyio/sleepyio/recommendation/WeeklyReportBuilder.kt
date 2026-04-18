package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.recommendation.model.Confidence
import com.sleepyio.sleepyio.recommendation.model.Factor
import com.sleepyio.sleepyio.recommendation.model.FactorDirection
import com.sleepyio.sleepyio.recommendation.model.LineupRecommendation
import com.sleepyio.sleepyio.recommendation.model.LineupSlot
import com.sleepyio.sleepyio.recommendation.model.Rationale
import com.sleepyio.sleepyio.recommendation.model.StartSitRecommendation
import com.sleepyio.sleepyio.recommendation.model.WeeklyReport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs

/**
 * Builds the full weekly digest for a roster.
 *
 * Pulls the roster + every rostered player's insight in one aggregator batch,
 * runs the slot-fill lineup optimizer, diffs projected starters against the
 * bench to surface the three closest start/sit calls, flags risky starters,
 * dials up waiver suggestions via [WaiverWireAnalyzer], and synthesises a
 * one-sentence headline for the notification row.
 */
class WeeklyReportBuilder(
    private val sleeper: SleeperClient,
    private val aggregator: InsightAggregator,
    private val startSit: StartSitAnalyzer,
    private val waivers: WaiverWireAnalyzer,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun build(leagueId: Long, rosterId: Long, week: Int): WeeklyReport {
        val roster: SleeperRoster? = try {
            sleeper.getRostersInLeague(leagueId).firstOrNull { it.rosterId.toLong() == rosterId }
        } catch (e: Exception) {
            logger.error(e) { "WeeklyReportBuilder failed to load rosters for league=$leagueId" }
            null
        }
        val players = roster?.players.orEmpty()
        val insights = if (players.isEmpty()) emptyMap() else aggregator.insightFor(players, week)

        val allPlayers: Map<String, SleeperPlayer> = try {
            sleeper.getAllPlayers("nfl")
        } catch (e: Exception) {
            logger.error(e) { "WeeklyReportBuilder failed to load all players" }
            emptyMap()
        }

        val starters = if (roster != null) selectLineup(roster, insights, allPlayers) else emptyList()
        val starterIds = starters.map { it.playerId }.toSet()
        val benchings = players.filter { it !in starterIds }

        val closeCalls = findClosestCalls(starters, benchings, insights, week)
        val risky = starters.filter { slot ->
            val p = insights[slot.playerId] ?: return@filter false
            hasNegativeHighWeightFactor(p)
        }.map { it.playerId }

        val lineupRationale = buildLineupRationale(starters, insights)
        val lineupScore = lineupScore(starters, insights)
        val lineup = LineupRecommendation(
            rosterId = rosterId,
            week = week,
            starters = starters,
            benchings = benchings,
            score = lineupScore,
            confidence = Confidence.fromScore(lineupScore),
            rationale = lineupRationale,
        )

        val waiverTargets = try {
            waivers.findTargets(leagueId, rosterId, week)
        } catch (e: Exception) {
            logger.error(e) { "WeeklyReportBuilder failed to load waiver targets" }
            emptyList()
        }

        val headline = buildHeadline(starters, insights)

        return WeeklyReport(
            leagueId = leagueId,
            rosterId = rosterId,
            week = week,
            lineup = lineup,
            startSitDecisions = closeCalls,
            waiverTargets = waiverTargets,
            riskyStarters = risky,
            headlineAdvice = headline,
        )
    }

    private suspend fun findClosestCalls(
        starters: List<LineupSlot>,
        bench: List<String>,
        insights: Map<String, PlayerInsight>,
        week: Int,
    ): List<StartSitRecommendation> {
        if (starters.isEmpty() || bench.isEmpty()) return emptyList()

        // For each starter, pick the bench player at the same position whose
        // projection is closest — that's the margin the user cares about.
        val pairs: List<Pair<String, String>> = starters.mapNotNull { slot ->
            val starter = insights[slot.playerId] ?: return@mapNotNull null
            val sameSlotBench = bench.mapNotNull { insights[it] }
                .filter { slotOf(it.position, slot.slot) }
            val best = sameSlotBench.minByOrNull {
                abs(it.projection.median - starter.projection.median)
            } ?: return@mapNotNull null
            slot.playerId to best.playerId
        }

        val scored = pairs.map { (a, b) ->
            val delta = abs(
                (insights[a]?.projection?.median ?: 0.0) -
                    (insights[b]?.projection?.median ?: 0.0)
            )
            Triple(a, b, delta)
        }.sortedBy { it.third }.take(3)

        return scored.map { (a, b, _) -> startSit.analyze(a, b, week) }
    }

    private fun slotOf(position: String, slot: String): Boolean = when (slot.uppercase()) {
        "QB" -> position == "QB"
        "RB" -> position == "RB"
        "WR" -> position == "WR"
        "TE" -> position == "TE"
        "K" -> position == "K"
        "DEF", "DST" -> position == "DEF"
        "FLEX" -> position in setOf("RB", "WR", "TE")
        "SUPER_FLEX" -> position in setOf("QB", "RB", "WR", "TE")
        else -> false
    }

    private fun hasNegativeHighWeightFactor(p: PlayerInsight): Boolean {
        val designation = p.injury.designation?.uppercase()
        if (designation in setOf("Q", "D", "O", "OUT", "IR", "DOUBTFUL", "QUESTIONABLE")) return true
        if (p.matchup.grade == com.sleepyio.sleepyio.insight.model.MatchupGrade.NIGHTMARE) return true
        if (!p.gameEnvironment.dome && (p.gameEnvironment.windMph ?: 0) >= 20) return true
        return false
    }

    private fun buildLineupRationale(
        starters: List<LineupSlot>,
        insights: Map<String, PlayerInsight>,
    ): Rationale {
        val totalMedian = starters.sumOf { insights[it.playerId]?.projection?.median ?: 0.0 }
        val strongest = starters.maxByOrNull { insights[it.playerId]?.projection?.median ?: 0.0 }
        val factors = mutableListOf<Factor>()
        factors += Factor(
            label = "Projected total",
            weight = 25.0,
            evidence = "Lineup projects for $totalMedian median fantasy points",
            direction = FactorDirection.POSITIVE,
        )
        if (strongest != null) {
            val p = insights[strongest.playerId]
            if (p != null) {
                factors += Factor(
                    label = "Anchor start",
                    weight = 15.0,
                    evidence = "${p.fullName} is the top projection at ${p.projection.median}",
                    direction = FactorDirection.POSITIVE,
                )
            }
        }
        if (factors.size < 2) {
            factors += Factor(
                label = "Slot coverage",
                weight = 10.0,
                evidence = "All required slots filled with best available projections",
                direction = FactorDirection.NEUTRAL,
            )
        }
        return Rationale(
            factors = factors,
            summary = "Optimal lineup selected by greedy slot fill on median projection.",
        )
    }

    private fun lineupScore(
        starters: List<LineupSlot>,
        insights: Map<String, PlayerInsight>,
    ): Int {
        if (starters.isEmpty()) return 50
        val total = starters.sumOf { insights[it.playerId]?.projection?.median ?: 0.0 }
        // Map 80..160 projected points -> 40..95 confidence.
        val clamped = total.coerceIn(60.0, 180.0)
        return (40 + ((clamped - 60.0) / 120.0) * 55).toInt().coerceIn(0, 100)
    }

    private fun buildHeadline(
        starters: List<LineupSlot>,
        insights: Map<String, PlayerInsight>,
    ): String {
        val top = starters
            .mapNotNull { slot -> insights[slot.playerId]?.let { slot to it } }
            .maxByOrNull { it.second.projection.median }
            ?: return "Set your lineup — no strong signals this week."
        val (_, insight) = top
        return "Lean into ${insight.fullName} this week — projection + matchup are your strongest edge."
    }
}

/**
 * Pure slot-fill optimizer. Exposed at package level (not inside the class) so
 * QA can unit-test it without constructing a builder.
 *
 * Fill order follows the framework's standard roster: QB, RB, RB, WR, WR, TE,
 * FLEX, DEF, K. For each slot we pick the highest median-projection eligible
 * player we haven't already started.
 */
internal fun selectLineup(
    roster: SleeperRoster,
    insights: Map<String, PlayerInsight>,
    allPlayers: Map<String, SleeperPlayer> = emptyMap(),
): List<LineupSlot> {
    val slotOrder = listOf("QB", "RB", "RB", "WR", "WR", "TE", "FLEX", "DEF", "K")

    // Resolve position per player (fall back to SleeperPlayer if insight is missing the info).
    fun positionOf(id: String): String =
        insights[id]?.position?.uppercase()?.takeIf { it.isNotBlank() }
            ?: allPlayers[id]?.position?.uppercase()
            ?: "FLEX"

    val available = roster.players.toMutableList()
    val result = mutableListOf<LineupSlot>()

    for (slot in slotOrder) {
        val eligible = available.filter { id ->
            val pos = positionOf(id)
            when (slot) {
                "QB" -> pos == "QB"
                "RB" -> pos == "RB"
                "WR" -> pos == "WR"
                "TE" -> pos == "TE"
                "K" -> pos == "K"
                "DEF" -> pos == "DEF" || pos == "DST"
                "FLEX" -> pos in setOf("RB", "WR", "TE")
                else -> false
            }
        }
        val pick = eligible.maxByOrNull { insights[it]?.projection?.median ?: 0.0 } ?: continue
        val insight = insights[pick]
        val reason = insight?.let {
            "Projected ${it.projection.median} median; matchup ${it.matchup.grade}"
        } ?: "Starting by roster availability"
        result += LineupSlot(slot = slot, playerId = pick, reason = reason)
        available.remove(pick)
    }
    return result
}
