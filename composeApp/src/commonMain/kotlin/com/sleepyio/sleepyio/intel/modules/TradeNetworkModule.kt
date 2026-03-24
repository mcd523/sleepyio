package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.intel.OpponentRepository
import com.sleepyio.sleepyio.intel.SurveillanceInsightModule
import com.sleepyio.sleepyio.intel.model.DataTier
import com.sleepyio.sleepyio.intel.model.IntelCategory

data class TradePartner(
    val userName: String,
    val userId: String,
    val tradeCount: Int
)

data class TradeNetworkResult(
    val tradePartners: List<TradePartner>,
    val totalTrades: Int,
    val positionsTraded: Map<String, Int>,
    val positionsAcquired: Map<String, Int>
)

object TradeNetworkModule : SurveillanceInsightModule<TradeNetworkResult> {
    override val id = "trade_network"
    override val displayName = "Trade Network"
    override val category = IntelCategory.STRATEGIC
    override val requiredTier = DataTier.DEEP

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): TradeNetworkResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        val partnerCounts = mutableMapOf<String, Int>()
        val partnerNames = mutableMapOf<String, String>()
        val positionsTraded = mutableMapOf<String, Int>()
        val positionsAcquired = mutableMapOf<String, Int>()
        var totalTrades = 0

        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val rosters = try { opponentRepo.getRosters(leagueId) } catch (_: Exception) { continue }
            val users = try { opponentRepo.getLeaguemates(leagueId) } catch (_: Exception) { continue }
            val userMap = users.associateBy { it.userId }
            val rosterOwnerMap = rosters.associate { it.rosterId to it.ownerId }
            val targetRosterIds = rosters.filter { it.ownerId == targetUserId }.map { it.rosterId }.toSet()

            val transactions = try {
                opponentRepo.getLeaguemateTransactions(leagueId, targetUserId)
            } catch (_: Exception) { continue }

            val trades = transactions.filter { it.type == "trade" && it.status == "complete" }
            totalTrades += trades.size

            for (trade in trades) {
                // Find trade partners (other roster IDs in the trade)
                for (rosterId in trade.rosterIds) {
                    if (rosterId in targetRosterIds) continue
                    val partnerId = rosterOwnerMap[rosterId] ?: continue
                    partnerCounts[partnerId] = (partnerCounts[partnerId] ?: 0) + 1
                    if (partnerId !in partnerNames) {
                        val user = userMap[partnerId]
                        partnerNames[partnerId] = user?.displayName ?: user?.userName ?: partnerId
                    }
                }

                // Analyze positions traded away (drops from target's roster)
                trade.drops?.forEach { (playerId, rosterIdStr) ->
                    if (rosterIdStr.toIntOrNull() in targetRosterIds) {
                        val player = SleeperCache.getPlayer(playerId)
                        val pos = player?.position ?: "?"
                        positionsTraded[pos] = (positionsTraded[pos] ?: 0) + 1
                    }
                }

                // Analyze positions acquired (adds to target's roster)
                trade.adds?.forEach { (playerId, rosterIdStr) ->
                    if (rosterIdStr.toIntOrNull() in targetRosterIds) {
                        val player = SleeperCache.getPlayer(playerId)
                        val pos = player?.position ?: "?"
                        positionsAcquired[pos] = (positionsAcquired[pos] ?: 0) + 1
                    }
                }
            }
        }

        if (totalTrades == 0) return null

        val tradePartners = partnerCounts.entries
            .sortedByDescending { it.value }
            .map { (userId, count) ->
                TradePartner(
                    userName = partnerNames[userId] ?: userId,
                    userId = userId,
                    tradeCount = count
                )
            }

        return TradeNetworkResult(
            tradePartners = tradePartners,
            totalTrades = totalTrades,
            positionsTraded = positionsTraded,
            positionsAcquired = positionsAcquired
        )
    }

    @Composable
    override fun Render(data: TradeNetworkResult, modifier: Modifier) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(SleeperSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${data.totalTrades} trades",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Trade partners
                if (data.tradePartners.isNotEmpty()) {
                    Text(
                        text = "Preferred Partners",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))

                    data.tradePartners.take(5).forEach { partner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SleeperSpacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = partner.userName,
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${partner.tradeCount}x",
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Position chips
                if (data.positionsTraded.isNotEmpty() || data.positionsAcquired.isNotEmpty()) {
                    Text(
                        text = "Trades Away",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xxs)
                    ) {
                        data.positionsTraded.entries.sortedByDescending { it.value }.forEach { (pos, count) ->
                            Text(
                                text = "$pos x$count",
                                fontSize = SleeperType.caption,
                                color = Color.White,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(SleeperSpacing.xs))

                    Text(
                        text = "Acquires",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xxs)
                    ) {
                        data.positionsAcquired.entries.sortedByDescending { it.value }.forEach { (pos, count) ->
                            Text(
                                text = "$pos x$count",
                                fontSize = SleeperType.caption,
                                color = Color.White,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                            )
                        }
                    }
                }
            }
        }
    }
}
