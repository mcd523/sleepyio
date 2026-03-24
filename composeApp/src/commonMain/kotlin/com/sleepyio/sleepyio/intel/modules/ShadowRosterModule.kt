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

data class ShadowPlayer(
    val playerId: String,
    val name: String,
    val position: String,
    val leagueCount: Int,
    val totalLeagues: Int
)

data class ShadowRosterResult(
    val playersByPosition: Map<String, List<ShadowPlayer>>
)

object ShadowRosterModule : SurveillanceInsightModule<ShadowRosterResult> {
    override val id = "shadow_roster"
    override val displayName = "Shadow Roster"
    override val category = IntelCategory.STRATEGIC
    override val requiredTier = DataTier.METADATA

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): ShadowRosterResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        val totalLeagues = opponentLeagues.size
        val playerCounts = mutableMapOf<String, Int>()

        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val metadata = opponentRepo.fetchShadowMetadata(leagueId) ?: continue
            val opponentRoster = metadata.rosters.find { it.ownerId == targetUserId } ?: continue
            for (playerId in opponentRoster.players) {
                playerCounts[playerId] = (playerCounts[playerId] ?: 0) + 1
            }
        }

        if (playerCounts.isEmpty()) return null

        val shadowPlayers = playerCounts.entries.mapNotNull { (playerId, count) ->
            val player = SleeperCache.getPlayer(playerId)
            val name = if (player != null) {
                "${player.firstName ?: ""} ${player.lastName ?: ""}".trim()
            } else {
                playerId
            }
            val position = player?.position ?: "?"
            ShadowPlayer(
                playerId = playerId,
                name = name,
                position = position,
                leagueCount = count,
                totalLeagues = totalLeagues
            )
        }

        val byPosition = shadowPlayers
            .sortedByDescending { it.leagueCount }
            .groupBy { it.position }

        return ShadowRosterResult(playersByPosition = byPosition)
    }

    @Composable
    override fun Render(data: ShadowRosterResult, modifier: Modifier) {
        val purpleBg = Color(0xFF7C3AED)

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(SleeperSpacing.md)) {
                Text(
                    text = displayName,
                    fontSize = SleeperType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                val positionOrder = listOf("QB", "RB", "WR", "TE", "K", "DEF")
                val sortedPositions = data.playersByPosition.keys
                    .sortedBy { pos -> positionOrder.indexOf(pos).let { if (it == -1) 99 else it } }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xs)
                ) {
                    for (position in sortedPositions) {
                        val players = data.playersByPosition[position] ?: continue
                        val topPlayer = players.first()
                        val chipText = "$position: ${topPlayer.name} x${topPlayer.leagueCount}"

                        Text(
                            text = chipText,
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier
                                .background(purpleBg, RoundedCornerShape(16.dp))
                                .padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Behavioral insight
                val topPosition = sortedPositions.maxByOrNull { pos ->
                    data.playersByPosition[pos]?.sumOf { it.leagueCount } ?: 0
                }
                val insight = when (topPosition) {
                    "QB" -> "Heavy investment in QB — values elite quarterback play"
                    "RB" -> "RB-heavy drafter — likely prioritizes running backs early"
                    "WR" -> "WR-focused roster construction — zero-RB tendencies"
                    "TE" -> "Premium TE investor — willing to pay up at tight end"
                    else -> "Balanced roster approach across positions"
                }

                Text(
                    text = "Pattern: $insight",
                    fontSize = SleeperType.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
