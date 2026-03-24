package com.sleepyio.sleepyio.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.LeagueTable

enum class HomeTab(val label: String, val emoji: String) {
    FEED("Feed", "\uD83D\uDCF0"),
    LEAGUES("Leagues", "\uD83C\uDFC6")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShellScreen(
    user: SleeperUser,
    leagues: List<SleeperLeague>,
    onLeagueSelected: (SleeperLeague) -> Unit,
    onSeasonChanged: (String) -> Unit,
    currentSeason: String,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(HomeTab.FEED) }
    var devWeek by remember { mutableStateOf<Int?>(null) }

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
                DevToolbar(
                    currentSeason = currentSeason,
                    onSeasonChanged = onSeasonChanged,
                    currentWeek = devWeek,
                    onWeekChanged = { devWeek = it }
                )
                HomeTabContent(
                    activeTab = activeTab,
                    user = user,
                    leagues = leagues,
                    onLeagueSelected = onLeagueSelected,
                    weekOverride = devWeek
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                Column {
                    HomeHeader(user = user)
                    DevToolbar(
                        currentSeason = currentSeason,
                        onSeasonChanged = onSeasonChanged,
                        currentWeek = devWeek,
                        onWeekChanged = { devWeek = it }
                    )
                }
            },
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
                    onLeagueSelected = onLeagueSelected,
                    weekOverride = devWeek
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
private fun DevToolbar(
    currentSeason: String,
    onSeasonChanged: (String) -> Unit,
    currentWeek: Int?,
    onWeekChanged: (Int?) -> Unit
) {
    var seasonExpanded by remember { mutableStateOf(false) }
    var weekExpanded by remember { mutableStateOf(false) }

    val seasons = listOf("2024", "2025", "2026")
    val weeks = listOf(null) + (1..18).toList() // null = "Auto"

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SleeperSpacing.md, vertical = SleeperSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.md)
        ) {
            Text(
                "DEV",
                fontSize = SleeperType.caption,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            // Season picker
            Box {
                OutlinedButton(
                    onClick = { seasonExpanded = true },
                    contentPadding = PaddingValues(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                ) {
                    Text("Season: $currentSeason", fontSize = SleeperType.caption)
                }
                DropdownMenu(
                    expanded = seasonExpanded,
                    onDismissRequest = { seasonExpanded = false }
                ) {
                    seasons.forEach { season ->
                        DropdownMenuItem(
                            text = { Text(season) },
                            onClick = {
                                onSeasonChanged(season)
                                seasonExpanded = false
                            }
                        )
                    }
                }
            }

            // Week picker
            Box {
                OutlinedButton(
                    onClick = { weekExpanded = true },
                    contentPadding = PaddingValues(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                ) {
                    Text(
                        "Week: ${currentWeek?.toString() ?: "Auto"}",
                        fontSize = SleeperType.caption
                    )
                }
                DropdownMenu(
                    expanded = weekExpanded,
                    onDismissRequest = { weekExpanded = false }
                ) {
                    weeks.forEach { week ->
                        DropdownMenuItem(
                            text = { Text(week?.toString() ?: "Auto (from API)") },
                            onClick = {
                                onWeekChanged(week)
                                weekExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    activeTab: HomeTab,
    user: SleeperUser,
    leagues: List<SleeperLeague>,
    onLeagueSelected: (SleeperLeague) -> Unit,
    weekOverride: Int? = null
) {
    when (activeTab) {
        HomeTab.FEED -> CrossLeagueFeedScreen(
            leagues = leagues,
            user = user,
            onLeagueClick = onLeagueSelected,
            weekOverride = weekOverride
        )
        HomeTab.LEAGUES -> LeagueTable(
            user = user,
            onLeagueClick = onLeagueSelected,
            preloadedLeagues = leagues
        )
    }
}
