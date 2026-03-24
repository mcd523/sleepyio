package com.sleepyio.sleepyio.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.ui.activity.ActivityFeedScreen
import com.sleepyio.sleepyio.ui.league.LeagueStateScreen
import com.sleepyio.sleepyio.ui.leaguemate.LeaguemateListScreen
import com.sleepyio.sleepyio.ui.leaguemate.LeaguemateProfileScreen
import com.sleepyio.sleepyio.ui.intel.IntelOverviewScreen

enum class LeagueTab(val label: String, val emoji: String) {
    MATCHUPS("Matchups", "\uD83C\uDFC8"),
    LEAGUEMATES("Leaguemates", "\uD83D\uDC65"),
    ACTIVITY("Activity", "\uD83D\uDCCA"),
    INTEL("Intel", "\uD83D\uDD0D")
}

sealed class DetailScreen {
    data class LeaguemateProfile(val userId: String) : DetailScreen()
}

@Composable
fun LeagueShellScreen(
    league: SleeperLeague,
    user: SleeperUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(LeagueTab.MATCHUPS) }
    var detailScreen by remember { mutableStateOf<DetailScreen?>(null) }

    val myUserId = user.userId ?: ""
    val repository = remember(league.leagueId, myUserId) {
        LeaguemateRepository(myUserId)
    }

    val configuration = LocalWindowInfo.current
    val screenWidth = configuration.containerSize.width
    val useRail = screenWidth >= 800

    // If showing a detail screen, render it on top
    if (detailScreen != null) {
        when (val detail = detailScreen!!) {
            is DetailScreen.LeaguemateProfile -> {
                LeaguemateProfileScreen(
                    targetUserId = detail.userId,
                    myUserId = myUserId,
                    currentLeagueId = league.leagueId,
                    repository = repository,
                    onBack = { detailScreen = null },
                    modifier = modifier
                )
            }
        }
        return
    }

    if (useRail) {
        // Desktop/tablet: NavigationRail on the left
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(modifier = Modifier.weight(1f))
                LeagueTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = { Text(tab.emoji, fontSize = 20.sp) },
                        label = { Text(tab.label) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Content
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ShellHeader(league = league, onBack = onBack)
                TabContent(
                    activeTab = activeTab,
                    league = league,
                    myUserId = myUserId,
                    repository = repository,
                    onLeaguemateClick = { userId ->
                        detailScreen = DetailScreen.LeaguemateProfile(userId)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // Mobile: NavigationBar at the bottom
        Scaffold(
            modifier = modifier,
            topBar = {
                ShellHeader(league = league, onBack = onBack)
            },
            bottomBar = {
                NavigationBar {
                    LeagueTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Text(tab.emoji, fontSize = 20.sp) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            TabContent(
                activeTab = activeTab,
                league = league,
                myUserId = myUserId,
                repository = repository,
                onLeaguemateClick = { userId ->
                    detailScreen = DetailScreen.LeaguemateProfile(userId)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellHeader(
    league: SleeperLeague,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = league.leagueName,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            TextButton(onClick = onBack) {
                Text("<- Home", color = MaterialTheme.colorScheme.primary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun TabContent(
    activeTab: LeagueTab,
    league: SleeperLeague,
    myUserId: String,
    repository: LeaguemateRepository,
    onLeaguemateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (activeTab) {
        LeagueTab.MATCHUPS -> {
            LeagueStateScreen(
                leagueId = league.leagueId,
                modifier = modifier
            )
        }
        LeagueTab.LEAGUEMATES -> {
            LeaguemateListScreen(
                leagueId = league.leagueId,
                leagueName = league.leagueName,
                myUserId = myUserId,
                repository = repository,
                onLeaguemateClick = onLeaguemateClick,
                modifier = modifier
            )
        }
        LeagueTab.ACTIVITY -> {
            ActivityFeedScreen(
                leagueId = league.leagueId,
                modifier = modifier
            )
        }
        LeagueTab.INTEL -> {
            IntelOverviewScreen(
                leagueId = league.leagueId,
                myUserId = myUserId,
                repository = repository,
                onLeaguemateClick = onLeaguemateClick,
                modifier = modifier
            )
        }
    }
}
