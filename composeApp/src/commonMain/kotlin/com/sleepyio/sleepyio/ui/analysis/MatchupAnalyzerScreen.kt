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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.odds.GameOdds
import com.sleepyio.sleepyio.client.model.stats.PlayerStats
import com.sleepyio.sleepyio.model.UnifiedPlayer
import com.sleepyio.sleepyio.service.SleeperService
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class AnalyzedMatchup(
    val matchupId: Long,
    val team1Name: String,
    val team2Name: String,
    val team1Starters: List<AnalyzedPlayer>,
    val team2Starters: List<AnalyzedPlayer>,
    val team1ActualPoints: Float,
    val team2ActualPoints: Float,
    val team1ProjectedPoints: Double,
    val team2ProjectedPoints: Double,
    val winProbability: SleeperService.MatchupProbability
)

data class AnalyzedPlayer(
    val name: String,
    val position: String,
    val team: String?,
    val actualPoints: Double,
    val projectedPoints: Double,
    val vegasContext: SleeperService.VegasContext?,
    val injuryStatus: String?
)

@Composable
fun MatchupAnalyzerScreen(
    leagueId: Long,
    gameOdds: List<GameOdds> = emptyList(),
    modifier: Modifier = Modifier
) {
    var analyzedMatchups by remember { mutableStateOf<List<AnalyzedMatchup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentWeek by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(leagueId) {
        scope.launch {
            try {
                isLoading = true
                val nflState = SleeperClient.getNflState()
                currentWeek = nflState?.week?.toInt() ?: 1
                val season = nflState?.season ?: "2025"

                val matchupsDeferred = scope.async { SleeperClient.getCurrentWeekMatchups(leagueId) }
                val rostersDeferred = scope.async { SleeperClient.getRostersInLeague(leagueId) }
                val usersDeferred = scope.async { SleeperClient.getUsersInLeague(leagueId) }
                val projectionsDeferred = scope.async { SleeperClient.getWeeklyProjections(season, currentWeek) }
                val statsDeferred = scope.async { SleeperClient.getWeeklyStats(season, currentWeek) }

                val rawMatchups = matchupsDeferred.await()
                val rosters = rostersDeferred.await()
                val users = usersDeferred.await()
                val projections = projectionsDeferred.await()
                val stats = statsDeferred.await()

                val rosterMap = rosters.associateBy { it.rosterId.toLong() }
                val userMap = users.associateBy { it.userId }

                val grouped = rawMatchups.groupBy { it.matchupId }
                analyzedMatchups = grouped.mapNotNull { (matchupId, teams) ->
                    val team1 = teams.firstOrNull() ?: return@mapNotNull null
                    val team2 = teams.getOrNull(1) ?: return@mapNotNull null

                    suspend fun analyzeTeam(matchup: com.sleepyio.sleepyio.client.model.league.SleeperMatchup): List<AnalyzedPlayer> {
                        return matchup.starters.mapNotNull { playerId ->
                            val player = SleeperCache.getPlayer(playerId) ?: return@mapNotNull null
                            val proj = projections[playerId]?.fantasyPoints ?: 0.0
                            val actual = stats[playerId]?.fantasyPoints ?: 0.0
                            val vegas = SleeperService.getVegasContext(player.team, gameOdds)

                            AnalyzedPlayer(
                                name = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim(),
                                position = player.position ?: "N/A",
                                team = player.team,
                                actualPoints = actual,
                                projectedPoints = proj,
                                vegasContext = vegas,
                                injuryStatus = player.injuryStatus
                            )
                        }
                    }

                    val team1Roster = rosterMap[team1.rosterId]
                    val team2Roster = rosterMap[team2.rosterId]
                    val team1User = team1Roster?.ownerId?.let { userMap[it] }
                    val team2User = team2Roster?.ownerId?.let { userMap[it] }

                    val team1Starters = analyzeTeam(team1)
                    val team2Starters = analyzeTeam(team2)

                    val team1Unified = team1Starters.mapIndexed { idx, it ->
                        UnifiedPlayer("t1_$idx", it.name, it.position, it.team, true, it.actualPoints, it.projectedPoints, it.injuryStatus)
                    }
                    val team2Unified = team2Starters.mapIndexed { idx, it ->
                        UnifiedPlayer("t2_$idx", it.name, it.position, it.team, true, it.actualPoints, it.projectedPoints, it.injuryStatus)
                    }

                    val winProb = SleeperService.estimateWinProbability(team1Unified, team2Unified)

                    AnalyzedMatchup(
                        matchupId = matchupId,
                        team1Name = team1User?.displayName ?: team1User?.userName ?: "Team ${team1.rosterId}",
                        team2Name = team2User?.displayName ?: team2User?.userName ?: "Team ${team2.rosterId}",
                        team1Starters = team1Starters,
                        team2Starters = team2Starters,
                        team1ActualPoints = team1.points,
                        team2ActualPoints = team2.points,
                        team1ProjectedPoints = team1Starters.sumOf { it.projectedPoints },
                        team2ProjectedPoints = team2Starters.sumOf { it.projectedPoints },
                        winProbability = winProb
                    )
                }

                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to analyze matchups: ${e.message}"
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Matchup Analyzer - Week $currentWeek",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(analyzedMatchups) { matchup ->
                    MatchupAnalysisCard(matchup)
                }
            }
        }
    }
}

@Composable
private fun MatchupAnalysisCard(matchup: AnalyzedMatchup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with win probability
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(matchup.team1Name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${matchup.team1ActualPoints} pts (proj ${(matchup.team1ProjectedPoints * 10).toInt() / 10.0})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val pct = (matchup.winProbability.team1WinPct * 100).toInt()
                    Text(
                        "$pct% - ${100 - pct}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(matchup.team2Name, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.End)
                    Text(
                        "${matchup.team2ActualPoints} pts (proj ${(matchup.team2ProjectedPoints * 10).toInt() / 10.0})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Win probability bar
            val winPct = matchup.winProbability.team1WinPct.toFloat()
            LinearProgressIndicator(
                progress = { winPct },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Side-by-side player comparison
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    matchup.team1Starters.forEach { player ->
                        AnalyzedPlayerRow(player)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    matchup.team2Starters.forEach { player ->
                        AnalyzedPlayerRow(player)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzedPlayerRow(player: AnalyzedPlayer) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(player.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Row {
                Text(player.position, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (player.injuryStatus != null) {
                    Text(" ${player.injuryStatus}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
                // Vegas smash spot indicator
                player.vegasContext?.let { vegas ->
                    if (vegas.isSmashSpot) {
                        Text(" SMASH", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    vegas.impliedTeamTotal?.let { implied ->
                        Text(" (${(implied * 10).toInt() / 10.0}IT)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${(player.actualPoints * 10).toInt() / 10.0}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("p${(player.projectedPoints * 10).toInt() / 10.0}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
