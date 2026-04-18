package com.sleepyio.sleepyio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.recommendation.LocalRecommendationService
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.ui.league.LeagueStateScreen
import com.sleepyio.sleepyio.ui.recommendation.LeagueAdvisorScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sleepyio.composeapp.generated.resources.Res
import sleepyio.composeapp.generated.resources.sleeper_logo

// Navigation states
enum class NavigationScreen {
    USER_LOGIN,
    LEAGUE_LIST,
    LEAGUE_STATE,
    LEAGUE_ADVISOR
}

@Composable
@Preview
fun App() {
    val cache by produceState(initialValue = SleeperCache, producer = {
        SleeperCache.init()
        value = SleeperCache
    })

    val recommendationService = remember { RecommendationService.default() }

    MyTheme {
        CompositionLocalProvider(LocalRecommendationService provides recommendationService) {
            var currentScreen by remember { mutableStateOf(NavigationScreen.USER_LOGIN) }
            var username by remember { mutableStateOf("thehippokid") }
            var user: SleeperUser? by remember { mutableStateOf(null) }
            var selectedLeague: SleeperLeague? by remember { mutableStateOf(null) }
            var advisorRosterId: Long? by remember { mutableStateOf(null) }

            Box(
                modifier = Modifier
                    .background(Theme[colors][background])
                    .safeContentPadding()
                    .fillMaxSize()
            ) {
                when (currentScreen) {
                    NavigationScreen.USER_LOGIN -> {
                        UserLoginScreen(
                            initialUsername = username,
                            onUserFound = { foundUser ->
                                user = foundUser
                                currentScreen = NavigationScreen.LEAGUE_LIST
                            },
                            onUsernameChanged = { username = it }
                        )
                    }

                    NavigationScreen.LEAGUE_LIST -> {
                        user?.let { currentUser ->
                            LeagueListScreen(
                                user = currentUser,
                                onLeagueSelected = { league ->
                                    selectedLeague = league
                                    currentScreen = NavigationScreen.LEAGUE_STATE
                                },
                                onBackToLogin = {
                                    currentScreen = NavigationScreen.USER_LOGIN
                                    user = null
                                    selectedLeague = null
                                }
                            )
                        }
                    }

                    NavigationScreen.LEAGUE_STATE -> {
                        selectedLeague?.let { league ->
                            LeagueStateNavigationScreen(
                                league = league,
                                currentUser = user,
                                onBackToLeagues = {
                                    currentScreen = NavigationScreen.LEAGUE_LIST
                                    selectedLeague = null
                                },
                                onOpenAdvisor = { rosterId ->
                                    advisorRosterId = rosterId
                                    currentScreen = NavigationScreen.LEAGUE_ADVISOR
                                }
                            )
                        }
                    }

                    NavigationScreen.LEAGUE_ADVISOR -> {
                        val league = selectedLeague
                        val rosterId = advisorRosterId
                        if (league != null && rosterId != null) {
                            LeagueAdvisorNavigationScreen(
                                league = league,
                                rosterId = rosterId,
                                onBack = {
                                    currentScreen = NavigationScreen.LEAGUE_STATE
                                    advisorRosterId = null
                                },
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

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun LeagueListScreen(
    user: SleeperUser,
    onLeagueSelected: (SleeperLeague) -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackToLogin) {
                Text("← Back")
            }
            Text(
                text = "Welcome, ${user.displayName ?: user.userName ?: "User"}",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        LeagueTable(
            user = user,
            onLeagueClick = onLeagueSelected
        )
    }
}

@Composable
fun LeagueStateNavigationScreen(
    league: SleeperLeague,
    currentUser: SleeperUser?,
    onBackToLeagues: () -> Unit,
    onOpenAdvisor: (rosterId: Long) -> Unit,
) {
    // Resolve the signed-in user's roster inside this league so the Advisor
    // button can hand it to LeagueAdvisorScreen. Stays null until the client
    // returns — the button disables itself in that window.
    var advisorRosterId: Long? by remember(league.leagueId, currentUser?.userId) {
        mutableStateOf(null)
    }
    LaunchedEffect(league.leagueId, currentUser?.userId) {
        val userId = currentUser?.userId ?: return@LaunchedEffect
        try {
            val rosters = SleeperClient.getRostersInLeague(league.leagueId)
            advisorRosterId = rosters
                .firstOrNull { it.ownerId == userId }
                ?.rosterId
                ?.toLong()
        } catch (_: Exception) {
            advisorRosterId = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onBackToLeagues) {
                    Text("← Back to Leagues")
                }
                Button(
                    onClick = { advisorRosterId?.let(onOpenAdvisor) },
                    enabled = advisorRosterId != null,
                ) {
                    Text("Advisor")
                }
            }
            Text(
                text = league.leagueName ?: "League",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        LeagueStateScreen(
            leagueId = league.leagueId,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LeagueAdvisorNavigationScreen(
    league: SleeperLeague,
    rosterId: Long,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("← Back to League")
            }
            Text(
                text = league.leagueName ?: "League",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        LeagueAdvisorScreen(
            league = league,
            rosterId = rosterId,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
