package com.sleepyio.sleepyio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.util.pmap
import kotlinx.coroutines.async
import org.jetbrains.compose.ui.tooling.preview.Preview

data class UserWithRoster(
    val user: SleeperUser,
    val roster: SleeperRoster?
)

@Composable
@Preview
fun TeamsView(league: SleeperLeague, onBack: () -> Unit = {}) {
    val client = remember { SleeperClient }
    var usersWithRosters: List<UserWithRoster> by remember { mutableStateOf(listOf()) }
    var isLoading by remember { mutableStateOf(true) }

    SleeperTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .safeContentPadding(),
            ) {
                Column {
                    // Back button
                    Row {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.padding(bottom = SleeperSpacing.md)
                        ) {
                            Text("← Back to Leagues")
                        }
                        if (isLoading) {
                            Text(
                                "Loading teams and rosters...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = SleeperSpacing.md)
                            )
                        } else {
                            Text(
                                text = "${league.leagueName} - Teams and Rosters",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = SleeperSpacing.md)
                            )
                        }
                    }
                    // Teams list with rosters
                    if (!isLoading) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                        ) {
                            items(usersWithRosters) { userWithRoster ->
                                TeamCard(userWithRoster = userWithRoster)
                            }
                        }
                    }
                }
            }
        }
    }

    // Load users and rosters when the component is created
    LaunchedEffect(league) {
        try {
            val usersDeferred = async {
                client.getUsersInLeague(league.leagueId)
            }
            val rostersDeferred = async {
                client.getRostersInLeague(league.leagueId).pmap { roster ->
                    val players = roster.players.pmap { playerId ->
                        SleeperCache.getPlayer(playerId)
                    }.filterNotNull()
                    val starterIds = roster.starters.toSet()
                    val starters = players.filter { starterIds.contains(it.playerId) }
                    roster.copy(fullPlayers = players, fullStarters = starters)
                }
            }

            val users = usersDeferred.await()
            val rosters = rostersDeferred.await()

            usersWithRosters = users.map { user ->
                val roster = rosters.find { it.ownerId == user.userId }
                UserWithRoster(user, roster)
            }
        } catch (_: Exception) {
            // Handle error - could show error message
            usersWithRosters = listOf()
        } finally {
            isLoading = false
        }
    }
}

@Composable
fun TeamCard(userWithRoster: UserWithRoster) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = !expanded }
            .padding(horizontal = SleeperSpacing.md),
        elevation = CardDefaults.cardElevation(defaultElevation = SleeperSpacing.xs),
        shape = RoundedCornerShape(SleeperSpacing.sm)
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.md)
        ) {
            // Team header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = userWithRoster.user.displayName ?: "Unknown User",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "User ID: ${userWithRoster.user.userId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                userWithRoster.roster?.let { roster ->
                    Text(
                        text = "Roster #${roster.rosterId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Expandable roster details
            if (expanded && userWithRoster.roster != null) {
                Spacer(modifier = Modifier.height(SleeperSpacing.md))

                RosterDetails(roster = userWithRoster.roster)
            } else if (expanded && userWithRoster.roster == null) {
                Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                Text(
                    text = "No roster found for this user",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Expand/collapse indicator
            if (userWithRoster.roster != null) {
                Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                Text(
                    text = if (expanded) "Tap to collapse" else "Tap to view roster",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RosterDetails(roster: SleeperRoster) {
    // Display starters in the correct order, similar to Sleeper order
    val comparePlayers = Comparator<SleeperPlayer> { p1, p2 ->
        val posOrder = listOf("QB", "RB", "WR", "TE", "K", "DEF")
        val p1Index = posOrder.indexOf(p1.position ?: "")
        val p2Index = posOrder.indexOf(p2.position ?: "")
        when {
            p1Index != p2Index -> p1Index - p2Index
            else -> (p1.lastName ?: "").compareTo(p2.lastName ?: "")
        }
    }
    Column {
        // Starters section
        Text(
            text = "Starters (${roster.starters.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(SleeperSpacing.xs))

        if (roster.fullStarters.isNotEmpty()) {
            roster.fullStarters.sortedWith(comparePlayers).forEach { player ->
                PlayerMetadataView(
                    player = player,
                    modifier = Modifier.padding(start = SleeperSpacing.sm, bottom = SleeperSpacing.xs)
                )
            }
        } else {
            Text(
                text = "No starters set",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SleeperSpacing.sm)
            )
        }

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        // Bench players section
        val benchPlayers = roster.fullPlayers.filter { !roster.starters.contains(it.playerId) }
        Text(
            text = "Bench (${benchPlayers.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(SleeperSpacing.xs))

        if (benchPlayers.isNotEmpty()) {
            benchPlayers.forEach { player ->
                PlayerMetadataView(
                    player = player,
                    modifier = Modifier.padding(start = SleeperSpacing.sm, bottom = SleeperSpacing.xs)
                )
            }
        } else {
            Text(
                text = "No bench players",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SleeperSpacing.sm)
            )
        }

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        // Total roster size
        Text(
            text = "Total Players: ${roster.players.size}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PlayerMetadataView(player: SleeperPlayer, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SleeperSpacing.xxs),
        elevation = CardDefaults.cardElevation(defaultElevation = SleeperSpacing.xxs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(SleeperSpacing.sm)
        ) {
            // Player name and position
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val fullName = buildString {
                        player.firstName?.let { append(it) }
                        if (player.firstName != null && player.lastName != null) append(" ")
                        player.lastName?.let { append(it) }
                    }.takeIf { it.isNotBlank() } ?: "Unknown Player"

                    Text(
                        text = fullName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = player.position ?: "N/A",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )

                        player.team?.let { team ->
                            Text(
                                text = " • $team",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Jersey number
                        player.number?.let { number ->
                            Text(
                                text = " #$number",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Injury status indicator
                player.injuryStatus?.let { status ->
                    if (status.lowercase() != "healthy") {
                        Text(
                            text = "⚠️ ${status.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Additional metadata row
            Spacer(modifier = Modifier.height(SleeperSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Physical attributes
                val physicalInfo = buildList {
                    player.age?.let { add("Age: $it") }
                    player.height?.let { add("Height: $it") }
                    player.weight?.let { add("Weight: $it lbs") }
                }.joinToString(" • ")

                if (physicalInfo.isNotEmpty()) {
                    Text(
                        text = physicalInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // College
                player.college?.let { college ->
                    Text(
                        text = college,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
