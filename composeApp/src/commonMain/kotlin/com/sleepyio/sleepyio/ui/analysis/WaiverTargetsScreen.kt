package com.sleepyio.sleepyio.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyio.sleepyio.service.SleeperService
import kotlinx.coroutines.launch

@Composable
fun WaiverTargetsScreen(
    leagueId: Long,
    modifier: Modifier = Modifier
) {
    var targets by remember { mutableStateOf<List<SleeperService.WaiverTarget>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPosition by remember { mutableStateOf("ALL") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(leagueId) {
        scope.launch {
            try {
                isLoading = true
                val nflState = com.sleepyio.sleepyio.client.SleeperClient.getNflState()
                val season = nflState?.season ?: "2025"
                val week = nflState?.week?.toInt() ?: 1

                targets = SleeperService.findWaiverTargets(leagueId, season, week, limit = 40)
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to load waiver targets: ${e.message}"
                isLoading = false
            }
        }
    }

    val positions = listOf("ALL", "QB", "RB", "WR", "TE", "K", "DEF")
    val filteredTargets = if (selectedPosition == "ALL") targets
        else targets.filter { it.position == selectedPosition }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Waiver Wire Targets",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Trending adds not rostered in your league",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Position filter
        ScrollableTabRow(
            selectedTabIndex = positions.indexOf(selectedPosition),
            modifier = Modifier.fillMaxWidth()
        ) {
            positions.forEach { pos ->
                Tab(
                    selected = selectedPosition == pos,
                    onClick = { selectedPosition = pos },
                    text = { Text(pos, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            filteredTargets.isEmpty() -> Text("No waiver targets found for $selectedPosition")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredTargets) { target ->
                    WaiverTargetCard(target)
                }
            }
        }
    }
}

@Composable
private fun WaiverTargetCard(target: SleeperService.WaiverTarget) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(target.playerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row {
                    Text("${target.position} - ${target.team ?: "FA"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (target.injuryStatus != null) {
                        Text(" ${target.injuryStatus}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                // Trend count (adds in last 24h)
                Text(
                    "+${target.trendCount} adds",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                target.projectedPoints?.let { proj ->
                    Text(
                        "Proj: ${(proj * 10).toInt() / 10.0}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
