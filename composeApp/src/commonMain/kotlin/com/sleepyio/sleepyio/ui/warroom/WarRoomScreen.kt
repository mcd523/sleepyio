package com.sleepyio.sleepyio.ui.warroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.OpponentRepository
import com.sleepyio.sleepyio.intel.model.ActivityType
import com.sleepyio.sleepyio.intel.model.OpponentLeague
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// ---------- State models ----------

private data class OpponentSummary(
    val user: SleeperUser,
    val sharedCount: Int,
    val shadowCount: Int,
    val threatScore: Int,
    val intelChips: List<String>,
    val isThisWeeksOpponent: Boolean
)

private data class WarRoomState(
    val opponents: List<OpponentSummary> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val shadowEvents: List<ShadowFeedEvent> = emptyList(),
    val isDiscovering: Boolean = true,
    val isFeedLoading: Boolean = true,
    val discoveredCount: Int = 0,
    val totalCount: Int = 0
)

// ---------- Screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarRoomScreen(
    user: SleeperUser,
    leagues: List<SleeperLeague>,
    currentSeason: String,
    onSeasonChanged: (String) -> Unit,
    onLeagueSelected: (SleeperLeague) -> Unit,
    onOpponentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var devWeek by remember { mutableStateOf<Int?>(null) }
    var state by remember { mutableStateOf(WarRoomState()) }

    // Discover opponents on composition
    LaunchedEffect(user.userId, leagues) {
        val userId = user.userId ?: return@LaunchedEffect
        if (leagues.isEmpty()) {
            state = state.copy(isDiscovering = false, isFeedLoading = false)
            return@LaunchedEffect
        }

        val repo = OpponentRepository(userId)
        state = state.copy(isDiscovering = true, isFeedLoading = true)

        try {
            val opponentMap = repo.discoverAllOpponents(leagues.map { it.leagueId })

            // Find this week's opponents across all leagues
            val thisWeeksOpponentIds = mutableSetOf<String>()
            for (league in leagues) {
                try {
                    val opponent = repo.getCurrentMatchupOpponent(league.leagueId)
                    opponent?.userId?.let { thisWeeksOpponentIds.add(it) }
                } catch (_: Exception) { /* ignore */ }
            }

            // Build summaries
            val summaries = coroutineScope {
                opponentMap.map { (opponentId, opponentLeagues) ->
                    async {
                        val sharedCount = opponentLeagues.count { it.isShared }
                        val shadowCount = opponentLeagues.count { !it.isShared }
                        val isThisWeek = opponentId in thisWeeksOpponentIds

                        // Compute threat score
                        val threatScore = computeThreatScore(
                            sharedCount = sharedCount,
                            shadowCount = shadowCount,
                            isThisWeek = isThisWeek,
                            totalLeagues = opponentLeagues.size
                        )

                        // Build intel chips
                        val chips = buildIntelChips(
                            sharedCount = sharedCount,
                            shadowCount = shadowCount,
                            isThisWeek = isThisWeek
                        )

                        // Resolve opponent user info
                        val opponentUser = try {
                            SleeperClient.getUserById(opponentId) ?: SleeperUser(
                                userId = opponentId,
                                displayName = "User $opponentId"
                            )
                        } catch (_: Exception) {
                            SleeperUser(userId = opponentId, displayName = "User $opponentId")
                        }

                        OpponentSummary(
                            user = opponentUser,
                            sharedCount = sharedCount,
                            shadowCount = shadowCount,
                            threatScore = threatScore,
                            intelChips = chips,
                            isThisWeeksOpponent = isThisWeek
                        )
                    }
                }.awaitAll()
            }.sortedWith(
                compareByDescending<OpponentSummary> { it.isThisWeeksOpponent }
                    .thenByDescending { it.threatScore }
            )

            // Build alerts
            val alerts = buildAlerts(summaries, thisWeeksOpponentIds)

            // Build shadow feed events from opponent leagues
            val shadowEvents = buildShadowEvents(opponentMap, repo)

            state = state.copy(
                opponents = summaries,
                alerts = alerts,
                shadowEvents = shadowEvents,
                isDiscovering = false,
                isFeedLoading = false,
                discoveredCount = summaries.size,
                totalCount = summaries.size
            )
        } catch (e: Exception) {
            state = state.copy(
                isDiscovering = false,
                isFeedLoading = false,
                alerts = listOf(
                    Alert(
                        message = "Discovery failed: ${e.message}",
                        severity = AlertSeverity.HIGH,
                        timestamp = 0L
                    )
                )
            )
        }
    }

    val configuration = LocalWindowInfo.current
    val screenWidth = configuration.containerSize.width
    val isDesktop = screenWidth >= 1200
    val isTablet = screenWidth in 800 until 1200
    val isMobile = screenWidth < 800

    Column(modifier = modifier.fillMaxSize()) {
        // TopAppBar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "WAR ROOM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (state.isDiscovering) {
                        Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                        Text(
                            text = "Scanning...",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(SleeperSpacing.xs))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // DevToolbar
        DevToolbar(
            currentSeason = currentSeason,
            onSeasonChanged = onSeasonChanged,
            currentWeek = devWeek,
            onWeekChanged = { devWeek = it }
        )

        // Alert banner
        if (state.alerts.isNotEmpty()) {
            AlertBanner(
                alerts = state.alerts,
                modifier = Modifier.padding(
                    horizontal = SleeperSpacing.md,
                    vertical = SleeperSpacing.sm
                )
            )
        }

        // Responsive layout
        when {
            isDesktop -> DesktopLayout(state, onOpponentSelected)
            isTablet -> TabletLayout(state, onOpponentSelected)
            isMobile -> MobileLayout(state, onOpponentSelected)
        }
    }
}

// ---------- Responsive layouts ----------

@Composable
private fun DesktopLayout(state: WarRoomState, onOpponentSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SleeperSpacing.md)
    ) {
        // Opponent grid
        OpponentGrid(
            opponents = state.opponents,
            isLoading = state.isDiscovering,
            onOpponentSelected = onOpponentSelected,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(SleeperSpacing.md))

        // Shadow feed
        ShadowActivityFeed(
            events = state.shadowEvents,
            isLoading = state.isFeedLoading,
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun TabletLayout(state: WarRoomState, onOpponentSelected: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SleeperSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.md)
    ) {
        item {
            OpponentSection(
                opponents = state.opponents,
                isLoading = state.isDiscovering,
                onOpponentSelected = onOpponentSelected
            )
        }

        item {
            ShadowActivityFeed(
                events = state.shadowEvents,
                isLoading = state.isFeedLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MobileLayout(state: WarRoomState, onOpponentSelected: (String) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Opponents", "Feed")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = SleeperType.body
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> OpponentList(
                opponents = state.opponents,
                isLoading = state.isDiscovering,
                onOpponentSelected = onOpponentSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SleeperSpacing.md)
            )
            1 -> ShadowActivityFeed(
                events = state.shadowEvents,
                isLoading = state.isFeedLoading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SleeperSpacing.md)
            )
        }
    }
}

// ---------- Opponent display components ----------

@Composable
private fun OpponentGrid(
    opponents: List<OpponentSummary>,
    isLoading: Boolean,
    onOpponentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Opponents",
                fontSize = SleeperType.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (opponents.isNotEmpty()) {
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    text = "${opponents.size} discovered",
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        if (isLoading && opponents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                    Text("Discovering opponents...", fontSize = SleeperType.body)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
            ) {
                items(opponents) { opponent ->
                    OpponentCard(
                        user = opponent.user,
                        sharedLeagueCount = opponent.sharedCount,
                        shadowLeagueCount = opponent.shadowCount,
                        threatScore = opponent.threatScore,
                        intelChips = opponent.intelChips,
                        isThisWeeksOpponent = opponent.isThisWeeksOpponent,
                        onClick = { opponent.user.userId?.let(onOpponentSelected) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpponentSection(
    opponents: List<OpponentSummary>,
    isLoading: Boolean,
    onOpponentSelected: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Opponents",
                fontSize = SleeperType.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (opponents.isNotEmpty()) {
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    text = "${opponents.size} discovered",
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        if (isLoading && opponents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SleeperSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                    Text("Discovering opponents...", fontSize = SleeperType.body)
                }
            }
        } else {
            opponents.forEach { opponent ->
                OpponentCard(
                    user = opponent.user,
                    sharedLeagueCount = opponent.sharedCount,
                    shadowLeagueCount = opponent.shadowCount,
                    threatScore = opponent.threatScore,
                    intelChips = opponent.intelChips,
                    isThisWeeksOpponent = opponent.isThisWeeksOpponent,
                    onClick = { opponent.user.userId?.let(onOpponentSelected) },
                    modifier = Modifier.padding(bottom = SleeperSpacing.sm)
                )
            }
        }
    }
}

@Composable
private fun OpponentList(
    opponents: List<OpponentSummary>,
    isLoading: Boolean,
    onOpponentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading && opponents.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                Text("Discovering opponents...", fontSize = SleeperType.body)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
        ) {
            items(opponents) { opponent ->
                OpponentCard(
                    user = opponent.user,
                    sharedLeagueCount = opponent.sharedCount,
                    shadowLeagueCount = opponent.shadowCount,
                    threatScore = opponent.threatScore,
                    intelChips = opponent.intelChips,
                    isThisWeeksOpponent = opponent.isThisWeeksOpponent,
                    onClick = { opponent.user.userId?.let(onOpponentSelected) }
                )
            }
        }
    }
}

// ---------- DevToolbar (reused from HomeShellScreen) ----------

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
                    contentPadding = PaddingValues(
                        horizontal = SleeperSpacing.sm,
                        vertical = SleeperSpacing.xxs
                    )
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
                    contentPadding = PaddingValues(
                        horizontal = SleeperSpacing.sm,
                        vertical = SleeperSpacing.xxs
                    )
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

// ---------- Helper functions ----------

private fun computeThreatScore(
    sharedCount: Int,
    shadowCount: Int,
    isThisWeek: Boolean,
    totalLeagues: Int
): Int {
    var score = 0

    // Base score from league overlap
    score += (sharedCount * 15).coerceAtMost(45)

    // Shadow league presence adds intrigue
    score += (shadowCount * 5).coerceAtMost(20)

    // This week's opponent gets a boost
    if (isThisWeek) score += 25

    // Multi-league exposure
    if (totalLeagues >= 3) score += 10

    return score.coerceIn(0, 100)
}

private fun buildIntelChips(
    sharedCount: Int,
    shadowCount: Int,
    isThisWeek: Boolean
): List<String> {
    val chips = mutableListOf<String>()
    if (isThisWeek) chips.add("RIVAL")
    if (shadowCount > 0) chips.add("SHADOW")
    if (sharedCount >= 2) chips.add("CONVERGENT")
    return chips
}

private fun buildAlerts(
    opponents: List<OpponentSummary>,
    thisWeeksOpponentIds: Set<String>
): List<Alert> {
    val alerts = mutableListOf<Alert>()

    // Alert for this week's opponents
    val thisWeekOpponents = opponents.filter { it.isThisWeeksOpponent }
    if (thisWeekOpponents.isNotEmpty()) {
        val names = thisWeekOpponents.mapNotNull { it.user.displayName ?: it.user.userName }
        alerts.add(
            Alert(
                message = "Facing ${names.joinToString(", ")} this week across ${thisWeekOpponents.sumOf { it.sharedCount }} leagues",
                severity = AlertSeverity.HIGH,
                timestamp = 0L
            )
        )
    }

    // Alert for high-threat opponents not faced this week
    val highThreat = opponents.filter { !it.isThisWeeksOpponent && it.threatScore > 60 }
    if (highThreat.isNotEmpty()) {
        alerts.add(
            Alert(
                message = "${highThreat.size} high-threat opponents detected in your leagues",
                severity = AlertSeverity.MEDIUM,
                timestamp = 0L
            )
        )
    }

    // Shadow league intel
    val shadowOpponents = opponents.filter { it.shadowCount > 0 }
    if (shadowOpponents.isNotEmpty()) {
        alerts.add(
            Alert(
                message = "${shadowOpponents.size} opponents found in ${shadowOpponents.sumOf { it.shadowCount }} shadow leagues",
                severity = AlertSeverity.LOW,
                timestamp = 0L
            )
        )
    }

    return alerts
}

private suspend fun buildShadowEvents(
    opponentMap: Map<String, List<OpponentLeague>>,
    repo: OpponentRepository
): List<ShadowFeedEvent> {
    val events = mutableListOf<ShadowFeedEvent>()

    // Collect recent transactions from shadow leagues for known opponents
    for ((opponentId, leagues) in opponentMap) {
        val shadowLeagues = leagues.filter { !it.isShared }
        for (opponentLeague in shadowLeagues.take(3)) { // limit to 3 shadow leagues per opponent
            try {
                val transactions = repo.getAllTransactions(opponentLeague.league.leagueId)
                val opponentUser = try {
                    SleeperClient.getUserById(opponentId)
                } catch (_: Exception) { null }
                val userName = opponentUser?.displayName ?: opponentUser?.userName ?: "Unknown"

                // Find this opponent's roster
                val rosters = repo.getRosters(opponentLeague.league.leagueId)
                val opponentRosterIds = rosters
                    .filter { it.ownerId == opponentId }
                    .map { it.rosterId }
                    .toSet()

                val opponentTxs = transactions
                    .filter { tx -> tx.rosterIds.any { it in opponentRosterIds } }
                    .sortedByDescending { it.updatedTime }
                    .take(5) // limit per league

                for (tx in opponentTxs) {
                    val type = when (tx.type) {
                        "trade" -> ActivityType.TRADE
                        "waiver" -> ActivityType.WAIVER_CLAIM
                        "free_agent" -> ActivityType.FREE_AGENT_ADD
                        else -> ActivityType.FREE_AGENT_ADD
                    }

                    val description = buildTransactionDescription(tx)
                    val annotation = if (leagues.any { it.isShared }) "Also in your league" else null

                    events.add(
                        ShadowFeedEvent(
                            type = type,
                            userName = userName,
                            leagueName = opponentLeague.league.leagueName,
                            isShadowLeague = true,
                            description = description,
                            annotation = annotation,
                            timestamp = tx.updatedTime
                        )
                    )
                }
            } catch (_: Exception) { /* skip on error */ }
        }
    }

    return events.sortedByDescending { it.timestamp }.take(50)
}

private fun buildTransactionDescription(tx: com.sleepyio.sleepyio.client.model.league.SleeperTransaction): String {
    val adds = tx.adds?.keys?.toList() ?: emptyList()
    val drops = tx.drops?.keys?.toList() ?: emptyList()

    return when (tx.type) {
        "trade" -> "Trade involving ${adds.size + drops.size} players"
        "waiver" -> {
            val added = if (adds.isNotEmpty()) "claimed ${adds.size} player(s)" else ""
            val dropped = if (drops.isNotEmpty()) "dropped ${drops.size} player(s)" else ""
            listOf(added, dropped).filter { it.isNotEmpty() }.joinToString(", ")
        }
        else -> {
            val added = if (adds.isNotEmpty()) "added ${adds.size} player(s)" else ""
            val dropped = if (drops.isNotEmpty()) "dropped ${drops.size} player(s)" else ""
            listOf(added, dropped).filter { it.isNotEmpty() }.joinToString(", ")
        }
    }
}

