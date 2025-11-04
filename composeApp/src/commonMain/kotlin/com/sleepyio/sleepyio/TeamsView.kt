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
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.util.pmap
import kotlinx.coroutines.async
import org.jetbrains.compose.ui.tooling.preview.Preview

data class UserWithRoster(
    val user: SleeperUser,
    val roster: SleeperRoster?
)

@Composable
@Preview
fun TeamsView(leagueId: String, onBack: () -> Unit = {}) {
    val client = remember { SleeperClient }
    var usersWithRosters: List<UserWithRoster> by remember { mutableStateOf(listOf()) }
    var isLoading by remember { mutableStateOf(true) }

    MyTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .safeContentPadding(),
            ) {
                Column {
                    // Back button
                    Button(
                        onClick = onBack,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text("← Back to Leagues")
                    }

                    if (isLoading) {
                        Text(
                            "Loading teams and rosters...",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        // Teams list with rosters
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    LaunchedEffect(leagueId) {
        try {
            val usersDeferred = async {
                client.getUsersInLeague(leagueId)
            }
            val rostersDeferred = async {
                client.getRostersInLeague(leagueId).pmap { roster ->
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
        } catch (e: Exception) {
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
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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
                Spacer(modifier = Modifier.height(16.dp))

                RosterDetails(roster = userWithRoster.roster)
            } else if (expanded && userWithRoster.roster == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No roster found for this user",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Expand/collapse indicator
            if (userWithRoster.roster != null) {
                Spacer(modifier = Modifier.height(8.dp))
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
    Column {
        // Starters section
        Text(
            text = "Starters (${roster.starters.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (roster.fullStarters.isNotEmpty()) {
            roster.fullStarters.forEachIndexed { index, playerId ->
                Text(
                    text = "${index + 1}. Player ID: $playerId",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else {
            Text(
                text = "No starters set",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bench players section
        val benchPlayers = roster.fullPlayers.filter { !roster.starters.contains(it.playerId) }
        Text(
            text = "Bench (${benchPlayers.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (benchPlayers.isNotEmpty()) {
            benchPlayers.forEachIndexed { index, player ->
                Text(
                    text = "${index + 1}. Player ID: $player",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else {
            Text(
                text = "No bench players",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Total roster size
        Text(
            text = "Total Players: ${roster.players.size}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
