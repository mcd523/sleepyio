package com.sleepyio.sleepyio.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class TeamScheduleStrength(
    val teamName: String,
    val rosterId: Long,
    val pastOpponents: List<OpponentResult>,
    val averageOpponentScore: Double,
    val rank: Int = 0 // 1 = hardest schedule
)

data class OpponentResult(
    val week: Int,
    val opponentName: String,
    val opponentScore: Float,
    val yourScore: Float
)

@Composable
fun ScheduleStrengthScreen(
    leagueId: Long,
    modifier: Modifier = Modifier
) {
    var schedules by remember { mutableStateOf<List<TeamScheduleStrength>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(leagueId) {
        scope.launch {
            try {
                isLoading = true
                val nflState = SleeperClient.getNflState()
                val currentWeek = nflState?.week?.toInt() ?: 1

                val rostersDeferred = scope.async { SleeperClient.getRostersInLeague(leagueId) }
                val usersDeferred = scope.async { SleeperClient.getUsersInLeague(leagueId) }

                val rosters = rostersDeferred.await()
                val users = usersDeferred.await()
                val userMap = users.associateBy { it.userId }
                val rosterOwnerMap = rosters.associate { roster ->
                    roster.rosterId.toLong() to (roster.ownerId?.let { userMap[it] }?.displayName
                        ?: roster.ownerId?.let { userMap[it] }?.userName
                        ?: "Team ${roster.rosterId}")
                }

                // Fetch all past weeks' matchups
                val allMatchups = mutableMapOf<Int, List<SleeperMatchup>>()
                for (week in 1 until currentWeek) {
                    allMatchups[week] = SleeperClient.getMatchupsInLeague(leagueId, week)
                }

                // Build schedule strength for each team
                val teamSchedules = rosters.map { roster ->
                    val rosterId = roster.rosterId.toLong()
                    val teamName = rosterOwnerMap[rosterId] ?: "Unknown"

                    val opponents = allMatchups.flatMap { (week, matchups) ->
                        val grouped = matchups.groupBy { it.matchupId }
                        grouped.values.mapNotNull { pair ->
                            val myMatchup = pair.firstOrNull { it.rosterId == rosterId }
                            val oppMatchup = pair.firstOrNull { it.rosterId != rosterId }
                            if (myMatchup != null && oppMatchup != null) {
                                OpponentResult(
                                    week = week,
                                    opponentName = rosterOwnerMap[oppMatchup.rosterId] ?: "Unknown",
                                    opponentScore = oppMatchup.points,
                                    yourScore = myMatchup.points
                                )
                            } else null
                        }
                    }.sortedBy { it.week }

                    val avgOpponentScore = if (opponents.isNotEmpty())
                        opponents.map { it.opponentScore.toDouble() }.average()
                    else 0.0

                    TeamScheduleStrength(
                        teamName = teamName,
                        rosterId = rosterId,
                        pastOpponents = opponents,
                        averageOpponentScore = avgOpponentScore
                    )
                }

                // Rank by average opponent score (highest = hardest)
                schedules = teamSchedules
                    .sortedByDescending { it.averageOpponentScore }
                    .mapIndexed { index, schedule ->
                        schedule.copy(rank = index + 1)
                    }

                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to calculate schedule strength: ${e.message}"
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Schedule Strength",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Ranked by average opponent score (hardest to easiest)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedules) { schedule ->
                    ScheduleStrengthCard(schedule, totalTeams = schedules.size)
                }
            }
        }
    }
}

@Composable
private fun ScheduleStrengthCard(schedule: TeamScheduleStrength, totalTeams: Int) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                schedule.rank <= 2 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) // Hardest
                schedule.rank >= totalTeams - 1 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) // Easiest
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${schedule.rank}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(schedule.teamName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Avg Opp: ${(schedule.averageOpponentScore * 10).toInt() / 10.0}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${schedule.pastOpponents.size} games",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable week-by-week breakdown
            if (schedule.pastOpponents.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(
                        if (isExpanded) "Hide Details" else "Show Week-by-Week",
                        fontSize = 12.sp
                    )
                }

                if (isExpanded) {
                    schedule.pastOpponents.forEach { opp ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Wk ${opp.week}: vs ${opp.opponentName}", fontSize = 11.sp)
                            Text(
                                "${opp.yourScore} - ${opp.opponentScore}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (opp.yourScore > opp.opponentScore)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
