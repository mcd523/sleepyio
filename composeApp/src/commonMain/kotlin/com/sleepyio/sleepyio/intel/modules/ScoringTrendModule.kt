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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.ui.components.SeasonBreakdown
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun Float.fmt1(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return rounded.toString()
}

data class ScoringTrendResult(
    val weeklyScores: List<Float>,
    val averagePoints: Float,
    val consistencyRating: String,
    val currentStreak: Pair<String, Int>,
    val allTimeAverage: Float = 0f,
    val seasonBreakdown: Map<String, ScoringTrendResult> = emptyMap()
)

object ScoringTrendModule : InsightModule<ScoringTrendResult> {
    override val id = "scoring_trend"
    override val displayName = "Scoring Trends"

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): ScoringTrendResult? {
        if (leagueHistory.isEmpty()) return null

        val (_, historyChain) = leagueHistory.entries.first()
        if (historyChain.isEmpty()) return null

        val repo = repository
        val nflState = repo.getNflState()
        val currentLeagueId = historyChain.first().leagueId

        val allScores = mutableListOf<Float>()
        val seasonResults = mutableMapOf<String, ScoringTrendResult>()

        for (league in historyChain) {
            val rosters = repo.getRosters(league.leagueId)
            val userRosterIds = rosters.filter { it.ownerId == targetUserId }.map { it.rosterId.toLong() }.toSet()
            if (userRosterIds.isEmpty()) continue

            val isCurrentSeason = league.leagueId == currentLeagueId
            val maxWeek = if (isCurrentSeason) {
                nflState?.week?.toInt() ?: 1
            } else {
                18
            }

            val weeklyScores = mutableListOf<Float>()
            val weeklyWins = mutableListOf<Boolean>()

            for (week in 1 until maxWeek) {
                val matchups = repo.getMatchups(league.leagueId, week)
                val myMatchup = matchups.find { it.rosterId in userRosterIds } ?: continue
                weeklyScores.add(myMatchup.points)
                allScores.add(myMatchup.points)

                val opponent = matchups.find {
                    myMatchup.matchupId != null && it.matchupId == myMatchup.matchupId && it.rosterId !in userRosterIds
                }
                weeklyWins.add(opponent != null && myMatchup.points > opponent.points)
            }

            if (weeklyScores.isNotEmpty()) {
                val avg = weeklyScores.average().toFloat()
                val variance = weeklyScores.map { (it - avg) * (it - avg) }.average()
                val stdDev = sqrt(variance).toFloat()
                val cv = if (avg > 0) stdDev / avg else 0f

                val consistencyRating = when {
                    cv < 0.10f -> "Very consistent"
                    cv < 0.20f -> "Consistent"
                    cv < 0.30f -> "Moderate variance"
                    else -> "Inconsistent (high variance)"
                }

                var streakType = if (weeklyWins.lastOrNull() == true) "W" else "L"
                var streakCount = 0
                for (i in weeklyWins.indices.reversed()) {
                    val won = weeklyWins[i]
                    if ((won && streakType == "W") || (!won && streakType == "L")) {
                        streakCount++
                    } else break
                }

                seasonResults[league.season] = ScoringTrendResult(
                    weeklyScores = weeklyScores,
                    averagePoints = avg,
                    consistencyRating = consistencyRating,
                    currentStreak = streakType to streakCount
                )
            }
        }

        if (allScores.isEmpty()) return null

        // Use the current season's data for the main display
        val currentSeason = historyChain.first().season
        val currentSeasonResult = seasonResults[currentSeason]
        val allTimeAvg = allScores.average().toFloat()

        // Overall consistency
        val overallVariance = allScores.map { (it - allTimeAvg) * (it - allTimeAvg) }.average()
        val overallStdDev = sqrt(overallVariance).toFloat()
        val overallCv = if (allTimeAvg > 0) overallStdDev / allTimeAvg else 0f
        val overallConsistency = when {
            overallCv < 0.10f -> "Very consistent"
            overallCv < 0.20f -> "Consistent"
            overallCv < 0.30f -> "Moderate variance"
            else -> "Inconsistent (high variance)"
        }

        return ScoringTrendResult(
            weeklyScores = currentSeasonResult?.weeklyScores ?: emptyList(),
            averagePoints = currentSeasonResult?.averagePoints ?: 0f,
            consistencyRating = if (seasonResults.size > 1) overallConsistency
                else currentSeasonResult?.consistencyRating ?: "N/A",
            currentStreak = currentSeasonResult?.currentStreak ?: ("" to 0),
            allTimeAverage = allTimeAvg,
            seasonBreakdown = seasonResults
        )
    }

    @Composable
    override fun Render(data: ScoringTrendResult, modifier: Modifier) {
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

                // Mini bar chart for current season
                if (data.weeklyScores.isNotEmpty()) {
                    val maxScore = data.weeklyScores.max()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        data.weeklyScores.forEachIndexed { index, score ->
                            val fraction = if (maxScore > 0) score / maxScore else 0f
                            val isLast = index == data.weeklyScores.lastIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(
                                        if (isLast) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        if (data.seasonBreakdown.size > 1 && data.allTimeAverage > 0) {
                            Text(
                                text = data.allTimeAverage.fmt1(),
                                fontSize = SleeperType.body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(SleeperSpacing.xs))
                            Text(
                                text = "all-time avg/wk",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = data.averagePoints.fmt1(),
                                fontSize = SleeperType.body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(SleeperSpacing.xs))
                            Text(
                                text = "avg/wk",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = data.consistencyRating,
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                val (streakType, streakCount) = data.currentStreak
                if (streakCount > 1) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.xs))
                    Text(
                        text = "${streakCount}${streakType} streak",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = if (streakType == "W") MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
                    )
                }

                SeasonBreakdown(seasonData = data.seasonBreakdown) { season, seasonData ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = season,
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${seasonData.averagePoints.fmt1()} avg/wk",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = seasonData.consistencyRating,
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
