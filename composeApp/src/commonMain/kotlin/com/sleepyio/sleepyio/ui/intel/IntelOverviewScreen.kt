package com.sleepyio.sleepyio.ui.intel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.intel.model.LeaguemateIntelSummary
import kotlin.math.roundToInt

@Composable
fun IntelOverviewScreen(
    leagueId: Long,
    myUserId: String,
    repository: LeaguemateRepository,
    onLeaguemateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var summaries by remember { mutableStateOf<List<LeaguemateIntelSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(leagueId) {
        try {
            isLoading = true
            summaries = repository.getLeagueIntelSummaries(leagueId)
        } catch (e: Exception) {
            errorMessage = "Failed to load intel: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val configuration = LocalWindowInfo.current
    val screenWidth = configuration.containerSize.width
    val useGrid = screenWidth >= 1200

    Column(modifier = modifier.fillMaxSize().padding(SleeperSpacing.md)) {
        Text(
            text = "League Intel",
            fontSize = SleeperType.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Scouting reports on all leaguemates",
            fontSize = SleeperType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(SleeperSpacing.md))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(SleeperSpacing.md),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> {
                if (useGrid) {
                    // 2-column grid for desktop
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                    ) {
                        val rows = summaries.chunked(2)
                        items(rows) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                            ) {
                                row.forEach { summary ->
                                    IntelSummaryCard(
                                        summary = summary,
                                        onClick = { onLeaguemateClick(summary.user.userId ?: "") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Fill empty space if odd number
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // Single column for mobile/tablet
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                    ) {
                        items(summaries) { summary ->
                            IntelSummaryCard(
                                summary = summary,
                                onClick = { onLeaguemateClick(summary.user.userId ?: "") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntelSummaryCard(
    summary: LeaguemateIntelSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(SleeperSpacing.sm + SleeperSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    if (summary.user.avatar != null) {
                        AsyncImage(
                            model = SleeperClient.getAvatarUrl(summary.user.avatar!!),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = (summary.user.displayName ?: summary.user.userName ?: "?").take(2).uppercase(),
                                fontSize = SleeperType.body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(SleeperSpacing.sm + SleeperSpacing.xs))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.user.displayName ?: summary.user.userName ?: "Unknown",
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val record = summary.record
                    if (record != null) {
                        Text(
                            text = "${record.wins}-${record.losses}",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (summary.isCurrentOpponent) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "This week",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xs)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SleeperSpacing.sm))

            // Intel stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // H2H record
                if (summary.h2hWins > 0 || summary.h2hLosses > 0) {
                    Column {
                        Text(
                            text = "${summary.h2hWins}-${summary.h2hLosses} vs you",
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = if (summary.h2hWins <= summary.h2hLosses) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                        if (summary.h2hSeasons > 1) {
                            Text(
                                text = "${summary.h2hSeasons} seasons",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Scoring avg + trend
                if (summary.avgPointsPerWeek > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val trendArrow = when (summary.scoringTrend) {
                            "up" -> " ^"
                            "down" -> " v"
                            else -> ""
                        }
                        val trendColor = when (summary.scoringTrend) {
                            "up" -> MaterialTheme.colorScheme.tertiary
                            "down" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "${((summary.avgPointsPerWeek * 10).roundToInt() / 10.0)} ppg$trendArrow",
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = trendColor
                        )
                    }
                }

                // Activity level
                val activityLabel = when {
                    summary.recentTransactionCount >= 20 -> "Very active"
                    summary.recentTransactionCount >= 10 -> "Active"
                    summary.recentTransactionCount >= 3 -> "Moderate"
                    summary.recentTransactionCount > 0 -> "Low activity"
                    else -> "Inactive"
                }
                Text(
                    text = activityLabel,
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
