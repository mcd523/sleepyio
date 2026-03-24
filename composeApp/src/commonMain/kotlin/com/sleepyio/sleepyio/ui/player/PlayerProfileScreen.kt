package com.sleepyio.sleepyio.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.graphql.PlayerNews
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.client.model.stats.PlayerStats
import com.sleepyio.sleepyio.client.model.stats.WeeklyProjections
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class PlayerProfileData(
    val player: SleeperPlayer,
    val weeklyStats: PlayerStats?,
    val seasonStats: PlayerStats?,
    val weeklyProjection: WeeklyProjections?,
    val news: List<PlayerNews>,
    val opponent: String?,
    val gameTime: String?,
    val rank: Int?,
    val positionRank: Int?,
    val currentWeek: Int = 1
)

data class StartSitRecommendation(
    val recommendation: String, // "START", "SIT", "FLEX"
    val confidence: String, // "High", "Medium", "Low"
    val reasoning: List<String>
)

// Helper function for formatting numbers
private fun formatNumber(value: Double, decimals: Int = 1): String {
    return if (decimals == 0) {
        value.toInt().toString()
    } else {
        var result = value
        repeat(decimals) { result *= 10.0 }
        result = kotlin.math.round(result)
        repeat(decimals) { result /= 10.0 }
        result.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    playerId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var profileData by remember { mutableStateOf<PlayerProfileData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(playerId) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null

                val player = SleeperCache.getPlayer(playerId)
                if (player == null) {
                    errorMessage = "Player not found"
                    isLoading = false
                    return@launch
                }

                // Fetch all player data in parallel
                val nflState = SleeperClient.getNflState()
                val currentWeek = nflState?.week?.toInt() ?: 1

                val weeklyStats = try {
                    SleeperClient.getPlayerStats(playerId, groupByWeek = false)
                } catch (e: Exception) {
                    null
                }

                val seasonStats = try {
                    SleeperClient.getPlayerStats(playerId, groupByWeek = false)
                } catch (e: Exception) {
                    null
                }

                val projection = try {
                    SleeperClient.getAllWeeklyProjections()
                        .firstOrNull { it.playerId == playerId }
                } catch (e: Exception) {
                    null
                }

                val news = try {
                    SleeperClient.getPlayerNews(playerId, limit = 5)
                } catch (e: Exception) {
                    emptyList()
                }

                // Get rankings
                val rankings = try {
                    SleeperClient.getPlayerRanks()
                } catch (e: Exception) {
                    emptyList()
                }

                rankings.forEach { rank ->
                    println("Rankings Debug: Player ID: $rank")
                }

//                val playerRanking = rankings.firstOrNull { it.playerId == playerId }

                profileData = PlayerProfileData(
                    player = player,
                    weeklyStats = weeklyStats,
                    seasonStats = seasonStats,
                    weeklyProjection = projection,
                    news = news,
                    opponent = projection?.opponent,
                    gameTime = projection?.date,
                    rank = 1,
                    positionRank = 1,
                    currentWeek = currentWeek
                )

            } catch (e: Exception) {
                errorMessage = "Failed to load player profile: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = SleeperType.headline)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                errorMessage != null -> {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(SleeperSpacing.md),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(SleeperSpacing.md),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                profileData != null -> {
                    PlayerProfileContent(
                        profileData = profileData!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerProfileContent(
    profileData: PlayerProfileData,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(SleeperSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.md)
    ) {
        // Header with player image and basic info
        item {
            PlayerHeaderCard(profileData)
        }

        // Start/Sit Recommendation
        item {
            StartSitRecommendationCard(profileData, profileData.currentWeek)
        }

        // Current Matchup Information
        item {
            CurrentMatchupCard(profileData)
        }

        // Biographical Information
        item {
            BiographicalInfoCard(profileData.player)
        }

        // Season Stats and Rankings
        item {
            SeasonStatsCard(profileData)
        }

        // Recent News
        if (profileData.news.isNotEmpty()) {
            item {
                RecentNewsCard(profileData.news)
            }
        }
    }
}

@Composable
fun PlayerHeaderCard(profileData: PlayerProfileData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SleeperSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player headshot
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(SleeperSpacing.md),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val headshotUrl = SleeperClient.getPlayerHeadshotUrl(profileData.player.playerId)
                    AsyncImage(
                        model = headshotUrl,
                        contentDescription = "${profileData.player.firstName} ${profileData.player.lastName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(SleeperSpacing.xl))

                // Player info
                Column {
                    Text(
                        text = "${profileData.player.firstName ?: ""} ${profileData.player.lastName ?: ""}".trim(),
                        fontSize = SleeperType.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Text(
                        text = "${profileData.player.position} • ${profileData.player.team ?: "FA"}",
                        fontSize = SleeperType.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )

                    if (profileData.player.injuryStatus != null && profileData.player.injuryStatus != "Active") {
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(SleeperSpacing.sm)
                        ) {
                            Text(
                                text = profileData.player.injuryStatus,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                fontSize = SleeperType.body
                            )
                        }
                    }

                    if (profileData.rank != null) {
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                        Text(
                            text = "Overall Rank: #${profileData.rank} • ${profileData.player.position} Rank: #${profileData.positionRank ?: "N/A"}",
                            fontSize = SleeperType.body,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StartSitRecommendationCard(profileData: PlayerProfileData, currentWeek: Int = 1) {
    val recommendation = generateStartSitRecommendation(profileData)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (recommendation.recommendation) {
                "START" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                "SIT" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Week $currentWeek Recommendation",
                        fontSize = SleeperType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = recommendation.recommendation,
                        fontSize = SleeperType.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (recommendation.recommendation) {
                            "START" -> MaterialTheme.colorScheme.tertiary
                            "SIT" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${recommendation.confidence} Confidence",
                        modifier = Modifier.padding(horizontal = SleeperSpacing.md, vertical = SleeperSpacing.sm),
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(SleeperSpacing.md))

            Text(
                text = "Analysis:",
                fontSize = SleeperType.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(SleeperSpacing.sm))

            recommendation.reasoning.forEach { reason ->
                Row(
                    modifier = Modifier.padding(vertical = SleeperSpacing.xs)
                ) {
                    Text(
                        text = "• ",
                        fontSize = SleeperType.body
                    )
                    Text(
                        text = reason,
                        fontSize = SleeperType.body,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentMatchupCard(profileData: PlayerProfileData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏈",
                    fontSize = SleeperType.headline
                )
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    text = "Current Week Matchup",
                    fontSize = SleeperType.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(SleeperSpacing.md))

            if (profileData.opponent != null) {
                // Opponent
                InfoRow(
                    label = "Opponent",
                    value = "vs ${profileData.opponent}"
                )

                // Game time
                if (profileData.gameTime != null) {
                    InfoRow(
                        label = "Game Time",
                        value = profileData.gameTime
                    )
                }

                // Projected points
                profileData.weeklyProjection?.stats?.get("pts_ppr")?.jsonPrimitive?.doubleOrNull?.let { proj ->
                    InfoRow(
                        label = "Projected Points",
                        value = "${formatNumber(proj, 1)} pts",
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                }

                // Current week stats if available
                profileData.weeklyStats?.stats?.get("pts_ppr")?.jsonPrimitive?.doubleOrNull?.let { pts ->
                    InfoRow(
                        label = "Current Points",
                        value = "${formatNumber(pts, 1)} pts",
                        valueColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            } else {
                Text(
                    text = "No matchup information available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = SleeperType.body
                )
            }
        }
    }
}

@Composable
fun BiographicalInfoCard(player: SleeperPlayer) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👤",
                    fontSize = SleeperType.headline
                )
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    text = "Biographical Information",
                    fontSize = SleeperType.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(SleeperSpacing.md))

            player.age?.let { InfoRow("Age", "$it years old") }
            player.height?.let { InfoRow("Height", it) }
            player.weight?.let { InfoRow("Weight", "$it lbs") }
            player.college?.let { InfoRow("College", it) }
            player.number?.let { InfoRow("Jersey Number", "#$it") }
            player.team?.let { InfoRow("Team", it) }
            player.depthChartPosition?.let {
                InfoRow("Depth Chart", it)
            }
        }
    }
}

@Composable
fun SeasonStatsCard(profileData: PlayerProfileData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊",
                    fontSize = SleeperType.headline
                )
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    text = "2024 Season Stats & Rankings",
                    fontSize = SleeperType.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(SleeperSpacing.md))

            // Rankings
            if (profileData.rank != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox(
                        label = "Overall Rank",
                        value = "#${profileData.rank}",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                    StatBox(
                        label = "${profileData.player.position} Rank",
                        value = "#${profileData.positionRank ?: "N/A"}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.md))
            }

            // Position-specific stats
            profileData.seasonStats?.stats?.let { stats ->
                when (profileData.player.position) {
                    "QB" -> QuarterbackStats(stats)
                    "RB" -> RunningBackStats(stats)
                    "WR", "TE" -> ReceiverStats(stats)
                    "K" -> KickerStats(stats)
                    "DEF" -> DefenseStats(stats)
                    else -> GenericStats(stats)
                }
            } ?: run {
                Text(
                    text = "Season stats not available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = SleeperType.body
                )
            }
        }
    }
}

@Composable
fun QuarterbackStats(stats: JsonObject) {
    Column {
        stats["pass_yd"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Passing Yards", formatNumber(it, 0))
        }
        stats["pass_td"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Passing TDs", formatNumber(it, 0))
        }
        stats["pass_int"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Interceptions", formatNumber(it, 0))
        }
        stats["rush_yd"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Rushing Yards", formatNumber(it, 0))
        }
        stats["rush_td"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Rushing TDs", formatNumber(it, 0))
        }
        stats["pts_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points", formatNumber(it, 1),
                valueColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun RunningBackStats(stats: JsonObject) {
    Column {
        stats["rush_yd"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Rushing Yards", formatNumber(it, 0))
        }
        stats["rush_td"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Rushing TDs", formatNumber(it, 0))
        }
        stats["rec"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Receptions", formatNumber(it, 0))
        }
        stats["rec_yd"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Receiving Yards", formatNumber(it, 0))
        }
        stats["rec_td"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Receiving TDs", formatNumber(it, 0))
        }
        stats["pts_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points", formatNumber(it, 1),
                valueColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ReceiverStats(stats: JsonObject) {
    Column {
        stats["rec"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Receptions", formatNumber(it, 0))
        }
        stats["rec_yd"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Receiving Yards", formatNumber(it, 0))
        }
        stats["rec_td"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Receiving TDs", formatNumber(it, 0))
        }
        stats["rec_tgt"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Targets", formatNumber(it, 0))
        }
        stats["pts_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points", formatNumber(it, 1),
                valueColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun KickerStats(stats: JsonObject) {
    Column {
        stats["fgm"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Field Goals Made", formatNumber(it, 0))
        }
        stats["fga"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Field Goal Attempts", formatNumber(it, 0))
        }
        stats["xpm"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Extra Points Made", formatNumber(it, 0))
        }
        stats["pts_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points", formatNumber(it, 1),
                valueColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun DefenseStats(stats: JsonObject) {
    Column {
        stats["pts_allow"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Points Allowed", formatNumber(it, 0))
        }
        stats["sack"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Sacks", formatNumber(it, 0))
        }
        stats["int"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Interceptions", formatNumber(it, 0))
        }
        stats["fum_rec"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fumble Recoveries", formatNumber(it, 0))
        }
        stats["pts_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points", formatNumber(it, 1),
                valueColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun GenericStats(stats: JsonObject) {
    Column {
        stats["pts_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points (PPR)", formatNumber(it, 1),
                valueColor = MaterialTheme.colorScheme.primary)
        }
        stats["pts_std"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points (STD)", formatNumber(it, 1))
        }
        stats["pts_half_ppr"]?.jsonPrimitive?.doubleOrNull?.let {
            InfoRow("Fantasy Points (Half PPR)", formatNumber(it, 1))
        }
    }
}

@Composable
fun RecentNewsCard(news: List<PlayerNews>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📰",
                    fontSize = SleeperType.headline
                )
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    text = "Recent News",
                    fontSize = SleeperType.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(SleeperSpacing.md))

            news.forEach { newsItem ->
                NewsItem(newsItem)
                Spacer(modifier = Modifier.height(SleeperSpacing.sm))
            }
        }
    }
}

@Composable
fun NewsItem(news: PlayerNews) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(SleeperSpacing.sm)
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.sm)
        ) {
            // Try to extract headline from metadata
            val headline = try {
                news.metadata?.jsonObject?.get("title")?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }

            if (headline != null) {
                Text(
                    text = headline,
                    fontSize = SleeperType.body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.padding(top = SleeperSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
            ) {
                news.source?.let {
                    Text(
                        text = it,
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                news.published?.let {
                    Text(
                        text = "• $it",
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SleeperSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = SleeperType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = SleeperType.body,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = SleeperType.statValue,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                fontSize = SleeperType.caption,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

fun generateStartSitRecommendation(profileData: PlayerProfileData): StartSitRecommendation {
    val reasoning = mutableListOf<String>()
    var startScore = 0

    val player = profileData.player
    val projection = profileData.weeklyProjection
    val rank = profileData.rank
    val positionRank = profileData.positionRank

    // Injury status
    when (player.injuryStatus) {
        "Out", "IR", "Suspended" -> {
            reasoning.add("Player is ${player.injuryStatus.lowercase()} and will not play")
            return StartSitRecommendation("SIT", "High", reasoning)
        }
        "Doubtful" -> {
            startScore -= 3
            reasoning.add("Doubtful injury status - unlikely to play or limited if active")
        }
        "Questionable" -> {
            startScore -= 1
            reasoning.add("Questionable injury status - monitor leading up to game time")
        }
    }

    // Projected points
    val projectedPoints = projection?.stats?.get("pts_ppr")?.jsonPrimitive?.doubleOrNull
    if (projectedPoints != null) {
        when {
            projectedPoints >= 20.0 -> {
                startScore += 3
                reasoning.add("Strong projection of ${formatNumber(projectedPoints, 1)} points")
            }
            projectedPoints >= 15.0 -> {
                startScore += 2
                reasoning.add("Solid projection of ${formatNumber(projectedPoints, 1)} points")
            }
            projectedPoints >= 10.0 -> {
                startScore += 1
                reasoning.add("Moderate projection of ${formatNumber(projectedPoints, 1)} points")
            }
            else -> {
                startScore -= 1
                reasoning.add("Low projection of only ${formatNumber(projectedPoints, 1)} points")
            }
        }
    }

    // Position rank
    if (positionRank != null) {
        val positionThresholds = when (player.position) {
            "QB" -> 12
            "RB" -> 24
            "WR" -> 36
            "TE" -> 12
            "K" -> 12
            "DEF" -> 12
            else -> 24
        }

        when {
            positionRank <= positionThresholds / 3 -> {
                startScore += 2
                reasoning.add("Top-tier ${player.position} (ranked #$positionRank)")
            }
            positionRank <= positionThresholds * 2 / 3 -> {
                startScore += 1
                reasoning.add("Mid-tier ${player.position} (ranked #$positionRank)")
            }
            positionRank > positionThresholds -> {
                startScore -= 1
                reasoning.add("Below average ${player.position} ranking (#$positionRank)")
            }
        }
    }

    // Matchup analysis
    if (projection?.opponent != null) {
        reasoning.add("Facing ${projection.opponent} this week")
    }

    // News sentiment (basic check)
    if (profileData.news.isNotEmpty()) {
        val latestNews = profileData.news.first()
        val headline = try {
            latestNews.metadata?.jsonObject?.get("title")?.jsonPrimitive?.content?.lowercase()
        } catch (e: Exception) {
            null
        }

        if (headline != null) {
            when {
                headline.contains("injured") || headline.contains("limited") || headline.contains("questionable") -> {
                    startScore -= 1
                    reasoning.add("Recent injury news - monitor practice participation")
                }
                headline.contains("cleared") || headline.contains("full practice") || headline.contains("activated") -> {
                    startScore += 1
                    reasoning.add("Positive injury news - cleared for action")
                }
            }
        }
    }

    // Depth chart position
    if (player.depthChartPosition != null && player.depthChartOrder != null) {
        when (player.depthChartOrder) {
            1L -> {
                startScore += 1
                reasoning.add("Listed as starter on depth chart")
            }
            2L -> {
                reasoning.add("Listed as backup - limited opportunity")
            }
            else -> {
                startScore -= 1
                reasoning.add("Deep on depth chart - very limited touches expected")
            }
        }
    }

    // Determine recommendation
    val (recommendation, confidence) = when {
        startScore >= 3 -> "START" to "High"
        startScore >= 1 -> "START" to "Medium"
        startScore >= -1 -> "FLEX" to "Medium"
        startScore >= -3 -> "SIT" to "Medium"
        else -> "SIT" to "High"
    }

    return StartSitRecommendation(recommendation, confidence, reasoning)
}

