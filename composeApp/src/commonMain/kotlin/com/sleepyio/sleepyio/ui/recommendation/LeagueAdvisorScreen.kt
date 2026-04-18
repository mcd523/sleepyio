package com.sleepyio.sleepyio.ui.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.background
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.primary
import com.sleepyio.sleepyio.recommendation.LocalRecommendationService
import com.sleepyio.sleepyio.surface
import com.sleepyio.sleepyio.surfaceElevated

/** Tab identity inside [LeagueAdvisorScreen]. */
private enum class AdvisorTab(val label: String) {
    REPORT("Report"),
    START_SIT("Start/Sit"),
    WAIVERS("Waivers"),
    INSIGHT("Insight"),
}

/**
 * Host screen for the advisor area: shared week selector up top, four tabs
 * via a Material3 [NavigationBar] at the bottom, tab content in the middle.
 *
 * The current NFL week is resolved once from [SleeperClient.getNflState] and
 * used as the default dropdown value; the dropdown allows picking weeks 1..18.
 */
@Composable
fun LeagueAdvisorScreen(
    league: SleeperLeague,
    rosterId: Long,
    modifier: Modifier = Modifier,
) {
    val service = LocalRecommendationService.current
    val sizes = rememberAdvisorSizes()

    var currentWeek by remember { mutableStateOf(1) }
    var selectedWeek by remember { mutableStateOf(1) }
    var weekInitialised by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(AdvisorTab.REPORT) }

    LaunchedEffect(league.leagueId) {
        val nflState = SleeperClient.getNflState()
        val resolved = (nflState?.week?.toInt() ?: 1).coerceIn(1, 18)
        currentWeek = resolved
        if (!weekInitialised) {
            selectedWeek = resolved
            weekInitialised = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme[colors][background]),
    ) {
        // Shared week selector
        WeekSelector(
            selectedWeek = selectedWeek,
            currentWeek = currentWeek,
            onSelect = { selectedWeek = it },
            sizes = sizes,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                AdvisorTab.REPORT -> WeeklyReportTab(
                    service = service,
                    leagueId = league.leagueId,
                    rosterId = rosterId,
                    week = selectedWeek,
                )
                AdvisorTab.START_SIT -> StartSitTab(
                    service = service,
                    leagueId = league.leagueId,
                    rosterId = rosterId,
                    week = selectedWeek,
                )
                AdvisorTab.WAIVERS -> WaiversTab(
                    service = service,
                    leagueId = league.leagueId,
                    rosterId = rosterId,
                    week = selectedWeek,
                )
                AdvisorTab.INSIGHT -> PlayerInsightTab(
                    service = service,
                    leagueId = league.leagueId,
                    rosterId = rosterId,
                    week = selectedWeek,
                )
            }
        }

        NavigationBar(
            containerColor = Theme[colors][surface],
            contentColor = Theme[colors][onSurface],
        ) {
            AdvisorTab.values().forEach { entry ->
                NavigationBarItem(
                    selected = entry == tab,
                    onClick = { tab = entry },
                    icon = {
                        Text(
                            text = glyphFor(entry),
                            color = if (entry == tab) Theme[colors][primary] else Theme[colors][onSurfaceMuted],
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    label = {
                        Text(
                            text = entry.label,
                            color = if (entry == tab) Theme[colors][primary] else Theme[colors][onSurfaceMuted],
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Theme[colors][primary],
                        selectedTextColor = Theme[colors][primary],
                        unselectedIconColor = Theme[colors][onSurfaceMuted],
                        unselectedTextColor = Theme[colors][onSurfaceMuted],
                        indicatorColor = Theme[colors][surfaceElevated],
                    ),
                )
            }
        }
    }
}

@Composable
private fun WeekSelector(
    selectedWeek: Int,
    currentWeek: Int,
    onSelect: (Int) -> Unit,
    sizes: AdvisorSizes,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(sizes.cardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Advisor",
            color = Theme[colors][onSurface],
            fontSize = sizes.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(sizes.pillRadius))
                    .background(Theme[colors][primary])
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Week $selectedWeek",
                    color = Theme[colors][onPrimary],
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "\u25BE",
                    color = Theme[colors][onPrimary],
                    fontWeight = FontWeight.Bold,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Theme[colors][surface])
                    .border(1.dp, Theme[colors][outline], RoundedCornerShape(8.dp)),
            ) {
                (1..18).forEach { week ->
                    DropdownMenuItem(
                        text = {
                            val suffix = if (week == currentWeek) "  (current)" else ""
                            Text(
                                text = "Week $week$suffix",
                                color = if (week == selectedWeek) Theme[colors][primary] else Theme[colors][onSurface],
                                fontWeight = if (week == selectedWeek) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelect(week)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun glyphFor(tab: AdvisorTab): String = when (tab) {
    AdvisorTab.REPORT -> "R"
    AdvisorTab.START_SIT -> "S"
    AdvisorTab.WAIVERS -> "W"
    AdvisorTab.INSIGHT -> "I"
}
