package com.sleepyio.sleepyio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun LeagueTable(user: SleeperUser) {
    val client = remember { SleeperClient }
    val scope = rememberCoroutineScope()
    var leagues: List<SleeperLeague> by remember { mutableStateOf(listOf()) }
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            scope.launch {
                leagues = client.getLeaguesForUser(user.userId.toString(), "nfl", "2025")
            }
            Text("League Table for ${user.displayName}")
            AnimatedVisibility(leagues.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    leagues.map { league ->
                        Text("League: ${league.leagueName} (ID: ${league.leagueId})")
                        var users: List<SleeperUser> by remember { mutableStateOf(listOf()) }
                        scope.launch {
                            users = client.getUsersInLeague(league.leagueId.toString())
                        }
                        AnimatedVisibility(users.isNotEmpty()) {
                            users.map {
                                Text(" - User: ${it.displayName} (ID: ${it.userId})")
                            }

                        }
                    }
                }
            }
        }
    }
}