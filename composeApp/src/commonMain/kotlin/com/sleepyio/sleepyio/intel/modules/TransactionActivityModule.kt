package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.ui.components.SeasonBreakdown

data class TransactionActivityResult(
    val addCount: Int,
    val dropCount: Int,
    val tradeCount: Int,
    val waiverCount: Int,
    val mostActiveDay: String?,
    val lastMoveTimestamp: Long?,
    val seasonBreakdown: Map<String, TransactionActivityResult> = emptyMap()
)

object TransactionActivityModule : InsightModule<TransactionActivityResult> {
    override val id = "transaction_activity"
    override val displayName = "Transaction Activity"

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): TransactionActivityResult? {
        val repo = repository
        var addCount = 0
        var dropCount = 0
        var tradeCount = 0
        var waiverCount = 0
        var lastTimestamp: Long? = null
        val dayOfWeekCounts = mutableMapOf<Int, Int>()
        val seasonResults = mutableMapOf<String, TransactionActivityResult>()

        for ((_, historyChain) in leagueHistory) {
            for (league in historyChain) {
                var seasonAdds = 0
                var seasonDrops = 0
                var seasonTrades = 0
                var seasonWaivers = 0
                var seasonLastTimestamp: Long? = null
                val seasonDayCounts = mutableMapOf<Int, Int>()

                val transactions = repo.getLeaguemateTransactions(league.leagueId, targetUserId)
                for (tx in transactions) {
                    when (tx.type) {
                        "trade" -> { tradeCount++; seasonTrades++ }
                        "waiver" -> {
                            waiverCount++; seasonWaivers++
                            val adds = tx.adds?.size ?: 0
                            val drops = tx.drops?.size ?: 0
                            addCount += adds; seasonAdds += adds
                            dropCount += drops; seasonDrops += drops
                        }
                        "free_agent" -> {
                            val adds = tx.adds?.size ?: 0
                            val drops = tx.drops?.size ?: 0
                            addCount += adds; seasonAdds += adds
                            dropCount += drops; seasonDrops += drops
                        }
                    }

                    if (lastTimestamp == null || tx.createdTime > lastTimestamp) {
                        lastTimestamp = tx.createdTime
                    }
                    if (seasonLastTimestamp == null || tx.createdTime > seasonLastTimestamp) {
                        seasonLastTimestamp = tx.createdTime
                    }

                    val dayIndex = ((tx.createdTime / 86_400_000L + 3) % 7).toInt()
                    dayOfWeekCounts[dayIndex] = (dayOfWeekCounts[dayIndex] ?: 0) + 1
                    seasonDayCounts[dayIndex] = (seasonDayCounts[dayIndex] ?: 0) + 1
                }

                if (seasonAdds > 0 || seasonDrops > 0 || seasonTrades > 0 || seasonWaivers > 0) {
                    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    val seasonActiveDay = seasonDayCounts.maxByOrNull { it.value }?.let { dayNames[it.key] }
                    seasonResults[league.season] = TransactionActivityResult(
                        addCount = seasonAdds,
                        dropCount = seasonDrops,
                        tradeCount = seasonTrades,
                        waiverCount = seasonWaivers,
                        mostActiveDay = seasonActiveDay,
                        lastMoveTimestamp = seasonLastTimestamp
                    )
                }
            }
        }

        if (addCount == 0 && dropCount == 0 && tradeCount == 0 && waiverCount == 0) return null

        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val mostActiveDay = dayOfWeekCounts.maxByOrNull { it.value }?.let { dayNames[it.key] }

        return TransactionActivityResult(
            addCount = addCount,
            dropCount = dropCount,
            tradeCount = tradeCount,
            waiverCount = waiverCount,
            mostActiveDay = mostActiveDay,
            lastMoveTimestamp = lastTimestamp,
            seasonBreakdown = seasonResults
        )
    }

    @Composable
    override fun Render(data: TransactionActivityResult, modifier: Modifier) {
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
                        text = if (data.seasonBreakdown.size > 1) "All-time" else "This season",
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    StatBox(
                        label = "Adds",
                        value = data.addCount.toString(),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = "Drops",
                        value = data.dropCount.toString(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = "Trades",
                        value = data.tradeCount.toString(),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                val details = buildList {
                    data.mostActiveDay?.let { add("Most active on ${it}s") }
                    if (data.lastMoveTimestamp != null) add("Last move: recently")
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(". "),
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SeasonBreakdown(seasonData = data.seasonBreakdown) { season, seasonData ->
                    Column {
                        Text(
                            text = season,
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                        ) {
                            Text(
                                text = "${seasonData.addCount} adds",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "${seasonData.dropCount} drops",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "${seasonData.tradeCount} trades",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SleeperSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = SleeperType.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = SleeperType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
