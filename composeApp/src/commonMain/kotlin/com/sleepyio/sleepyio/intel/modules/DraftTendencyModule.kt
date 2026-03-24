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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.ui.components.SeasonBreakdown

data class DraftTendencyResult(
    val positionDistribution: Map<String, Float>,
    val earlyRoundBias: String,
    val repeatedTargets: List<String>,
    val totalPicks: Int,
    val seasonBreakdown: Map<String, DraftTendencyResult> = emptyMap()
)

private val positionColors = mapOf(
    "QB" to Color(0xFFEF5350),
    "RB" to Color(0xFF4CAF50),
    "WR" to Color(0xFF1ABC9C),
    "TE" to Color(0xFFFF9800),
    "K" to Color(0xFF9C27B0),
    "DEF" to Color(0xFF607D8B)
)

object DraftTendencyModule : InsightModule<DraftTendencyResult> {
    override val id = "draft_tendency"
    override val displayName = "Draft Tendencies"

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): DraftTendencyResult? {
        val repo = repository
        val positionCounts = mutableMapOf<String, Int>()
        val earlyRoundPositions = mutableMapOf<String, Int>()
        val playerPickCounts = mutableMapOf<String, Int>()
        var totalPicks = 0
        val seasonResults = mutableMapOf<String, DraftTendencyResult>()

        for ((_, historyChain) in leagueHistory) {
            for (league in historyChain) {
                val seasonPositionCounts = mutableMapOf<String, Int>()
                val seasonEarlyRoundPositions = mutableMapOf<String, Int>()
                val seasonPlayerPickCounts = mutableMapOf<String, Int>()
                var seasonTotalPicks = 0

                val picks = repo.getLeaguemateDraftPicks(league.leagueId, targetUserId)
                for (pick in picks) {
                    totalPicks++
                    seasonTotalPicks++
                    val player = SleeperCache.getPlayer(pick.playerId)
                    val position = player?.position ?: "Unknown"

                    positionCounts[position] = (positionCounts[position] ?: 0) + 1
                    seasonPositionCounts[position] = (seasonPositionCounts[position] ?: 0) + 1

                    if (pick.round <= 3) {
                        earlyRoundPositions[position] = (earlyRoundPositions[position] ?: 0) + 1
                        seasonEarlyRoundPositions[position] = (seasonEarlyRoundPositions[position] ?: 0) + 1
                    }

                    val name = "${player?.firstName ?: ""} ${player?.lastName ?: ""}".trim()
                    if (name.isNotEmpty()) {
                        playerPickCounts[name] = (playerPickCounts[name] ?: 0) + 1
                        seasonPlayerPickCounts[name] = (seasonPlayerPickCounts[name] ?: 0) + 1
                    }
                }

                if (seasonTotalPicks > 0) {
                    val seasonDistribution = seasonPositionCounts.mapValues { it.value.toFloat() / seasonTotalPicks }
                    val seasonEarlyBias = seasonEarlyRoundPositions.maxByOrNull { it.value }?.let { (pos, count) ->
                        "$pos-heavy (${count} of first 3 rounds)"
                    } ?: "Balanced"
                    val seasonRepeated = seasonPlayerPickCounts.filter { it.value > 1 }
                        .entries.sortedByDescending { it.value }
                        .take(3)
                        .map { "${it.key} (${it.value}x)" }

                    seasonResults[league.season] = DraftTendencyResult(
                        positionDistribution = seasonDistribution,
                        earlyRoundBias = seasonEarlyBias,
                        repeatedTargets = seasonRepeated,
                        totalPicks = seasonTotalPicks
                    )
                }
            }
        }

        if (totalPicks == 0) return null

        val distribution = positionCounts.mapValues { it.value.toFloat() / totalPicks }

        val earlyBias = earlyRoundPositions.maxByOrNull { it.value }?.let { (pos, count) ->
            "$pos-heavy (${count} of first 3 rounds)"
        } ?: "Balanced"

        val repeated = playerPickCounts.filter { it.value > 1 }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { "${it.key} (${it.value}x)" }

        return DraftTendencyResult(
            positionDistribution = distribution,
            earlyRoundBias = earlyBias,
            repeatedTargets = repeated,
            totalPicks = totalPicks,
            seasonBreakdown = seasonResults
        )
    }

    @Composable
    override fun Render(data: DraftTendencyResult, modifier: Modifier) {
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
                    if (data.seasonBreakdown.size > 1) {
                        Text(
                            text = "All-time (${data.totalPicks} picks)",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                PositionBar(data.positionDistribution)

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                Text(
                    text = data.earlyRoundBias,
                    fontSize = SleeperType.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )

                if (data.repeatedTargets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.xs))
                    Text(
                        text = "Repeat targets: ${data.repeatedTargets.joinToString(", ")}",
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SeasonBreakdown(seasonData = data.seasonBreakdown) { season, seasonData ->
                    Column {
                        Text(
                            text = "$season (${seasonData.totalPicks} picks)",
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                        PositionBar(seasonData.positionDistribution, height = 16)
                        Text(
                            text = seasonData.earlyRoundBias,
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionBar(distribution: Map<String, Float>, height: Int = 24) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        val sortedPositions = listOf("QB", "RB", "WR", "TE", "K", "DEF")
        for (pos in sortedPositions) {
            val fraction = distribution[pos] ?: 0f
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .background(positionColors[pos] ?: Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    if (fraction > 0.08f) {
                        Text(
                            text = pos,
                            fontSize = SleeperType.caption,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
