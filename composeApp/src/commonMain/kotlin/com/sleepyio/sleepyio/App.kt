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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.EspnClient
import com.sleepyio.sleepyio.client.OddsClient
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.odds.GameOdds
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.model.EspnMapper
import com.sleepyio.sleepyio.model.Platform
import com.sleepyio.sleepyio.model.UnifiedLeague
import com.sleepyio.sleepyio.ui.analysis.MatchupAnalyzerScreen
import com.sleepyio.sleepyio.ui.analysis.ScheduleStrengthScreen
import com.sleepyio.sleepyio.ui.analysis.StartSitScreen
import com.sleepyio.sleepyio.ui.analysis.TradeAnalyzerScreen
import com.sleepyio.sleepyio.ui.analysis.WaiverTargetsScreen
import com.sleepyio.sleepyio.ui.league.LeagueStateScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sleepyio.composeapp.generated.resources.Res
import sleepyio.composeapp.generated.resources.sleeper_logo

// Navigation states
enum class NavigationScreen {
    USER_LOGIN,
    LEAGUE_LIST,
    LEAGUE_STATE
}

// Dashboard tabs within league view
enum class LeagueTab(val label: String) {
    MATCHUPS("Matchups"),
    ANALYZER("Analyzer"),
    START_SIT("Start/Sit"),
    WAIVERS("Waivers"),
    TRADES("Trades"),
    SCHEDULE("Schedule")
}

@Composable
@Preview
fun App() {
    val cache by produceState(initialValue = SleeperCache, producer = {
        SleeperCache.init()
        value = SleeperCache
    })

    MyTheme {
        var currentScreen by remember { mutableStateOf(NavigationScreen.USER_LOGIN) }
        var username by remember { mutableStateOf("thehippokid") }
        var user: SleeperUser? by remember { mutableStateOf(null) }
        var selectedLeague: SleeperLeague? by remember { mutableStateOf(null) }
        var gameOdds by remember { mutableStateOf<List<GameOdds>>(emptyList()) }

        // ESPN state
        var espnClient by remember { mutableStateOf<EspnClient?>(null) }
        var espnLeagues by remember { mutableStateOf<List<UnifiedLeague>>(emptyList()) }

        Box(
            modifier = Modifier
                .background(Theme[colors][background])
                .safeContentPadding()
                .fillMaxSize()
        ) {
            when (currentScreen) {
                NavigationScreen.USER_LOGIN -> {
                    PlatformLoginScreen(
                        initialUsername = username,
                        onSleeperUserFound = { foundUser ->
                            user = foundUser
                            currentScreen = NavigationScreen.LEAGUE_LIST
                        },
                        onUsernameChanged = { username = it },
                        onEspnConnected = { client, leagues ->
                            espnClient = client
                            espnLeagues = leagues
                            currentScreen = NavigationScreen.LEAGUE_LIST
                        }
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
                        LeagueDashboard(
                            league = league,
                            gameOdds = gameOdds,
                            onBackToLeagues = {
                                currentScreen = NavigationScreen.LEAGUE_LIST
                                selectedLeague = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformLoginScreen(
    initialUsername: String,
    onSleeperUserFound: (SleeperUser) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onEspnConnected: (EspnClient, List<UnifiedLeague>) -> Unit
) {
    var selectedPlatform by remember { mutableStateOf(Platform.SLEEPER) }

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

        // Platform toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedPlatform = Platform.SLEEPER },
                colors = if (selectedPlatform == Platform.SLEEPER)
                    ButtonDefaults.buttonColors()
                else
                    ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Sleeper")
            }
            OutlinedButton(
                onClick = { selectedPlatform = Platform.ESPN },
                colors = if (selectedPlatform == Platform.ESPN)
                    ButtonDefaults.buttonColors()
                else
                    ButtonDefaults.outlinedButtonColors()
            ) {
                Text("ESPN")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedPlatform) {
            Platform.SLEEPER -> SleeperLoginForm(
                initialUsername = initialUsername,
                onUserFound = onSleeperUserFound,
                onUsernameChanged = onUsernameChanged
            )
            Platform.ESPN -> EspnLoginForm(onConnected = onEspnConnected)
        }
    }
}

@Composable
private fun SleeperLoginForm(
    initialUsername: String,
    onUserFound: (SleeperUser) -> Unit,
    onUsernameChanged: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(initialUsername) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    val foundUser = SleeperClient.getUser(username)
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
        Text(text = error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EspnLoginForm(
    onConnected: (EspnClient, List<UnifiedLeague>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var leagueId by remember { mutableStateOf("") }
    var espnS2 by remember { mutableStateOf("") }
    var swid by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    TextField(
        value = leagueId,
        onValueChange = { leagueId = it; errorMessage = null },
        label = { Text("ESPN League ID") },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    TextField(
        value = espnS2,
        onValueChange = { espnS2 = it; errorMessage = null },
        label = { Text("espn_s2 Cookie") },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    TextField(
        value = swid,
        onValueChange = { swid = it; errorMessage = null },
        label = { Text("SWID Cookie") },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        "Find these in your browser cookies after logging into fantasy.espn.com",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val client = EspnClient(espnS2, swid)
                    val lid = leagueId.toLongOrNull() ?: throw Exception("Invalid league ID")
                    val response = client.getLeague(lid, 2025)
                    if (response != null) {
                        val unified = EspnMapper.toUnifiedLeague(response)
                        onConnected(client, listOf(unified))
                    } else {
                        errorMessage = "Could not connect to ESPN league"
                    }
                } catch (e: Exception) {
                    errorMessage = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        },
        enabled = !isLoading && leagueId.isNotBlank() && espnS2.isNotBlank() && swid.isNotBlank()
    ) {
        Text(if (isLoading) "Connecting..." else "Connect to ESPN")
    }

    errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = error, color = MaterialTheme.colorScheme.error)
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
fun LeagueDashboard(
    league: SleeperLeague,
    gameOdds: List<GameOdds>,
    onBackToLeagues: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(LeagueTab.MATCHUPS) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackToLeagues) {
                Text("← Back")
            }
            Text(
                text = league.leagueName ?: "League",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Tab navigation
        ScrollableTabRow(
            selectedTabIndex = LeagueTab.entries.indexOf(selectedTab),
            modifier = Modifier.fillMaxWidth()
        ) {
            LeagueTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            tab.label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Tab content
        when (selectedTab) {
            LeagueTab.MATCHUPS -> LeagueStateScreen(
                leagueId = league.leagueId,
                modifier = Modifier.fillMaxSize()
            )
            LeagueTab.ANALYZER -> MatchupAnalyzerScreen(
                leagueId = league.leagueId,
                gameOdds = gameOdds,
                modifier = Modifier.fillMaxSize()
            )
            LeagueTab.START_SIT -> StartSitScreen(
                leagueId = league.leagueId,
                gameOdds = gameOdds,
                modifier = Modifier.fillMaxSize()
            )
            LeagueTab.WAIVERS -> WaiverTargetsScreen(
                leagueId = league.leagueId,
                modifier = Modifier.fillMaxSize()
            )
            LeagueTab.TRADES -> TradeAnalyzerScreen(
                leagueId = league.leagueId,
                modifier = Modifier.fillMaxSize()
            )
            LeagueTab.SCHEDULE -> ScheduleStrengthScreen(
                leagueId = league.leagueId,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
