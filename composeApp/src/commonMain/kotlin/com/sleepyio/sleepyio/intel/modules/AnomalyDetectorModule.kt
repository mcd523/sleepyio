package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.intel.OpponentRepository
import com.sleepyio.sleepyio.intel.SurveillanceInsightModule
import com.sleepyio.sleepyio.intel.model.DataTier
import com.sleepyio.sleepyio.intel.model.IntelCategory

data class Anomaly(
    val type: String,
    val description: String,
    val severity: String // "high", "medium", "low"
)

data class AnomalyResult(
    val anomalies: List<Anomaly>
)

object AnomalyDetectorModule : SurveillanceInsightModule<AnomalyResult> {
    override val id = "anomaly_detector"
    override val displayName = "Anomaly Detection"
    override val category = IntelCategory.COMPETITIVE
    override val requiredTier = DataTier.DEEP

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): AnomalyResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        // Collect all transactions across all leagues
        data class TimedTransaction(val createdTime: Long, val leagueId: Long, val week: Int, val type: String)

        val allTransactions = mutableListOf<TimedTransaction>()

        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val transactions = try {
                opponentRepo.getLeaguemateTransactions(leagueId, targetUserId)
            } catch (_: Exception) { continue }

            transactions.forEach { tx ->
                allTransactions.add(
                    TimedTransaction(
                        createdTime = tx.createdTime,
                        leagueId = leagueId,
                        week = tx.week,
                        type = tx.type
                    )
                )
            }
        }

        if (allTransactions.isEmpty()) return null

        val anomalies = mutableListOf<Anomaly>()

        // Detect bursts: 4+ transactions within a 2-hour window across different leagues
        val sortedByTime = allTransactions.sortedBy { it.createdTime }
        val twoHoursMs = 2 * 60 * 60 * 1000L
        for (i in sortedByTime.indices) {
            val windowEnd = sortedByTime[i].createdTime + twoHoursMs
            val windowTransactions = sortedByTime.drop(i).takeWhile { it.createdTime <= windowEnd }
            val distinctLeagues = windowTransactions.map { it.leagueId }.toSet()
            if (windowTransactions.size >= 4 && distinctLeagues.size >= 2) {
                anomalies.add(
                    Anomaly(
                        type = "Burst",
                        description = "${windowTransactions.size} moves across ${distinctLeagues.size} leagues in a 2-hour window",
                        severity = if (windowTransactions.size >= 6) "high" else "medium"
                    )
                )
                break // Only report the most significant burst
            }
        }

        // Detect unusual timing: transactions between midnight-5am
        val lateNightCount = allTransactions.count { tx ->
            val hourOfDay = ((tx.createdTime % 86400000) / 3600000).toInt()
            hourOfDay in 0..4 // midnight to 5am (exclusive)
        }
        if (lateNightCount >= 3) {
            anomalies.add(
                Anomaly(
                    type = "Late Night",
                    description = "$lateNightCount transactions made between midnight and 5am",
                    severity = if (lateNightCount >= 8) "high" else if (lateNightCount >= 5) "medium" else "low"
                )
            )
        }

        // Detect high-volume weeks: weeks with 3+ moves across leagues
        val byWeek = allTransactions.groupBy { it.week }
        val highVolumeWeeks = byWeek.filter { (_, txs) ->
            txs.size >= 3 && txs.map { it.leagueId }.toSet().size >= 2
        }
        if (highVolumeWeeks.isNotEmpty()) {
            val worstWeek = highVolumeWeeks.maxByOrNull { it.value.size }
            if (worstWeek != null) {
                anomalies.add(
                    Anomaly(
                        type = "High Volume",
                        description = "${highVolumeWeeks.size} weeks with 3+ cross-league moves (peak: Week ${worstWeek.key} with ${worstWeek.value.size} moves)",
                        severity = if (highVolumeWeeks.size >= 5) "high" else if (highVolumeWeeks.size >= 3) "medium" else "low"
                    )
                )
            }
        }

        if (anomalies.isEmpty()) return null

        return AnomalyResult(anomalies = anomalies.sortedBy {
            when (it.severity) { "high" -> 0; "medium" -> 1; else -> 2 }
        })
    }

    @Composable
    override fun Render(data: AnomalyResult, modifier: Modifier) {
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

                data.anomalies.forEach { anomaly ->
                    val dotColor = when (anomaly.severity) {
                        "high" -> MaterialTheme.colorScheme.error
                        "medium" -> Color(0xFFFF9800) // orange
                        else -> MaterialTheme.colorScheme.tertiary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SleeperSpacing.xxs),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = anomaly.type,
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = anomaly.description,
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
