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

data class WaiverPatternResult(
    val dayDistribution: Map<String, Int>,
    val mostActiveDay: String,
    val mostActivePct: Float,
    val recentShadowAdds: List<String>,
    val behaviorLabel: String
)

object WaiverPatternModule : SurveillanceInsightModule<WaiverPatternResult> {
    override val id = "waiver_pattern"
    override val displayName = "Waiver Patterns"
    override val category = IntelCategory.PREDICTIVE
    override val requiredTier = DataTier.DEEP

    private val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private val behaviorLabels = mapOf(
        "Sunday" to "Sunday streamer",
        "Monday" to "Monday morning manager",
        "Tuesday" to "Tuesday tactician",
        "Wednesday" to "Wednesday warrior",
        "Thursday" to "Thursday thinker",
        "Friday" to "Friday fire-seller",
        "Saturday" to "Saturday scrambler"
    )

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): WaiverPatternResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        val dayCounts = mutableMapOf<Int, Int>()
        val recentShadowAdds = mutableListOf<Pair<Long, String>>() // timestamp to player name

        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val metadata = opponentRepo.fetchShadowMetadata(leagueId) ?: continue
            val opponentRoster = metadata.rosters.find { it.ownerId == targetUserId } ?: continue
            val rosterIds = setOf(opponentRoster.rosterId)

            val transactions = opponentRepo.getAllTransactions(leagueId)
            val opponentTx = transactions.filter { tx ->
                tx.rosterIds.any { it in rosterIds } &&
                    (tx.type == "waiver" || tx.type == "free_agent")
            }

            for (tx in opponentTx) {
                // Day of week: (epochMs / 86400000 + 4) % 7 gives 0=Sunday..6=Saturday
                val dayIndex = ((tx.createdTime / 86_400_000L + 4) % 7).toInt()
                dayCounts[dayIndex] = (dayCounts[dayIndex] ?: 0) + 1

                // Track shadow league adds for early mover signals
                if (!opponentLeague.isShared && tx.adds != null) {
                    for (playerId in tx.adds.keys) {
                        val player = SleeperCache.getPlayer(playerId)
                        val name = if (player != null) {
                            "${player.firstName ?: ""} ${player.lastName ?: ""}".trim()
                        } else {
                            playerId
                        }
                        recentShadowAdds.add(tx.createdTime to name)
                    }
                }
            }
        }

        if (dayCounts.isEmpty()) return null

        val totalTx = dayCounts.values.sum()
        val mostActiveDayIndex = dayCounts.maxByOrNull { it.value }?.key ?: 0
        val mostActiveCount = dayCounts[mostActiveDayIndex] ?: 0
        val mostActiveDay = dayNames[mostActiveDayIndex]
        val mostActivePct = if (totalTx > 0) mostActiveCount.toFloat() / totalTx else 0f

        val dayDistribution = dayNames.associateWith { name ->
            val idx = dayNames.indexOf(name)
            dayCounts[idx] ?: 0
        }

        val recentAdds = recentShadowAdds
            .sortedByDescending { it.first }
            .take(5)
            .map { it.second }

        val behaviorLabel = behaviorLabels[mostActiveDay] ?: "Unknown pattern"

        return WaiverPatternResult(
            dayDistribution = dayDistribution,
            mostActiveDay = mostActiveDay,
            mostActivePct = mostActivePct,
            recentShadowAdds = recentAdds,
            behaviorLabel = behaviorLabel
        )
    }

    @Composable
    override fun Render(data: WaiverPatternResult, modifier: Modifier) {
        val tealColor = Color(0xFF009688)
        val highlightColor = MaterialTheme.colorScheme.primary

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

                // Mini bar chart for day distribution
                val maxCount = data.dayDistribution.values.maxOrNull() ?: 1
                val dayAbbrevs = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

                val maxBarHeight = 40.dp
                val dayOrder = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xxs),
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayOrder.forEachIndexed { index, dayName ->
                        val count = data.dayDistribution[dayName] ?: 0
                        val barFraction = if (maxCount > 0) (count.toFloat() / maxCount) else 0f
                        val isHighlighted = dayName == data.mostActiveDay
                        val barColor = if (isHighlighted) highlightColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        val barH = maxBarHeight * barFraction.coerceAtLeast(0.05f)

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barH)
                                    .background(barColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            )
                            Text(
                                text = dayAbbrevs[index],
                                fontSize = SleeperType.caption,
                                color = if (isHighlighted) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Behavior label
                val pct = (data.mostActivePct * 100).roundToInt()
                Text(
                    text = "${data.behaviorLabel} ($pct% of moves on ${data.mostActiveDay}s)",
                    fontSize = SleeperType.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )

                // Recent shadow league adds
                if (data.recentShadowAdds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                    Text(
                        text = "Early Mover Signals",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = tealColor
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                    data.recentShadowAdds.forEach { playerName ->
                        Text(
                            text = playerName,
                            fontSize = SleeperType.caption,
                            color = tealColor,
                            modifier = Modifier.padding(vertical = SleeperSpacing.xxs)
                        )
                    }
                }
            }
        }
    }
}
