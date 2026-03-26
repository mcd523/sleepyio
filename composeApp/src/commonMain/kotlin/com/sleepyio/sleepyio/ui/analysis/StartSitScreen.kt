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
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.odds.GameOdds
import com.sleepyio.sleepyio.model.UnifiedPlayer
import com.sleepyio.sleepyio.model.UnifiedTeam
import com.sleepyio.sleepyio.service.SleeperService
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class TeamWithRecommendations(
    val teamName: String,
    val rosterId: Int,
    val starters: List<UnifiedPlayer>,
    val bench: List<UnifiedPlayer>,
    val recommendations: List<SleeperService.StartSitRecommendation>
)

@Composable
fun StartSitScreen(
    leagueId: Long,
    gameOdds: List<GameOdds> = emptyList(),
    modifier: Modifier = Modifier
) {
    var teams by remember { mutableStateOf<List<TeamWithRecommendations>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTeamIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(leagueId) {
        scope.launch {
            isLoading = true

            val nflState = SleeperClient.getNflState()
            val season = nflState?.season ?: "2025"
            val week = nflState?.week?.toInt() ?: 1

            val rostersDeferred = scope.async { SleeperClient.getRostersInLeague(leagueId) }
            val usersDeferred = scope.async { SleeperClient.getUsersInLeague(leagueId) }
            val projectionsDeferred = scope.async { SleeperClient.getWeeklyProjections(season, week) }

            val rosters = rostersDeferred.await()
            val users = usersDeferred.await()
            val projections = projectionsDeferred.await()

            val userMap = users.associateBy { it.userId }

            teams = rosters.map { roster ->
                val user = roster.ownerId?.let { userMap[it] }
                val teamName = user?.displayName ?: user?.userName ?: "Team ${roster.rosterId}"

                suspend fun buildPlayer(playerId: String, isStarter: Boolean): UnifiedPlayer? {
                    val player = SleeperCache.getPlayer(playerId) ?: return null
                    val proj = projections[playerId]?.fantasyPoints
                    return UnifiedPlayer(
                        id = playerId,
                        name = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim(),
                        position = player.position ?: "N/A",
                        team = player.team,
                        isStarter = isStarter,
                        projectedPoints = proj,
                        injuryStatus = player.injuryStatus
                    )
                }

                val starters = roster.starters.mapNotNull { buildPlayer(it, true) }
                val benchIds = roster.players.filter { it !in roster.starters }
                val bench = benchIds.mapNotNull { buildPlayer(it, false) }

                val unifiedTeam = UnifiedTeam(
                    id = roster.rosterId.toString(),
                    name = teamName,
                    ownerName = teamName,
                    roster = starters + bench,
                    starters = starters,
                    bench = bench
                )

                val recommendations = SleeperService.analyzeStartSit(unifiedTeam)

                TeamWithRecommendations(
                    teamName = teamName,
                    rosterId = roster.rosterId,
                    starters = starters,
                    bench = bench,
                    recommendations = recommendations
                )
            }.sortedByDescending { it.recommendations.size }

            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Start/Sit Optimizer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (teams.isEmpty()) {
            Text("No teams found")
        } else {
            // Team selector
            ScrollableTabRow(selectedTabIndex = selectedTeamIndex) {
                teams.forEachIndexed { index, team ->
                    Tab(
                        selected = selectedTeamIndex == index,
                        onClick = { selectedTeamIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(team.teamName, fontSize = 12.sp)
                                if (team.recommendations.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge { Text("${team.recommendations.size}") }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val selectedTeam = teams[selectedTeamIndex]

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Recommendations
                if (selectedTeam.recommendations.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Lineup Alerts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                selectedTeam.recommendations.forEach { rec ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                "START ${rec.benchPlayer.name} (${rec.benchPlayer.position})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "over ${rec.starterToReplace.name} (${rec.starterToReplace.position})",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "+${(rec.pointsDelta * 10).toInt() / 10.0} projected pts",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Current starters
                item {
                    Text("Starters", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(selectedTeam.starters.sortedByDescending { it.projectedPoints ?: 0.0 }) { player ->
                    PlayerProjectionRow(player, isStarter = true, gameOdds = gameOdds)
                }

                // Bench
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Bench", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(selectedTeam.bench.sortedByDescending { it.projectedPoints ?: 0.0 }) { player ->
                    PlayerProjectionRow(player, isStarter = false, gameOdds = gameOdds)
                }
            }
        }
    }
}

@Composable
private fun PlayerProjectionRow(
    player: UnifiedPlayer,
    isStarter: Boolean,
    gameOdds: List<GameOdds>
) {
    val vegas = SleeperService.getVegasContext(player.team, gameOdds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isStarter)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(player.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row {
                    Text("${player.position} - ${player.team ?: "FA"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (player.injuryStatus != null) {
                        Text(" ${player.injuryStatus}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Proj: ${((player.projectedPoints ?: 0.0) * 10).toInt() / 10.0}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                vegas.impliedTeamTotal?.let { implied ->
                    Text(
                        "IT: ${(implied * 10).toInt() / 10.0}",
                        fontSize = 10.sp,
                        color = if (vegas.isSmashSpot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
