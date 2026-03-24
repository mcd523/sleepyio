package com.sleepyio.sleepyio.intel.creative

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.intel.OpponentRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

private val logger = KotlinLogging.logger("ProjectionDeltaTracker")

data class ProjectionDelta(
    val playerId: String,
    val playerName: String,
    val position: String,
    val currentProjection: Float,
    val previousProjection: Float,
    val deltaPercent: Float,
    val action: String
)

/**
 * Monitors projection changes for opponent roster players week-over-week.
 * Flags significant drops that may indicate upcoming roster moves.
 */
object ProjectionDeltaTracker {

    /**
     * Analyze projection deltas for all players on an opponent's rosters.
     * Returns players with >5% projection drop, categorized by severity.
     */
    suspend fun analyze(
        opponentId: String,
        repository: OpponentRepository
    ): List<ProjectionDelta> {
        return try {
        val nflState = SleeperClient.getNflState()
        if (nflState == null) {
            logger.warn { "NFL state unavailable, skipping projection delta analysis" }
            return emptyList()
        }

        val currentWeek = nflState.displayWeek?.toInt() ?: return emptyList()
        val previousWeek = currentWeek - 1
        if (previousWeek < 1) return emptyList()

        val season = nflState.season

        // Fetch both weeks' projections
        val currentProjections = try {
            SleeperClient.getAllWeeklyProjections(season = season, week = currentWeek)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch current week projections" }
            return emptyList()
        }

        val previousProjections = try {
            SleeperClient.getAllWeeklyProjections(season = season, week = previousWeek)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch previous week projections" }
            return emptyList()
        }

        if (currentProjections.isEmpty() || previousProjections.isEmpty()) return emptyList()

        // Index projections by player ID for fast lookup
        val currentByPlayer = currentProjections.associateBy { it.playerId }
        val previousByPlayer = previousProjections.associateBy { it.playerId }

        // Collect all player IDs from opponent rosters across leagues
        val opponentPlayerIds = mutableSetOf<String>()
        val opponentLeagues = repository.getOpponentLeagues(opponentId) ?: emptyList()

        for (opponentLeague in opponentLeagues) {
            try {
                val metadata = repository.fetchShadowMetadata(opponentLeague.league.leagueId) ?: continue
                val roster = metadata.rosters.find { it.ownerId == opponentId } ?: continue
                opponentPlayerIds.addAll(roster.players)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get roster for league ${opponentLeague.league.leagueId}" }
            }
        }

        if (opponentPlayerIds.isEmpty()) return emptyList()

        // Compare projections for each opponent player
        val deltas = opponentPlayerIds.mapNotNull { playerId ->
            try {
                val current = currentByPlayer[playerId]
                val previous = previousByPlayer[playerId]
                if (current == null || previous == null) return@mapNotNull null

                val currentPts = extractPtsProjection(current.stats) ?: return@mapNotNull null
                val previousPts = extractPtsProjection(previous.stats) ?: return@mapNotNull null

                if (previousPts <= 0f) return@mapNotNull null

                val deltaPct = ((currentPts - previousPts) / previousPts) * 100f

                // Only flag drops (negative deltas)
                if (deltaPct >= -5f) return@mapNotNull null

                val player = try {
                    SleeperCache.getPlayer(playerId)
                } catch (_: Exception) {
                    null
                }
                val name = player?.let {
                    listOfNotNull(it.firstName, it.lastName).joinToString(" ")
                }?.ifBlank { playerId } ?: playerId
                val position = player?.position ?: "UNK"

                val action = when {
                    deltaPct <= -20f -> "Likely drop"
                    deltaPct <= -10f -> "Possible stream"
                    else -> "Watch"
                }

                ProjectionDelta(
                    playerId = playerId,
                    playerName = name,
                    position = position,
                    currentProjection = currentPts,
                    previousProjection = previousPts,
                    deltaPercent = deltaPct,
                    action = action
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to process projection delta for player $playerId" }
                null
            }
        }

        deltas.sortedBy { it.deltaPercent } // Most negative first
        } catch (e: Exception) {
            logger.error(e) { "ProjectionDeltaTracker.analyze failed for opponent $opponentId" }
            emptyList()
        }
    }

    /**
     * Extract projected fantasy points from a stats JsonObject.
     * Tries common keys: pts_ppr, pts_half_ppr, pts_std, fan_pts_allow.
     */
    private fun extractPtsProjection(stats: kotlinx.serialization.json.JsonObject?): Float? {
        if (stats == null) return null
        val keys = listOf("pts_ppr", "pts_half_ppr", "pts_std")
        for (key in keys) {
            val value = stats[key]?.jsonPrimitive?.floatOrNull
            if (value != null) return value
        }
        return null
    }
}
