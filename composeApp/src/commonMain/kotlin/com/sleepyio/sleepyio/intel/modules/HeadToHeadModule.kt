package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.ui.components.ComparisonCard
import com.sleepyio.sleepyio.ui.components.SeasonBreakdown
import kotlin.math.roundToInt

data class HeadToHeadResult(
    val h2hWins: Int,
    val h2hLosses: Int,
    val myAvgPoints: Float,
    val theirAvgPoints: Float,
    val allTimeMyAvg: Float,
    val allTimeTheirAvg: Float,
    val hasHistoricalData: Boolean,
    val seasonBreakdown: Map<String, SeasonH2H>
)

data class SeasonH2H(
    val wins: Int,
    val losses: Int,
    val myAvg: Float,
    val theirAvg: Float
)

private fun Float.fmt1(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return rounded.toString()
}

object HeadToHeadModule : InsightModule<HeadToHeadResult> {
    override val id = "head_to_head"
    override val displayName = "Head-to-Head"

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): HeadToHeadResult? {
        if (leagueHistory.isEmpty()) return null

        val (_, historyChain) = leagueHistory.entries.first()
        if (historyChain.isEmpty()) return null

        val repo = repository
        val currentLeagueId = historyChain.first().leagueId
        val h2hBySeason = repo.getHeadToHeadHistoryAllTime(currentLeagueId, targetUserId)

        if (h2hBySeason.isEmpty()) return null

        val allResults = h2hBySeason.values.flatten()
        val allMyScores = allResults.map { it.myPoints }
        val allTheirScores = allResults.map { it.theirPoints }

        // Current season averages from all matchups (not just H2H)
        val nflState = repo.getNflState()
        val currentWeek = nflState?.week?.toInt() ?: 1
        val rosters = repo.getRosters(currentLeagueId)
        val myRosterIds = rosters.filter { it.ownerId == myUserId }.map { it.rosterId.toLong() }.toSet()
        val oppRosterIds = rosters.filter { it.ownerId == targetUserId }.map { it.rosterId.toLong() }.toSet()

        val mySeasonScores = mutableListOf<Float>()
        val oppSeasonScores = mutableListOf<Float>()
        for (week in 1 until currentWeek) {
            val matchups = repo.getMatchups(currentLeagueId, week)
            matchups.find { it.rosterId in myRosterIds }?.let { mySeasonScores.add(it.points) }
            matchups.find { it.rosterId in oppRosterIds }?.let { oppSeasonScores.add(it.points) }
        }

        val seasonBreakdown = h2hBySeason.map { (season, results) ->
            season to SeasonH2H(
                wins = results.count { it.won },
                losses = results.count { !it.won },
                myAvg = if (results.isNotEmpty()) results.map { it.myPoints }.average().toFloat() else 0f,
                theirAvg = if (results.isNotEmpty()) results.map { it.theirPoints }.average().toFloat() else 0f
            )
        }.toMap()

        return HeadToHeadResult(
            h2hWins = allResults.count { it.won },
            h2hLosses = allResults.count { !it.won },
            myAvgPoints = if (mySeasonScores.isNotEmpty()) mySeasonScores.average().toFloat() else 0f,
            theirAvgPoints = if (oppSeasonScores.isNotEmpty()) oppSeasonScores.average().toFloat() else 0f,
            allTimeMyAvg = if (allMyScores.isNotEmpty()) allMyScores.average().toFloat() else 0f,
            allTimeTheirAvg = if (allTheirScores.isNotEmpty()) allTheirScores.average().toFloat() else 0f,
            hasHistoricalData = h2hBySeason.size > 1,
            seasonBreakdown = seasonBreakdown
        )
    }

    @Composable
    override fun Render(data: HeadToHeadResult, modifier: Modifier) {
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
                        text = "${data.h2hWins}-${data.h2hLosses} all time",
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.Bold,
                        color = if (data.h2hWins >= data.h2hLosses) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Current season scoring comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    ComparisonCard(
                        value = data.theirAvgPoints.fmt1(),
                        label = "Their avg pts/week",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    ComparisonCard(
                        value = data.myAvgPoints.fmt1(),
                        label = "Your avg pts/week",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (data.hasHistoricalData) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                    ) {
                        ComparisonCard(
                            value = data.allTimeTheirAvg.fmt1(),
                            label = "Their all-time avg",
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonCard(
                            value = data.allTimeMyAvg.fmt1(),
                            label = "Your all-time avg",
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                                text = "${seasonData.wins}-${seasonData.losses}",
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Bold,
                                color = if (seasonData.wins >= seasonData.losses) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "You: ${seasonData.myAvg.fmt1()} avg  /  Them: ${seasonData.theirAvg.fmt1()} avg",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
