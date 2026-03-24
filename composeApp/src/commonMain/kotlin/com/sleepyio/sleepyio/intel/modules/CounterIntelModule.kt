package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

data class CounterIntelResult(
    val pickedUpDrops: List<String>,
    val suspiciousDraftTargets: List<String>,
    val riskLevel: String
)

object CounterIntelModule : SurveillanceInsightModule<CounterIntelResult> {
    override val id = "counter_intel"
    override val displayName = "Counter-Intelligence"
    override val category = IntelCategory.COMPETITIVE
    override val requiredTier = DataTier.DEEP

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): CounterIntelResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        val sharedLeagues = opponentLeagues.filter { it.isShared }

        // Step 1: Find my recent drops in shared leagues
        val myDroppedPlayerIds = mutableSetOf<String>()
        for (sharedLeague in sharedLeagues) {
            val leagueId = sharedLeague.league.leagueId
            val myTransactions = try {
                opponentRepo.getLeaguemateTransactions(leagueId, myUserId)
            } catch (_: Exception) { continue }

            myTransactions.forEach { tx ->
                tx.drops?.forEach { (playerId, _) ->
                    myDroppedPlayerIds.add(playerId)
                }
            }
        }

        // Step 2: Find opponent's adds across ALL leagues that match my drops
        val pickedUpDropIds = mutableSetOf<String>()
        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val opponentTransactions = try {
                opponentRepo.getLeaguemateTransactions(leagueId, targetUserId)
            } catch (_: Exception) { continue }

            opponentTransactions.forEach { tx ->
                tx.adds?.forEach { (playerId, _) ->
                    if (playerId in myDroppedPlayerIds) {
                        pickedUpDropIds.add(playerId)
                    }
                }
            }
        }

        val pickedUpDrops = pickedUpDropIds.mapNotNull { playerId ->
            val player = SleeperCache.getPlayer(playerId)
            if (player != null) {
                "${player.firstName ?: ""} ${player.lastName ?: ""}".trim()
            } else {
                playerId
            }
        }

        // Step 3: Analyze draft overlap in shared leagues
        val suspiciousDraftTargets = mutableSetOf<String>()
        for (sharedLeague in sharedLeagues) {
            val leagueId = sharedLeague.league.leagueId
            val myPicks = try {
                opponentRepo.getLeaguemateDraftPicks(leagueId, myUserId)
            } catch (_: Exception) { continue }
            val opponentPicks = try {
                opponentRepo.getLeaguemateDraftPicks(leagueId, targetUserId)
            } catch (_: Exception) { continue }

            // Check for counter-drafting: opponent picks same position within 3 picks of user
            for (myPick in myPicks) {
                val myPlayer = SleeperCache.getPlayer(myPick.playerId)
                val myPosition = myPlayer?.position ?: continue

                for (oppPick in opponentPicks) {
                    val oppPlayer = SleeperCache.getPlayer(oppPick.playerId)
                    val oppPosition = oppPlayer?.position ?: continue

                    // Same position, picked within 3 picks of each other, opponent picked after
                    val pickDelta = oppPick.pickNumber - myPick.pickNumber
                    if (oppPosition == myPosition && pickDelta in 1..3) {
                        val name = "${oppPlayer.firstName ?: ""} ${oppPlayer.lastName ?: ""}".trim()
                        if (name.isNotBlank()) {
                            suspiciousDraftTargets.add("$name ($oppPosition, pick ${oppPick.pickNumber})")
                        }
                    }
                }
            }
        }

        val riskLevel = when {
            pickedUpDrops.size >= 3 || suspiciousDraftTargets.size >= 3 -> "Likely watching you"
            pickedUpDrops.isNotEmpty() || suspiciousDraftTargets.isNotEmpty() -> "Possible awareness"
            else -> "No signals detected"
        }

        // Only return if there's something to show
        if (pickedUpDrops.isEmpty() && suspiciousDraftTargets.isEmpty()) return null

        return CounterIntelResult(
            pickedUpDrops = pickedUpDrops,
            suspiciousDraftTargets = suspiciousDraftTargets.toList(),
            riskLevel = riskLevel
        )
    }

    @Composable
    override fun Render(data: CounterIntelResult, modifier: Modifier) {
        val riskColor = when (data.riskLevel) {
            "Likely watching you" -> MaterialTheme.colorScheme.error
            "Possible awareness" -> Color(0xFFFF9800)
            else -> MaterialTheme.colorScheme.tertiary
        }

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(SleeperSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayName,
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = data.riskLevel,
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(riskColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                if (data.pickedUpDrops.isNotEmpty()) {
                    Text(
                        text = "Picked Up Your Drops",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                    data.pickedUpDrops.forEach { playerName ->
                        Text(
                            text = "• $playerName",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = SleeperSpacing.xxs)
                        )
                    }
                }

                if (data.suspiciousDraftTargets.isNotEmpty()) {
                    if (data.pickedUpDrops.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                    }
                    Text(
                        text = "Suspicious Draft Targets",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                    data.suspiciousDraftTargets.forEach { target ->
                        Text(
                            text = "• $target",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = SleeperSpacing.xxs)
                        )
                    }
                }
            }
        }
    }
}
