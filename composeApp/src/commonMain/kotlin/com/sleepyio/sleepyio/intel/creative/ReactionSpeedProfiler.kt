package com.sleepyio.sleepyio.intel.creative

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.intel.OpponentRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("ReactionSpeedProfiler")

data class ReactionProfile(
    val avgReactionHours: Float?,
    val fastestReactionHours: Float?,
    val reactionCategory: String,
    val examples: List<ReactionExample>
)

data class ReactionExample(
    val playerName: String,
    val newsTimestamp: Long,
    val transactionTimestamp: Long,
    val reactionHours: Float
)

/**
 * Profiles how quickly an opponent reacts to player news by correlating
 * transaction timestamps against news publication times.
 */
object ReactionSpeedProfiler {

    /**
     * Analyze an opponent's reaction speed to player news.
     * Best-effort: returns a default "Unknown" profile if data is insufficient.
     */
    suspend fun analyze(
        opponentId: String,
        repository: OpponentRepository
    ): ReactionProfile {
        return try {
        val opponentLeagues = repository.getOpponentLeagues(opponentId) ?: emptyList()
        val examples = mutableListOf<ReactionExample>()

        for (opponentLeague in opponentLeagues) {
            try {
                val leagueId = opponentLeague.league.leagueId

                // Get opponent's transactions (adds only)
                val transactions = repository.getLeaguemateTransactions(leagueId, opponentId)
                val addTransactions = transactions.filter { tx ->
                    tx.type == "free_agent" || tx.type == "waiver"
                }.filter { it.adds != null && it.adds.isNotEmpty() }

                // Limit to recent transactions to avoid excessive API calls
                val recentAdds = addTransactions
                    .sortedByDescending { it.createdTime }
                    .take(10)

                for (tx in recentAdds) {
                    val addedPlayerIds = tx.adds?.keys ?: continue
                    for (playerId in addedPlayerIds) {
                        try {
                            val news = SleeperClient.getPlayerNews(playerId, limit = 5)
                            if (news.isEmpty()) continue

                            // Find the nearest news article published BEFORE the transaction
                            val txTime = tx.createdTime
                            val nearestNews = news
                                .mapNotNull { n ->
                                    val published = n.published?.toLongOrNull() ?: return@mapNotNull null
                                    if (published < txTime) published else null
                                }
                                .maxOrNull() ?: continue

                            val reactionMs = txTime - nearestNews
                            val reactionHours = reactionMs / (1000f * 60f * 60f)

                            // Sanity check: ignore reactions that seem unreasonable (> 7 days)
                            if (reactionHours > 168f || reactionHours < 0f) continue

                            val player = try {
                                SleeperCache.getPlayer(playerId)
                            } catch (_: Exception) {
                                null
                            }
                            val name = player?.let {
                                listOfNotNull(it.firstName, it.lastName).joinToString(" ")
                            }?.ifBlank { playerId } ?: playerId

                            examples.add(
                                ReactionExample(
                                    playerName = name,
                                    newsTimestamp = nearestNews,
                                    transactionTimestamp = txTime,
                                    reactionHours = reactionHours
                                )
                            )
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to fetch news for player $playerId" }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to analyze transactions for league ${opponentLeague.league.leagueId}" }
            }
        }

        if (examples.isEmpty()) {
            return ReactionProfile(
                avgReactionHours = null,
                fastestReactionHours = null,
                reactionCategory = "Unknown",
                examples = emptyList()
            )
        }

        val avgHours = examples.map { it.reactionHours }.average().toFloat()
        val fastestHours = examples.minOf { it.reactionHours }
        val category = categorize(avgHours)

        ReactionProfile(
            avgReactionHours = avgHours,
            fastestReactionHours = fastestHours,
            reactionCategory = category,
            examples = examples.sortedBy { it.reactionHours }.take(5)
        )
        } catch (e: Exception) {
            logger.error(e) { "ReactionSpeedProfiler.analyze failed for opponent $opponentId" }
            ReactionProfile(
                avgReactionHours = null,
                fastestReactionHours = null,
                reactionCategory = "Unknown",
                examples = emptyList()
            )
        }
    }

    private fun categorize(avgHours: Float): String = when {
        avgHours < 6f -> "Lightning fast"
        avgHours < 24f -> "Active"
        avgHours < 48f -> "Moderate"
        else -> "Passive"
    }
}
