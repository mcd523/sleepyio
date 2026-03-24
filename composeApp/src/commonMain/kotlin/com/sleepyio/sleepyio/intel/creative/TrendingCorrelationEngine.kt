package com.sleepyio.sleepyio.intel.creative

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.intel.OpponentRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("TrendingCorrelationEngine")

data class TrendingCorrelation(
    val playerId: String,
    val playerName: String,
    val position: String,
    val trendCount: Int,
    val opponentNeedsPosition: Boolean,
    val prediction: String
)

/**
 * Cross-references trending waiver adds with opponent roster holes to predict
 * which players an opponent is likely to target.
 */
object TrendingCorrelationEngine {

    /**
     * Analyze trending players against an opponent's roster weaknesses.
     * Returns correlations sorted by likelihood (trend count x position-need match).
     */
    suspend fun analyze(
        opponentId: String,
        repository: OpponentRepository
    ): List<TrendingCorrelation> {
        return try {
        val trendingPlayers = SleeperClient.getTrendingPlayers(
            type = "add",
            lookbackHours = 24,
            limit = 25
        )

        if (trendingPlayers.isEmpty()) return emptyList()

        // Collect position weaknesses across all opponent leagues
        val positionWeaknesses = mutableSetOf<String>()
        val opponentLeagues = repository.getOpponentLeagues(opponentId) ?: emptyList()

        for (opponentLeague in opponentLeagues) {
            try {
                val metadata = repository.fetchShadowMetadata(opponentLeague.league.leagueId) ?: continue
                val opponentRoster = metadata.rosters.find { it.ownerId == opponentId } ?: continue

                // Count players per position on the roster
                val positionCounts = mutableMapOf<String, Int>()
                val hasInjuredStarter = mutableSetOf<String>()

                for (playerId in opponentRoster.players) {
                    val player = try {
                        SleeperCache.getPlayer(playerId)
                    } catch (_: Exception) {
                        null
                    }
                    val pos = player?.position ?: continue
                    positionCounts[pos] = (positionCounts[pos] ?: 0) + 1

                    // Check if a starter is injured
                    if (playerId in opponentRoster.starters) {
                        val injuryStatus = player.injuryStatus
                        if (injuryStatus != null && injuryStatus != "Active" && injuryStatus.isNotBlank()) {
                            hasInjuredStarter.add(pos)
                        }
                    }
                }

                // Positions with only 1 player or with injured starters are weaknesses
                for ((pos, count) in positionCounts) {
                    if (count <= 1 || pos in hasInjuredStarter) {
                        positionWeaknesses.add(pos)
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to analyze roster for league ${opponentLeague.league.leagueId}" }
            }
        }

        // Cross-reference trending players with position weaknesses
        val correlations = trendingPlayers.mapNotNull { trending ->
            try {
                val player = SleeperCache.getPlayer(trending.playerId) ?: return@mapNotNull null
                val name = listOfNotNull(player.firstName, player.lastName).joinToString(" ")
                    .ifBlank { trending.playerId }
                val position = player.position ?: return@mapNotNull null
                val needsPosition = position in positionWeaknesses

                val prediction = when {
                    needsPosition && trending.count > 10 -> "High likelihood — trending heavily at a position of need"
                    needsPosition -> "Moderate likelihood — fills a roster hole"
                    trending.count > 15 -> "Possible — highly trending but no clear positional need"
                    else -> "Low likelihood — monitoring"
                }

                TrendingCorrelation(
                    playerId = trending.playerId,
                    playerName = name,
                    position = position,
                    trendCount = trending.count.toInt(),
                    opponentNeedsPosition = needsPosition,
                    prediction = prediction
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to process trending player ${trending.playerId}" }
                null
            }
        }

        // Sort: position-need matches first, then by trend count descending
        correlations.sortedWith(
            compareByDescending<TrendingCorrelation> { it.opponentNeedsPosition }
                .thenByDescending { it.trendCount }
        )
    } catch (e: Exception) {
        logger.error(e) { "TrendingCorrelationEngine.analyze failed for opponent $opponentId" }
        emptyList()
        }
    }
}
