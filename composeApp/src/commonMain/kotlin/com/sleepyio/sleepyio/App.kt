package com.sleepyio.sleepyio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.ui.shell.LeagueShellScreen
import com.sleepyio.sleepyio.ui.warroom.WarRoomScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sleepyio.composeapp.generated.resources.Res
import sleepyio.composeapp.generated.resources.sleeper_logo

// Navigation states
enum class NavigationScreen {
    USER_LOGIN,
    WAR_ROOM,
    LEAGUE_DETAIL,
    OPPONENT_DOSSIER
}

@Composable
@Preview
fun App() {
    val cache by produceState(initialValue = SleeperCache, producer = {
        SleeperCache.init()
        value = SleeperCache
    })

    SleeperTheme {
        val scope = rememberCoroutineScope()
        var currentScreen by remember { mutableStateOf(NavigationScreen.USER_LOGIN) }
        var username by remember { mutableStateOf("thehippokid") }
        var user: SleeperUser? by remember { mutableStateOf(null) }
        var selectedLeague: SleeperLeague? by remember { mutableStateOf(null) }
        var allLeagues by remember { mutableStateOf<List<SleeperLeague>>(emptyList()) }
        var currentSeason by remember { mutableStateOf("2025") }

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
        ) {
            when (currentScreen) {
                NavigationScreen.USER_LOGIN -> {
                    UserLoginScreen(
                        initialUsername = username,
                        onUserFound = { foundUser ->
                            user = foundUser
                            scope.launch {
                                allLeagues = SleeperClient.getLeaguesForUser(
                                    foundUser.userId.toString(), "nfl", currentSeason
                                )
                                currentScreen = NavigationScreen.WAR_ROOM
                            }
                        },
                        onUsernameChanged = { username = it }
                    )
                }

                NavigationScreen.WAR_ROOM -> {
                    user?.let { currentUser ->
                        WarRoomScreen(
                            user = currentUser,
                            leagues = allLeagues,
                            currentSeason = currentSeason,
                            onSeasonChanged = { newSeason ->
                                currentSeason = newSeason
                                scope.launch {
                                    allLeagues = SleeperClient.getLeaguesForUser(
                                        currentUser.userId.toString(), "nfl", newSeason
                                    )
                                }
                            },
                            onLeagueSelected = { league ->
                                selectedLeague = league
                                currentScreen = NavigationScreen.LEAGUE_DETAIL
                            },
                            onOpponentSelected = { _ ->
                                // Will be wired to OPPONENT_DOSSIER in Phase 5
                                currentScreen = NavigationScreen.WAR_ROOM
                            }
                        )
                    }
                }

                NavigationScreen.OPPONENT_DOSSIER -> {
                    // Placeholder — will be implemented in Phase 5
                    user?.let {
                        currentScreen = NavigationScreen.WAR_ROOM
                    }
                }

                NavigationScreen.LEAGUE_DETAIL -> {
                    selectedLeague?.let { league ->
                        user?.let { currentUser ->
                            LeagueShellScreen(
                                league = league,
                                user = currentUser,
                                onBack = { currentScreen = NavigationScreen.WAR_ROOM }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserLoginScreen(
    initialUsername: String,
    onUserFound: (SleeperUser) -> Unit,
    onUsernameChanged: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val client = remember { SleeperClient }
    var username by remember { mutableStateOf(initialUsername) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.sleeper_logo),
            contentDescription = "Sleeper Logo"
        )

        Spacer(modifier = Modifier.height(SleeperSpacing.md))

        TextField(
            value = username,
            onValueChange = {
                username = it
                onUsernameChanged(it)
                errorMessage = null
            },
            label = { Text("Enter Sleeper Username") },
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(SleeperSpacing.md))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val foundUser = client.getUser(username)
                        if (foundUser != null) {
                            onUserFound(foundUser)
                        } else {
                            errorMessage = "User not found"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && username.isNotBlank()
        ) {
            Text(if (isLoading) "Loading..." else "Find User")
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(SleeperSpacing.sm))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
