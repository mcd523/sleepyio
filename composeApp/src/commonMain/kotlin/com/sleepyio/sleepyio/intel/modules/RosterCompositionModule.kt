package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.LeaguemateRepository

data class RosterCompositionResult(
    val positionCounts: Map<String, Int>,
    val injuredPlayers: List<Pair<String, String>>,
    val totalPlayers: Int
)

private val positionColors = mapOf(
    "QB" to Color(0xFFEF5350),
    "RB" to Color(0xFF4CAF50),
    "WR" to Color(0xFF1ABC9C),
    "TE" to Color(0xFFFF9800),
    "K" to Color(0xFF9C27B0),
    "DEF" to Color(0xFF607D8B)
)

object RosterCompositionModule : InsightModule<RosterCompositionResult> {
    override val id = "roster_composition"
    override val displayName = "Roster Composition"

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): RosterCompositionResult? {
        if (leagueHistory.isEmpty()) return null

        // Current season only — use the first (current) league from the first chain
        val currentLeagueId = leagueHistory.keys.first()
        val repo = repository
        val roster = repo.getLeaguemateRoster(currentLeagueId, targetUserId) ?: return null

        val positionCounts = mutableMapOf<String, Int>()
        val injured = mutableListOf<Pair<String, String>>()

        for (playerId in roster.players) {
            val player = SleeperCache.getPlayer(playerId) ?: continue
            val position = player.position ?: "Unknown"
            positionCounts[position] = (positionCounts[position] ?: 0) + 1

            if (player.injuryStatus != null && player.injuryStatus != "Active" && player.injuryStatus.isNotEmpty()) {
                val name = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim()
                injured.add(name to (player.injuryStatus))
            }
        }

        if (positionCounts.isEmpty()) return null

        return RosterCompositionResult(
            positionCounts = positionCounts,
            injuredPlayers = injured,
            totalPlayers = roster.players.size
        )
    }

    @Composable
    override fun Render(data: RosterCompositionResult, modifier: Modifier) {
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
                        text = "Current season",
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Position chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xs)
                ) {
                    val sortedPositions = listOf("QB", "RB", "WR", "TE", "K", "DEF")
                    for (pos in sortedPositions) {
                        val count = data.positionCounts[pos] ?: continue
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Row(modifier = Modifier.padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xs)) {
                                Text(
                                    text = pos,
                                    fontSize = SleeperType.caption,
                                    fontWeight = FontWeight.Bold,
                                    color = positionColors[pos] ?: MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(SleeperSpacing.xs))
                                Text(
                                    text = "x$count",
                                    fontSize = SleeperType.caption,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (data.injuredPlayers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                    for ((name, status) in data.injuredPlayers) {
                        Text(
                            text = "$name - $status",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
