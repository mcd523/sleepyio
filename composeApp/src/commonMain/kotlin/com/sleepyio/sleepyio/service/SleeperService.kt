package com.sleepyio.sleepyio.service

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.odds.GameOdds
import com.sleepyio.sleepyio.client.model.stats.PlayerStats
import com.sleepyio.sleepyio.model.UnifiedPlayer
import com.sleepyio.sleepyio.model.UnifiedTeam

object SleeperService {

    // Start/Sit Analysis: find suboptimal lineup decisions
    data class StartSitRecommendation(
        val benchPlayer: UnifiedPlayer,
        val starterToReplace: UnifiedPlayer,
        val pointsDelta: Double
    )

    fun analyzeStartSit(team: UnifiedTeam): List<StartSitRecommendation> {
        val recommendations = mutableListOf<StartSitRecommendation>()
        val starters = team.starters
        val bench = team.bench

        for (benchPlayer in bench) {
            val projBench = benchPlayer.projectedPoints ?: continue
            if (projBench <= 0) continue

            // Find starters at the same position with lower projections
            val weakerStarters = starters.filter { starter ->
                starter.position == benchPlayer.position &&
                    (starter.projectedPoints ?: 0.0) < projBench
            }

            for (starter in weakerStarters) {
                val projStarter = starter.projectedPoints ?: 0.0
                val delta = projBench - projStarter
                if (delta > 1.0) { // Only flag if >1 point difference
                    recommendations.add(
                        StartSitRecommendation(benchPlayer, starter, delta)
                    )
                }
            }
        }

        return recommendations.sortedByDescending { it.pointsDelta }
    }

    // Optimal lineup: rearrange players by position for maximum projected points
    fun calculateOptimalLineup(team: UnifiedTeam, starterSlots: Map<String, Int>): List<UnifiedPlayer> {
        val allPlayers = team.roster.sortedByDescending { it.projectedPoints ?: 0.0 }
        val optimal = mutableListOf<UnifiedPlayer>()
        val used = mutableSetOf<String>()

        // Fill each position slot with the best available player
        for ((position, count) in starterSlots) {
            val eligible = allPlayers.filter { player ->
                player.id !in used && (player.position == position || (position == "FLEX" && player.position in listOf("RB", "WR", "TE")))
            }.take(count)

            eligible.forEach { player ->
                optimal.add(player.copy(isStarter = true))
                used.add(player.id)
            }
        }

        return optimal
    }

    // Matchup win probability estimate (simple model based on projection sums)
    data class MatchupProbability(
        val team1WinPct: Double,
        val team2WinPct: Double,
        val projectedMargin: Double // positive = team1 advantage
    )

    fun estimateWinProbability(
        team1Starters: List<UnifiedPlayer>,
        team2Starters: List<UnifiedPlayer>
    ): MatchupProbability {
        val team1Projected = team1Starters.sumOf { it.projectedPoints ?: 0.0 }
        val team2Projected = team2Starters.sumOf { it.projectedPoints ?: 0.0 }
        val margin = team1Projected - team2Projected

        // Simple sigmoid-based win probability
        // ~10 point projected lead ≈ 70% win chance
        val winPct = 1.0 / (1.0 + kotlin.math.exp(-margin / 15.0))

        return MatchupProbability(
            team1WinPct = winPct,
            team2WinPct = 1.0 - winPct,
            projectedMargin = margin
        )
    }

    // Waiver wire analysis: find trending players not on any roster
    data class WaiverTarget(
        val playerId: String,
        val playerName: String,
        val position: String,
        val team: String?,
        val trendCount: Int,
        val projectedPoints: Double?,
        val injuryStatus: String?
    )

    suspend fun findWaiverTargets(
        leagueId: Long,
        season: String,
        week: Int,
        limit: Int = 25
    ): List<WaiverTarget> {
        // Get all rostered players in the league
        val rosters = SleeperClient.getRostersInLeague(leagueId)
        val rosteredPlayerIds = rosters.flatMap { it.players }.toSet()

        // Get trending players (adds)
        val trending = SleeperClient.getTrendingPlayers("nfl", "add", limit = limit * 2)

        // Get projections for the week
        val projections = SleeperClient.getWeeklyProjections(season, week)

        // Filter to unrostered trending players
        return trending
            .filter { it.playerId !in rosteredPlayerIds }
            .take(limit)
            .mapNotNull { trend ->
                val player = SleeperCache.getPlayer(trend.playerId) ?: return@mapNotNull null
                val projection = projections[trend.playerId]

                WaiverTarget(
                    playerId = trend.playerId,
                    playerName = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim(),
                    position = player.position ?: "N/A",
                    team = player.team,
                    trendCount = trend.count.toInt(),
                    projectedPoints = projection?.fantasyPoints,
                    injuryStatus = player.injuryStatus
                )
            }
    }

    // Trade analysis: evaluate a trade between two sets of players
    data class TradeAnalysis(
        val side1Players: List<UnifiedPlayer>,
        val side2Players: List<UnifiedPlayer>,
        val side1ProjectedTotal: Double,
        val side2ProjectedTotal: Double,
        val valueDifference: Double,
        val verdict: String
    )

    fun analyzeTrade(
        side1: List<UnifiedPlayer>,
        side2: List<UnifiedPlayer>
    ): TradeAnalysis {
        val side1Total = side1.sumOf { it.projectedPoints ?: 0.0 }
        val side2Total = side2.sumOf { it.projectedPoints ?: 0.0 }
        val diff = side1Total - side2Total

        val verdict = when {
            kotlin.math.abs(diff) < 5.0 -> "Fair trade"
            diff > 20.0 -> "Heavily favors Side 1"
            diff > 5.0 -> "Slightly favors Side 1"
            diff < -20.0 -> "Heavily favors Side 2"
            diff < -5.0 -> "Slightly favors Side 2"
            else -> "Fair trade"
        }

        return TradeAnalysis(
            side1Players = side1,
            side2Players = side2,
            side1ProjectedTotal = side1Total,
            side2ProjectedTotal = side2Total,
            valueDifference = diff,
            verdict = verdict
        )
    }

    // Vegas overlay: enrich players with game environment data
    data class VegasContext(
        val impliedTeamTotal: Double?,
        val gameTotal: Double?,
        val spread: Double?,
        val isSmashSpot: Boolean, // high total + player on favored team
        val isGarbageTimeCandidate: Boolean // large spread + player on trailing team
    )

    fun getVegasContext(
        teamAbbrev: String?,
        gameOdds: List<GameOdds>
    ): VegasContext {
        if (teamAbbrev == null) return VegasContext(null, null, null, false, false)

        val game = gameOdds.firstOrNull { odds ->
            odds.homeTeamAbbrev == teamAbbrev || odds.awayTeamAbbrev == teamAbbrev
        } ?: return VegasContext(null, null, null, false, false)

        val impliedTotal = game.impliedTotalForTeam(teamAbbrev)
        val isHome = game.homeTeamAbbrev == teamAbbrev
        val teamSpread = if (isHome) game.spread else -game.spread

        return VegasContext(
            impliedTeamTotal = impliedTotal,
            gameTotal = game.total,
            spread = teamSpread,
            isSmashSpot = (impliedTotal ?: 0.0) >= 27.0 && teamSpread <= -3.0,
            isGarbageTimeCandidate = game.total >= 45.0 && teamSpread >= 7.0
        )
    }

    // Schedule strength: analyze remaining schedule difficulty
    data class ScheduleStrength(
        val team: String,
        val remainingOpponents: List<OpponentStrength>,
        val averageDifficulty: Double // lower = easier
    )

    data class OpponentStrength(
        val week: Int,
        val opponentName: String,
        val pointsAllowed: Double // average points allowed by position
    )
}
