package com.sleepyio.sleepyio.ui.leaguemate

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.graphql.LeagueStanding
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.InsightRegistry
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.intel.model.MatchupResult
import com.sleepyio.sleepyio.ui.components.InsightCard

@Composable
fun LeaguemateProfileScreen(
    targetUserId: String,
    myUserId: String,
    currentLeagueId: Long,
    repository: LeaguemateRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var targetUser by remember { mutableStateOf<SleeperUser?>(null) }
    var sharedLeagues by remember { mutableStateOf<List<SleeperLeague>>(emptyList()) }
    var leagueHistory by remember { mutableStateOf<Map<Long, List<SleeperLeague>>>(emptyMap()) }
    var totalSeasons by remember { mutableStateOf(0) }
    var standing by remember { mutableStateOf<LeagueStanding?>(null) }
    var h2hBySeason by remember { mutableStateOf<Map<String, List<MatchupResult>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(targetUserId) {
        try {
            isLoading = true
            targetUser = SleeperClient.getUserById(targetUserId)
            sharedLeagues = repository.getSharedLeagues(targetUserId)

            // Resolve league history for all shared leagues
            val historyMap = mutableMapOf<Long, List<SleeperLeague>>()
            val allSeasons = mutableSetOf<String>()
            for (league in sharedLeagues) {
                val history = repository.getLeagueHistory(league.leagueId)
                historyMap[league.leagueId] = history
                history.forEach { allSeasons.add(it.season) }
            }
            leagueHistory = historyMap
            totalSeasons = allSeasons.size

            val standings = repository.getStandings(currentLeagueId)
            standing = standings.find { it.ownerId == targetUserId }

            h2hBySeason = repository.getHeadToHeadHistoryAllTime(currentLeagueId, targetUserId)
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val user = targetUser ?: return
    val modules = InsightRegistry.getAll()

    val allH2hResults = h2hBySeason.values.flatten()
    val h2hWins = allH2hResults.count { it.won }
    val h2hLosses = allH2hResults.count { !it.won }
    val totalFpts = standing?.fpts ?: 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = SleeperSpacing.xxl)
    ) {
        // Back button
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(SleeperSpacing.sm)) {
                Text("<- Back", color = MaterialTheme.colorScheme.primary)
            }
        }

        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .padding(SleeperSpacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        if (user.avatar != null) {
                            AsyncImage(
                                model = SleeperClient.getAvatarUrl(user.avatar!!),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = (user.displayName ?: user.userName ?: "?").take(2).uppercase(),
                                    fontSize = SleeperType.headline,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                    Text(
                        text = user.displayName ?: user.userName ?: "Unknown",
                        fontSize = SleeperType.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val subtitle = buildString {
                        append("${sharedLeagues.size} shared league${if (sharedLeagues.size != 1) "s" else ""}")
                        if (totalSeasons > 1) {
                            append(", $totalSeasons seasons")
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = SleeperType.body,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xl),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val record = standing
                        if (record != null) {
                            ProfileStat("${record.wins}-${record.losses}", "Record")
                        }
                        ProfileStat("${totalFpts.toInt()}", "Total Pts")
                        if (allH2hResults.isNotEmpty()) {
                            val h2hLabel = if (h2hBySeason.size > 1) "vs You (all-time)" else "vs You"
                            ProfileStat("$h2hWins-$h2hLosses", h2hLabel)
                        }
                    }
                }
            }
        }

        // Insight modules
        items(modules) { module ->
            @Suppress("UNCHECKED_CAST")
            InsightCard(
                module = module as com.sleepyio.sleepyio.intel.InsightModule<Any>,
                targetUserId = targetUserId,
                myUserId = myUserId,
                leagueHistory = leagueHistory,
                repository = repository,
                modifier = Modifier.padding(horizontal = SleeperSpacing.md, vertical = SleeperSpacing.xs)
            )
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = SleeperType.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = SleeperType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
