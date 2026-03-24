package com.sleepyio.sleepyio.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser

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
    var devWeek by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        HomeHeader(user = user)
        DevToolbar(
            currentSeason = currentSeason,
            onSeasonChanged = onSeasonChanged,
            currentWeek = devWeek,
            onWeekChanged = { devWeek = it }
        )
        CrossLeagueFeedScreen(
            leagues = leagues,
            user = user,
            onLeagueClick = onLeagueSelected,
            weekOverride = devWeek
        )
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
    val weeks = listOf(null) + (1..18).toList()

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
