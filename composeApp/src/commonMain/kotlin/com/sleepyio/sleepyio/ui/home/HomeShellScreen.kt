package com.sleepyio.sleepyio.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.LeagueTable

enum class HomeTab(val label: String, val emoji: String) {
    FEED("Feed", "📰"),
    LEAGUES("Leagues", "🏆")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShellScreen(
    user: SleeperUser,
    leagues: List<SleeperLeague>,
    onLeagueSelected: (SleeperLeague) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(HomeTab.FEED) }

    // Detect responsive breakpoint — same approach as LeagueShellScreen
    val configuration = LocalWindowInfo.current
    val screenWidth = configuration.containerSize.width
    val useRail = screenWidth >= 800

    if (useRail) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(modifier = Modifier.height(12.dp))
                HomeTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = { Text(tab.emoji) },
                        label = { Text(tab.label) }
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                HomeHeader(user = user)
                HomeTabContent(
                    activeTab = activeTab,
                    user = user,
                    leagues = leagues,
                    onLeagueSelected = onLeagueSelected
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = { HomeHeader(user = user) },
            bottomBar = {
                NavigationBar {
                    HomeTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Text(tab.emoji) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                HomeTabContent(
                    activeTab = activeTab,
                    user = user,
                    leagues = leagues,
                    onLeagueSelected = onLeagueSelected
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeader(user: SleeperUser) {
    TopAppBar(
        title = {
            Text(
                text = "sleepy.io",
                style = MaterialTheme.typography.titleMedium
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun HomeTabContent(
    activeTab: HomeTab,
    user: SleeperUser,
    leagues: List<SleeperLeague>,
    onLeagueSelected: (SleeperLeague) -> Unit
) {
    when (activeTab) {
        HomeTab.FEED -> CrossLeagueFeedScreen(
            leagues = leagues,
            user = user,
            onLeagueClick = onLeagueSelected
        )
        HomeTab.LEAGUES -> LeagueTable(
            user = user,
            onLeagueClick = onLeagueSelected,
            preloadedLeagues = leagues
        )
    }
}
