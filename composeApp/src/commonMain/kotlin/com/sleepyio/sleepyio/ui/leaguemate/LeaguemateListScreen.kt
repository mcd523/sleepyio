package com.sleepyio.sleepyio.ui.leaguemate

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.graphql.LeagueStanding
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.intel.model.LeaguemateProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@Composable
fun LeaguemateListScreen(
    leagueId: Long,
    leagueName: String,
    myUserId: String,
    repository: LeaguemateRepository,
    onLeaguemateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var profiles by remember { mutableStateOf<List<LeaguemateProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(leagueId) {
        try {
            isLoading = true
            val users = repository.getLeaguemates(leagueId)
            val standings = repository.getStandings(leagueId)
            val currentOpponent = repository.getCurrentMatchupOpponent(leagueId)

            val standingsByOwner = standings.associateBy { it.ownerId }

            val profileDeferreds = users
                .filter { it.userId != myUserId }
                .map { user ->
                    async {
                        val sharedLeagues = try {
                            repository.getSharedLeagues(user.userId ?: "")
                        } catch (_: Exception) { emptyList() }

                        LeaguemateProfile(
                            user = user,
                            sharedLeagueCount = sharedLeagues.size,
                            record = standingsByOwner[user.userId],
                            isCurrentOpponent = currentOpponent?.userId == user.userId
                        )
                    }
                }

            profiles = profileDeferreds.awaitAll()
                .sortedWith(compareByDescending<LeaguemateProfile> { it.isCurrentOpponent }
                    .thenByDescending { it.record?.wins ?: 0 })

        } catch (e: Exception) {
            errorMessage = "Failed to load leaguemates: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(SleeperSpacing.md)) {
        Text(
            text = "Leaguemates",
            fontSize = SleeperType.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = leagueName,
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    items(profiles) { profile ->
                        LeaguemateRow(
                            profile = profile,
                            onClick = { onLeaguemateClick(profile.user.userId ?: "") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaguemateRow(
    profile: LeaguemateProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(SleeperSpacing.sm + SleeperSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                if (profile.user.avatar != null) {
                    AsyncImage(
                        model = SleeperClient.getAvatarUrl(profile.user.avatar!!),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = (profile.user.displayName ?: profile.user.userName ?: "?").take(2).uppercase(),
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
                    text = profile.user.displayName ?: profile.user.userName ?: "Unknown",
                    fontSize = SleeperType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val record = profile.record
                val recordText = if (record != null) "${record.wins}-${record.losses}" else ""
                val sharedText = "${profile.sharedLeagueCount} shared league${if (profile.sharedLeagueCount != 1) "s" else ""}"
                Text(
                    text = listOfNotNull(
                        recordText.ifEmpty { null },
                        sharedText
                    ).joinToString(" \u2022 "),
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (profile.isCurrentOpponent) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "This week's\nopponent",
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xs)
                    )
                }
            }
        }
    }
}
