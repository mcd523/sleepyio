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
import kotlin.math.roundToInt

data class ConvergentPlayer(
    val playerId: String,
    val playerName: String,
    val position: String?,
    val leagueCount: Int,
    val totalLeagues: Int
)

data class ConvergentInterestResult(
    val players: List<ConvergentPlayer>
)

object ConvergentInterestModule : SurveillanceInsightModule<ConvergentInterestResult> {
    override val id = "convergent_interest"
    override val displayName = "Convergent Interests"
    override val category = IntelCategory.COMPETITIVE
    override val requiredTier = DataTier.METADATA

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): ConvergentInterestResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val convergentMap = opponentRepo.getConvergentPlayers(targetUserId)
        if (convergentMap.isEmpty()) return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId)
        val totalLeagues = opponentLeagues?.size ?: 1

        val players = convergentMap.entries
            .sortedByDescending { it.value }
            .mapNotNull { (playerId, count) ->
                val player = SleeperCache.getPlayer(playerId)
                val name = if (player != null) {
                    "${player.firstName ?: ""} ${player.lastName ?: ""}".trim()
                } else {
                    playerId
                }
                ConvergentPlayer(
                    playerId = playerId,
                    playerName = name,
                    position = player?.position,
                    leagueCount = count,
                    totalLeagues = totalLeagues
                )
            }

        if (players.isEmpty()) return null
        return ConvergentInterestResult(players = players)
    }

    @Composable
    override fun Render(data: ConvergentInterestResult, modifier: Modifier) {
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

                data.players.forEach { player ->
                    val pct = if (player.totalLeagues > 0) {
                        (player.leagueCount.toFloat() / player.totalLeagues * 100).roundToInt()
                    } else 0

                    val badgeColor = when {
                        pct > 50 -> MaterialTheme.colorScheme.error
                        pct > 30 -> Color(0xFFFF9800) // orange
                        else -> MaterialTheme.colorScheme.tertiary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SleeperSpacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = player.playerName,
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${player.position ?: "?"} - Owned in ${player.leagueCount}/${player.totalLeagues} leagues",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$pct%",
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(badgeColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = SleeperSpacing.xs, vertical = SleeperSpacing.xxs)
                        )
                    }
                }
            }
        }
    }
}
