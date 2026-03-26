package com.sleepyio.sleepyio.ui.analysis

import androidx.compose.foundation.clickable
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
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.model.UnifiedPlayer
import com.sleepyio.sleepyio.service.SleeperService
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class RosterForTrade(
    val teamName: String,
    val rosterId: Int,
    val players: List<UnifiedPlayer>
)

@Composable
fun TradeAnalyzerScreen(
    leagueId: Long,
    modifier: Modifier = Modifier
) {
    var rosters by remember { mutableStateOf<List<RosterForTrade>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTeam1 by remember { mutableStateOf<RosterForTrade?>(null) }
    var selectedTeam2 by remember { mutableStateOf<RosterForTrade?>(null) }
    var side1Players by remember { mutableStateOf<Set<String>>(emptySet()) }
    var side2Players by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tradeResult by remember { mutableStateOf<SleeperService.TradeAnalysis?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(leagueId) {
        scope.launch {
            isLoading = true
            val nflState = SleeperClient.getNflState()
            val season = nflState?.season ?: "2025"

            val rostersDeferred = scope.async { SleeperClient.getRostersInLeague(leagueId) }
            val usersDeferred = scope.async { SleeperClient.getUsersInLeague(leagueId) }
            val projectionsDeferred = scope.async { SleeperClient.getSeasonProjections(season) }

            val rawRosters = rostersDeferred.await()
            val users = usersDeferred.await()
            val projections = projectionsDeferred.await()

            val userMap = users.associateBy { it.userId }

            rosters = rawRosters.map { roster ->
                val user = roster.ownerId?.let { userMap[it] }
                val teamName = user?.displayName ?: user?.userName ?: "Team ${roster.rosterId}"

                val players = roster.players.mapNotNull { playerId ->
                    val player = SleeperCache.getPlayer(playerId) ?: return@mapNotNull null
                    val proj = projections[playerId]?.fantasyPoints
                    UnifiedPlayer(
                        id = playerId,
                        name = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim(),
                        position = player.position ?: "N/A",
                        team = player.team,
                        isStarter = playerId in roster.starters,
                        projectedPoints = proj,
                        injuryStatus = player.injuryStatus
                    )
                }.sortedByDescending { it.projectedPoints ?: 0.0 }

                RosterForTrade(teamName, roster.rosterId, players)
            }

            isLoading = false
        }
    }

    // Analyze trade whenever selections change
    LaunchedEffect(side1Players, side2Players) {
        if (side1Players.isNotEmpty() && side2Players.isNotEmpty()) {
            val team1 = selectedTeam1 ?: return@LaunchedEffect
            val team2 = selectedTeam2 ?: return@LaunchedEffect
            val s1 = team1.players.filter { it.id in side1Players }
            val s2 = team2.players.filter { it.id in side2Players }
            tradeResult = SleeperService.analyzeTrade(s1, s2)
        } else {
            tradeResult = null
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Trade Analyzer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        // Trade result banner
        tradeResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        result.verdict.contains("Heavily") -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        result.verdict == "Fair trade" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(result.verdict, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            "Side 1: ${(result.side1ProjectedTotal * 10).toInt() / 10.0} pts",
                            fontSize = 13.sp
                        )
                        Text(
                            "Side 2: ${(result.side2ProjectedTotal * 10).toInt() / 10.0} pts",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Team selectors
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeamDropdown(
                label = "Team 1",
                teams = rosters,
                selected = selectedTeam1,
                onSelect = { selectedTeam1 = it; side1Players = emptySet() },
                modifier = Modifier.weight(1f)
            )
            TeamDropdown(
                label = "Team 2",
                teams = rosters.filter { it.rosterId != selectedTeam1?.rosterId },
                selected = selectedTeam2,
                onSelect = { selectedTeam2 = it; side2Players = emptySet() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Player selection
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Team 1 players
            Column(modifier = Modifier.weight(1f)) {
                Text("${selectedTeam1?.teamName ?: "Select Team 1"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(selectedTeam1?.players ?: emptyList()) { player ->
                        val isSelected = player.id in side1Players
                        TradePlayerRow(player, isSelected) {
                            side1Players = if (isSelected) side1Players - player.id else side1Players + player.id
                        }
                    }
                }
            }

            // Team 2 players
            Column(modifier = Modifier.weight(1f)) {
                Text("${selectedTeam2?.teamName ?: "Select Team 2"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(selectedTeam2?.players ?: emptyList()) { player ->
                        val isSelected = player.id in side2Players
                        TradePlayerRow(player, isSelected) {
                            side2Players = if (isSelected) side2Players - player.id else side2Players + player.id
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamDropdown(
    label: String,
    teams: List<RosterForTrade>,
    selected: RosterForTrade?,
    onSelect: (RosterForTrade) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.teamName ?: label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teams.forEach { team ->
                DropdownMenuItem(
                    text = { Text(team.teamName, fontSize = 12.sp) },
                    onClick = { onSelect(team); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun TradePlayerRow(player: UnifiedPlayer, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(player.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("${player.position} - ${player.team ?: "FA"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${((player.projectedPoints ?: 0.0) * 10).toInt() / 10.0}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
