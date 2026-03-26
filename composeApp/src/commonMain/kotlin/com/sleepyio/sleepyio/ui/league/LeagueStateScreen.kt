package com.sleepyio.sleepyio.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import com.sleepyio.sleepyio.client.model.stats.PlayerStats
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class MatchupDisplay(
    val matchupId: Long,
    val team1: TeamInfo,
    val team2: TeamInfo?,
    val isInteresting: Boolean = false,
    val interestReason: String? = null
)

data class TeamInfo(
    val rosterId: Long,
    val ownerName: String,
    val teamName: String,
    val points: Float,
    val projectedPoints: Float?,
    val avatar: String?,
    val starters: List<PlayerInfo> = emptyList(),
    val playersYetToPlay: Int = 0
)

data class PlayerInfo(
    val playerId: String,
    val name: String,
    val position: String,
    val points: Float,
    val projectedPoints: Float?,
    val isStarted: Boolean,
    val status: String? = null
)

data class ResponsiveSizes(
    val headerFontSize: androidx.compose.ui.unit.TextUnit,
    val weekFontSize: androidx.compose.ui.unit.TextUnit,
    val matchupHeaderFontSize: androidx.compose.ui.unit.TextUnit,
    val teamNameFontSize: androidx.compose.ui.unit.TextUnit,
    val ownerNameFontSize: androidx.compose.ui.unit.TextUnit,
    val pointsFontSize: androidx.compose.ui.unit.TextUnit,
    val playerNameFontSize: androidx.compose.ui.unit.TextUnit,
    val positionFontSize: androidx.compose.ui.unit.TextUnit,
    val cardPadding: androidx.compose.ui.unit.Dp,
    val spacingSmall: androidx.compose.ui.unit.Dp,
    val spacingMedium: androidx.compose.ui.unit.Dp,
    val cardElevation: androidx.compose.ui.unit.Dp,
    val cornerRadius: androidx.compose.ui.unit.Dp
)

@Composable
private fun getResponsiveSizes(): ResponsiveSizes {
    val configuration = LocalWindowInfo.current
    val screenWidth = configuration.containerSize.width
    val screenHeight = configuration.containerSize.height

    return when {
        screenWidth >= 1200 -> ResponsiveSizes( // Large screens (desktop/tablet landscape)
            headerFontSize = 32.sp,
            weekFontSize = 20.sp,
            matchupHeaderFontSize = 18.sp,
            teamNameFontSize = 16.sp,
            ownerNameFontSize = 14.sp,
            pointsFontSize = 20.sp,
            playerNameFontSize = 14.sp,
            positionFontSize = 12.sp,
            cardPadding = 20.dp,
            spacingSmall = 8.dp,
            spacingMedium = 16.dp,
            cardElevation = 6.dp,
            cornerRadius = 16.dp
        )
        screenWidth >= 800 -> ResponsiveSizes( // Medium screens (tablet portrait)
            headerFontSize = 28.sp,
            weekFontSize = 18.sp,
            matchupHeaderFontSize = 16.sp,
            teamNameFontSize = 14.sp,
            ownerNameFontSize = 12.sp,
            pointsFontSize = 18.sp,
            playerNameFontSize = 12.sp,
            positionFontSize = 11.sp,
            cardPadding = 16.dp,
            spacingSmall = 6.dp,
            spacingMedium = 12.dp,
            cardElevation = 4.dp,
            cornerRadius = 14.dp
        )
        else -> ResponsiveSizes( // Small screens (phone)
            headerFontSize = 24.sp,
            weekFontSize = 16.sp,
            matchupHeaderFontSize = 14.sp,
            teamNameFontSize = 12.sp,
            ownerNameFontSize = 11.sp,
            pointsFontSize = 16.sp,
            playerNameFontSize = 10.sp,
            positionFontSize = 9.sp,
            cardPadding = 12.dp,
            spacingSmall = 4.dp,
            spacingMedium = 8.dp,
            cardElevation = 2.dp,
            cornerRadius = 12.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueStateScreen(
    leagueId: Long,
    modifier: Modifier = Modifier
) {
    var matchups by remember { mutableStateOf<List<MatchupDisplay>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentWeek by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(leagueId) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null

                // Get current NFL state to determine the week
                val nflState = SleeperClient.getNflState()
                currentWeek = nflState?.week?.toInt() ?: 1

                // Fetch current week matchups, rosters, users, players, and projections
                val rawMatchups = SleeperClient.getCurrentWeekMatchups(leagueId)
                val rosters = SleeperClient.getRostersInLeague(leagueId)
                val users = SleeperClient.getUsersInLeague(leagueId)

                // Fetch projections and stats for current week
                val season = nflState?.season ?: "2025"
                val projectionsDeferred = scope.async {
                    SleeperClient.getWeeklyProjections(season, currentWeek)
                }
                val statsDeferred = scope.async {
                    SleeperClient.getWeeklyStats(season, currentWeek)
                }
                val weeklyProjections = projectionsDeferred.await()
                val weeklyStats = statsDeferred.await()

                // Create lookup maps for efficiency
                val rosterMap = rosters.associateBy { it.rosterId.toLong() }
                val userMap = users.associateBy { it.userId }

                // Group matchups by matchup_id and create display objects
                val groupedMatchups = rawMatchups.groupBy { it.matchupId }

                matchups = groupedMatchups.map { (matchupId, teams) ->
                    val team1 = teams.firstOrNull()
                    val team2 = teams.getOrNull(1)

                    suspend fun createTeamInfo(matchup: SleeperMatchup): TeamInfo {
                        val roster = rosterMap[matchup.rosterId]
                        val user = roster?.ownerId?.let { userMap[it] }
                        val ownerName = user?.displayName ?: user?.userName ?: "Unknown Team"
                        val teamName = generateTeamName(ownerName)

                        // Create player info for starters with real stats and projections
                        val starterInfo = matchup.starters.mapNotNull { playerId ->
                            SleeperCache.getPlayer(playerId)?.let { player ->
                                val playerProjection = weeklyProjections[playerId]
                                val playerActualStats = weeklyStats[playerId]
                                val actualPts = playerActualStats?.fantasyPoints?.toFloat() ?: 0f
                                val projectedPts = playerProjection?.fantasyPoints?.toFloat()
                                val hasPlayed = playerActualStats != null && actualPts > 0f

                                PlayerInfo(
                                    playerId = playerId,
                                    name = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim(),
                                    position = player.position ?: "N/A",
                                    points = actualPts,
                                    projectedPoints = projectedPts,
                                    isStarted = hasPlayed,
                                    status = player.injuryStatus
                                )
                            }
                        }

                        // Calculate total projected points from starter projections
                        val totalProjected = starterInfo.mapNotNull { it.projectedPoints }.sum()

                        return TeamInfo(
                            rosterId = matchup.rosterId,
                            ownerName = ownerName,
                            teamName = teamName,
                            points = matchup.points,
                            projectedPoints = if (totalProjected > 0f) totalProjected else null,
                            avatar = user?.avatar,
                            starters = starterInfo,
                            playersYetToPlay = starterInfo.count { !it.isStarted }
                        )
                    }

                    val teamInfo1 = team1?.let { createTeamInfo(it) } ?: TeamInfo(0, "BYE", "BYE WEEK", 0f, null, null)
                    val teamInfo2 = team2?.let { createTeamInfo(it) }

                    // Determine if matchup is interesting
                    val (isInteresting, reason) = analyzeMatchupInterest(teamInfo1, teamInfo2)

                    MatchupDisplay(
                        matchupId = matchupId,
                        team1 = teamInfo1,
                        team2 = teamInfo2,
                        isInteresting = isInteresting,
                        interestReason = reason
                    )
                }.sortedBy {
                    // Sort by interest first, then by matchup ID
                    if (it.isInteresting) 0 else 1
                }

            } catch (e: Exception) {
                errorMessage = "Failed to load league data: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val sizes = getResponsiveSizes()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(sizes.cardPadding)
    ) {
        // Compact Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = sizes.spacingMedium),
            elevation = CardDefaults.cardElevation(defaultElevation = sizes.cardElevation),
            shape = RoundedCornerShape(sizes.cornerRadius)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(sizes.cardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "League Matchups",
                    fontSize = sizes.headerFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Week $currentWeek",
                    fontSize = sizes.weekFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            matchups.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No matchups found for this week",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(sizes.spacingMedium)
                ) {
                    items(matchups) { matchup ->
                        MatchupCard(matchup = matchup, sizes = sizes)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchupCard(
    matchup: MatchupDisplay,
    sizes: ResponsiveSizes,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (matchup.isInteresting) sizes.cardElevation else sizes.cardElevation / 2
        ),
        shape = RoundedCornerShape(sizes.cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (matchup.isInteresting)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(sizes.cardPadding)
        ) {
            // Matchup header with interest indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Matchup ${matchup.matchupId}",
                    fontSize = sizes.matchupHeaderFontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (matchup.isInteresting) {
                    Text(
                        text = "🔥 ${matchup.interestReason}",
                        fontSize = sizes.matchupHeaderFontSize * 0.85f,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizes.spacingSmall))

            if (matchup.team2 != null) {
                // Regular matchup with two teams
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamDisplay(
                        team = matchup.team1,
                        isWinning = matchup.team1.points > matchup.team2.points,
                        sizes = sizes,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "VS",
                        fontSize = sizes.matchupHeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = sizes.spacingSmall)
                    )

                    TeamDisplay(
                        team = matchup.team2,
                        isWinning = matchup.team2.points > matchup.team1.points,
                        sizes = sizes,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Expandable details button
                if (matchup.team1.starters.isNotEmpty() || matchup.team2.starters.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(sizes.spacingSmall))
                    Button(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = if (isExpanded) "Hide Player Details ▲" else "Show Player Details ▼",
                            fontSize = sizes.ownerNameFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expandable player details
                if (isExpanded && (matchup.team1.starters.isNotEmpty() || matchup.team2.starters.isNotEmpty())) {
                    Spacer(modifier = Modifier.height(sizes.spacingSmall))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Team 1 players
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${matchup.team1.teamName} Starters",
                                fontSize = sizes.playerNameFontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            matchup.team1.starters.forEach { player ->
                                PlayerRow(player = player, sizes = sizes)
                            }
                        }

                        Spacer(modifier = Modifier.width(sizes.spacingSmall))

                        // Team 2 players
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${matchup.team2.teamName} Starters",
                                fontSize = sizes.playerNameFontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            matchup.team2.starters.forEach { player ->
                                PlayerRow(player = player, sizes = sizes)
                            }
                        }
                    }
                }
            } else {
                // Bye week
                TeamDisplay(
                    team = matchup.team1,
                    isWinning = false,
                    sizes = sizes,
                    isBye = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TeamDisplay(
    team: TeamInfo,
    isWinning: Boolean,
    sizes: ResponsiveSizes,
    modifier: Modifier = Modifier,
    isBye: Boolean = false
) {
    val backgroundColor = when {
        isBye -> MaterialTheme.colorScheme.surfaceVariant
        isWinning -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isBye -> MaterialTheme.colorScheme.onSurfaceVariant
        isWinning -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isWinning) sizes.cardElevation / 1.5f else sizes.cardElevation / 3f
        ),
        shape = RoundedCornerShape(sizes.cornerRadius * 0.75f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizes.cardPadding * 0.75f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Team name
            Text(
                text = team.teamName,
                fontSize = sizes.teamNameFontSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            // Owner name
            Text(
                text = team.ownerName,
                fontSize = sizes.ownerNameFontSize,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            if (!isBye) {
                Spacer(modifier = Modifier.height(sizes.spacingSmall / 2))
                Text(
                    text = "${(team.points * 10).toInt() / 10.0} pts",
                    fontSize = sizes.pointsFontSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                // Show projected points if available
                team.projectedPoints?.let { projected ->
                    Text(
                        text = "Proj: ${(projected * 10).toInt() / 10.0}",
                        fontSize = sizes.positionFontSize,
                        color = if (team.points >= projected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Show players yet to play if available
                if (team.playersYetToPlay > 0) {
                    Text(
                        text = "${team.playersYetToPlay} to play",
                        fontSize = sizes.positionFontSize,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerRow(
    player: PlayerInfo,
    sizes: ResponsiveSizes,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = sizes.spacingSmall / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name,
                fontSize = sizes.playerNameFontSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row {
                Text(
                    text = player.position,
                    fontSize = sizes.positionFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (player.status != null && player.status != "Active") {
                    Text(
                        text = " • ${player.status}",
                        fontSize = sizes.positionFontSize,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${(player.points * 10).toInt() / 10.0} pts",
                fontSize = sizes.playerNameFontSize,
                fontWeight = FontWeight.Bold,
                color = if (player.isStarted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            )
            player.projectedPoints?.let { proj ->
                Text(
                    text = "proj ${(proj * 10).toInt() / 10.0}",
                    fontSize = sizes.positionFontSize,
                    color = if (player.points >= proj)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Helper function to generate team names based on owner names
private fun generateTeamName(ownerName: String): String {
    val teamSuffixes = listOf("Destroyers", "Legends", "Champions", "Warriors", "Thunder", "Lightning", "Storm", "Fire", "Ice", "Rockets")
    val words = ownerName.split(" ").filter { it.isNotEmpty() }
    return if (words.isNotEmpty()) {
        "${words.first()}'s ${teamSuffixes.random()}"
    } else {
        "Team ${teamSuffixes.random()}"
    }
}

// Helper function to analyze if a matchup is interesting
private fun analyzeMatchupInterest(team1: TeamInfo, team2: TeamInfo?): Pair<Boolean, String?> {
    if (team2 == null) return false to null

    val pointDiff = kotlin.math.abs(team1.points - team2.points)
    val playersYetToPlay = team1.playersYetToPlay + team2.playersYetToPlay

    return when {
        pointDiff <= 15 && playersYetToPlay > 2 -> true to "Close game with players yet to play"
        pointDiff <= 5 -> true to "Very close matchup!"
        playersYetToPlay >= 4 -> true to "Many players yet to play"
        team1.points > 150 || team2.points > 150 -> true to "High-scoring matchup"
        else -> false to null
    }
}



