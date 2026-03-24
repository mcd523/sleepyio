# Cross-League Feed & Navigation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the league-first navigation with a social-first home screen that aggregates activity from all user leagues into a unified feed.

**Architecture:** New `HomeShellScreen` becomes the post-login landing with two tabs (Feed/Leagues). Feed aggregates transactions, matchup results, and draft picks across all leagues using parallel coroutine fetches with progressive rendering. Entering a league pushes `LeagueShellScreen` as a full-screen overlay with back navigation.

**Tech Stack:** Kotlin Multiplatform Compose, Ktor (existing), kotlinx.serialization, kotlinx.coroutines (flow/channels for progressive loading)

---

## File Map

| File | Responsibility |
|------|---------------|
| **NEW** `ui/home/FeedEvent.kt` | Sealed class for unified feed events — wraps transactions, matchup results, draft picks with league context |
| **NEW** `ui/home/FeedDataLoader.kt` | Coroutine-based data aggregator — parallel per-league fetches, merges into shared state, progressive emission |
| **NEW** `ui/home/CrossLeagueFeedScreen.kt` | Feed tab UI — filter chips, week grouping, event cards, skeleton loading |
| **NEW** `ui/home/HomeShellScreen.kt` | Two-tab shell (Feed/Leagues) with responsive NavigationBar/Rail |
| **MODIFY** `App.kt` | New 3-state enum, rewired navigation, league list fetched after login |
| **MODIFY** `ui/shell/LeagueShellScreen.kt` | Update back button label from "Leagues" to "Home" |
| **MODIFY** `LeagueTable.kt` | Accept optional pre-fetched leagues list to avoid redundant API calls |

---

### Task 1: FeedEvent Data Model

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/FeedEvent.kt`

- [ ] **Step 1: Create the FeedEvent sealed class**

```kotlin
package com.sleepyio.sleepyio.ui.home

import com.sleepyio.sleepyio.client.model.draft.SleeperPick
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.intel.model.ActivityEvent

sealed class FeedEvent : Comparable<FeedEvent> {
    abstract val leagueId: Long
    abstract val leagueName: String
    abstract val timestamp: Long
    abstract val week: Int

    override fun compareTo(other: FeedEvent): Int =
        other.timestamp.compareTo(this.timestamp) // descending

    data class Transaction(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val event: ActivityEvent
    ) : FeedEvent()

    data class MatchupResult(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val userScore: Float,
        val opponentScore: Float,
        val opponentName: String,
        val won: Boolean,
        val margin: Float
    ) : FeedEvent()

    data class DraftPickEvent(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val player: SleeperPlayer?,
        val playerId: String,
        val round: Long,
        val pickNumber: Long,
        val pickedByName: String
    ) : FeedEvent()

    data class CommissionerAction(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val description: String
    ) : FeedEvent()
}

enum class FeedFilter(val label: String) {
    ALL("All"),
    TRADES("Trades"),
    WAIVERS("Waivers"),
    MATCHUPS("Matchups"),
    DRAFTS("Drafts"),
    COMMISSIONER("Commissioner")
}

fun FeedEvent.matchesFilter(filter: FeedFilter): Boolean = when (filter) {
    FeedFilter.ALL -> true
    FeedFilter.TRADES -> this is FeedEvent.Transaction && this.event.type == com.sleepyio.sleepyio.intel.model.ActivityType.TRADE
    FeedFilter.WAIVERS -> this is FeedEvent.Transaction && this.event.type == com.sleepyio.sleepyio.intel.model.ActivityType.WAIVER_CLAIM
    FeedFilter.MATCHUPS -> this is FeedEvent.MatchupResult
    FeedFilter.DRAFTS -> this is FeedEvent.DraftPickEvent
    FeedFilter.COMMISSIONER -> this is FeedEvent.CommissionerAction
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/FeedEvent.kt
git commit -m "feat: add FeedEvent sealed class for cross-league feed data model"
```

---

### Task 2: FeedDataLoader — Progressive Data Aggregation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/FeedDataLoader.kt`

This component fetches data from all leagues in parallel and progressively emits results as each league completes. It reuses the same transaction-to-ActivityEvent parsing logic from `ActivityFeedScreen`.

- [ ] **Step 1: Create the FeedDataLoader**

```kotlin
package com.sleepyio.sleepyio.ui.home

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.model.ActivityEvent
import com.sleepyio.sleepyio.intel.model.ActivityType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FeedState(
    val events: List<FeedEvent> = emptyList(),
    val leaguesLoaded: Int = 0,
    val leaguesTotal: Int = 0,
    val isComplete: Boolean = false,
    val errors: List<String> = emptyList()
)

/**
 * Progressively loads feed events from all leagues.
 * Emits updated FeedState as each league completes loading.
 */
fun loadFeed(
    leagues: List<SleeperLeague>,
    user: SleeperUser
): Flow<FeedState> = channelFlow {
    val allEvents = mutableListOf<FeedEvent>()
    val errors = mutableListOf<String>()
    var loaded = 0
    val mutex = Mutex()

    send(FeedState(leaguesTotal = leagues.size))

    // Fetch NFL state once for week context
    val nflState = try {
        SleeperClient.getNflState()
    } catch (_: Exception) { null }
    val currentWeek = nflState?.week?.toInt() ?: 1

    // Process leagues in parallel, emit after each completes
    coroutineScope {
        val jobs = leagues.map { league ->
            async {
                try {
                    val leagueEvents = loadLeagueEvents(league, user, currentWeek)
                    mutex.withLock {
                        allEvents.addAll(leagueEvents)
                        loaded++
                    }
                    send(FeedState(
                        events = mutex.withLock { allEvents.sortedDescending() },
                        leaguesLoaded = loaded,
                        leaguesTotal = leagues.size,
                        isComplete = loaded == leagues.size
                    ))
                } catch (e: Exception) {
                    mutex.withLock {
                        errors.add("${league.leagueName}: ${e.message}")
                        loaded++
                    }
                    send(FeedState(
                        events = mutex.withLock { allEvents.sortedDescending() },
                        leaguesLoaded = loaded,
                        leaguesTotal = leagues.size,
                        isComplete = loaded == leagues.size,
                        errors = mutex.withLock { errors.toList() }
                    ))
                }
            }
        }
        jobs.awaitAll()
    }
}

private suspend fun loadLeagueEvents(
    league: SleeperLeague,
    user: SleeperUser,
    currentWeek: Int
): List<FeedEvent> {
    val events = mutableListOf<FeedEvent>()

    // Fetch transactions
    val transactions = SleeperClient.getAllTransactionsInSeason(league.leagueId)
    val rosters = SleeperClient.getRostersInLeague(league.leagueId)
    val users = SleeperClient.getUsersInLeague(league.leagueId)

    val userMap = users.associateBy { it.userId }
    val rosterToUser = mutableMapOf<Int, SleeperUser>()
    for (roster in rosters) {
        roster.ownerId?.let { ownerId ->
            userMap[ownerId]?.let { u -> rosterToUser[roster.rosterId] = u }
        }
    }

    // Convert transactions to FeedEvents (same logic as ActivityFeedScreen)
    for (tx in transactions.filter { it.status == "complete" }) {
        val primaryRosterId = tx.rosterIds.firstOrNull() ?: continue
        val primaryUser = rosterToUser[primaryRosterId] ?: continue
        val userId = primaryUser.userId ?: continue
        val userName = primaryUser.displayName ?: primaryUser.userName ?: "Unknown"

        val addedPlayers = tx.adds?.keys?.mapNotNull { SleeperCache.getPlayer(it) } ?: emptyList()
        val droppedPlayers = tx.drops?.keys?.mapNotNull { SleeperCache.getPlayer(it) } ?: emptyList()

        val type = when (tx.type) {
            "trade" -> ActivityType.TRADE
            "waiver" -> ActivityType.WAIVER_CLAIM
            "free_agent" -> ActivityType.FREE_AGENT_ADD
            else -> continue
        }

        val tradePartner = if (type == ActivityType.TRADE && tx.rosterIds.size > 1) {
            rosterToUser[tx.rosterIds[1]]
        } else null

        events.add(FeedEvent.Transaction(
            leagueId = league.leagueId,
            leagueName = league.leagueName,
            timestamp = tx.createdTime,
            week = tx.week,
            event = ActivityEvent(
                type = type,
                userId = userId,
                userName = userName,
                week = tx.week,
                timestamp = tx.createdTime,
                playersAdded = addedPlayers,
                playersDropped = droppedPlayers,
                tradePartnerUserId = tradePartner?.userId,
                tradePartnerName = tradePartner?.displayName ?: tradePartner?.userName
            )
        ))
    }

    // Fetch matchup results for completed weeks
    val userRoster = rosters.find { it.ownerId == user.userId }
    if (userRoster != null) {
        for (week in 1 until currentWeek) {
            try {
                val matchups = SleeperClient.getMatchupsInLeague(league.leagueId, week)
                val myMatchup = matchups.find { it.rosterId == userRoster.rosterId.toLong() }
                if (myMatchup?.matchupId != null) {
                    val opponent = matchups.find {
                        it.matchupId == myMatchup.matchupId && it.rosterId != userRoster.rosterId.toLong()
                    }
                    if (opponent != null) {
                        val opponentUser = rosterToUser[opponent.rosterId.toInt()]
                        val won = myMatchup.points > opponent.points
                        events.add(FeedEvent.MatchupResult(
                            leagueId = league.leagueId,
                            leagueName = league.leagueName,
                            timestamp = 0L, // Matchups don't have timestamps; sort by week
                            week = week,
                            userScore = myMatchup.points,
                            opponentScore = opponent.points,
                            opponentName = opponentUser?.displayName ?: opponentUser?.userName ?: "Unknown",
                            won = won,
                            margin = kotlin.math.abs(myMatchup.points - opponent.points)
                        ))
                    }
                }
            } catch (_: Exception) { /* skip week if fetch fails */ }
        }
    }

    // Fetch draft picks
    try {
        val drafts = SleeperClient.getDraftsForLeague(league.leagueId)
        for (draft in drafts.filter { it.status == "complete" }) {
            val picks = SleeperClient.getDraftPicks(draft.draftId)
            for (pick in picks) {
                val drafter = rosterToUser[pick.rosterId.toIntOrNull() ?: continue]
                events.add(FeedEvent.DraftPickEvent(
                    leagueId = league.leagueId,
                    leagueName = league.leagueName,
                    timestamp = draft.startTime,
                    week = 0, // Pre-season
                    player = SleeperCache.getPlayer(pick.playerId),
                    playerId = pick.playerId,
                    round = pick.round,
                    pickNumber = pick.pickNumber,
                    pickedByName = drafter?.displayName ?: drafter?.userName ?: "Unknown"
                ))
            }
        }
    } catch (_: Exception) { /* skip drafts if fetch fails */ }

    // Commissioner actions: Sleeper API doesn't expose these as a dedicated endpoint.
    // Transactions with type "commissioner" are captured above if present.
    // We'll skip dedicated commissioner events for now — can be added if the API supports it.

    return events
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. `ActivityEvent` and `ActivityType` are already public classes at `intel/model/ActivityEvent.kt`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/FeedDataLoader.kt
git commit -m "feat: add FeedDataLoader for progressive cross-league data aggregation"
```

---

### Task 3: CrossLeagueFeedScreen — Feed Tab UI

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/CrossLeagueFeedScreen.kt`

This screen renders the aggregated feed with filter chips, week grouping, and event cards. It follows the same patterns as `ActivityFeedScreen` but with league context badges and additional event types.

- [ ] **Step 1: Create CrossLeagueFeedScreen composable**

```kotlin
package com.sleepyio.sleepyio.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.model.ActivityType

// Colors matching the spec mockup
private val TradeColor = Color(0xFFFF9800)
private val WaiverColor = Color(0xFF1ABC9C)
private val MatchupColor = Color(0xFF5A67D8)
private val FreeAgentColor = Color(0xFF4CAF50)
private val DraftColor = Color(0xFF2196F3)
private val CommissionerColor = Color(0xFFB794F4)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrossLeagueFeedScreen(
    leagues: List<SleeperLeague>,
    user: SleeperUser,
    onLeagueClick: (SleeperLeague) -> Unit,
    modifier: Modifier = Modifier
) {
    var feedState by remember { mutableStateOf(FeedState()) }
    var selectedFilter by remember { mutableStateOf(FeedFilter.ALL) }

    // Progressive loading
    LaunchedEffect(leagues, user) {
        loadFeed(leagues, user).collect { state ->
            feedState = state
        }
    }

    val filteredEvents = feedState.events.filter { it.matchesFilter(selectedFilter) }
    val groupedByWeek = filteredEvents.groupBy { it.week }.toSortedMap(compareByDescending { it })

    Column(modifier = modifier.fillMaxSize().padding(SleeperSpacing.md)) {
        // Title with loading indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activity Feed", fontSize = SleeperType.titleLarge, fontWeight = FontWeight.Bold)
            if (!feedState.isComplete && feedState.leaguesTotal > 0) {
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                Text(
                    "${feedState.leaguesLoaded}/${feedState.leaguesTotal} leagues",
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

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        // Filter chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs)) {
            FeedFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label, fontSize = SleeperType.caption) }
                )
            }
        }

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        when {
            feedState.leaguesLoaded == 0 && !feedState.isComplete -> {
                // Skeleton / initial loading
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                        Text("Loading your leagues...", fontSize = SleeperType.body)
                    }
                }
            }
            filteredEvents.isEmpty() && feedState.isComplete -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedFilter == FeedFilter.ALL) "No activity found across your leagues"
                        else "No ${selectedFilter.label.lowercase()} found",
                        fontSize = SleeperType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)) {
                    groupedByWeek.forEach { (week, weekEvents) ->
                        item {
                            Text(
                                text = if (week == 0) "PRE-SEASON" else "WEEK $week",
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = SleeperSpacing.sm)
                            )
                        }
                        items(weekEvents) { event ->
                            FeedEventCard(
                                event = event,
                                onLeagueClick = {
                                    leagues.find { l -> l.leagueId == event.leagueId }
                                        ?.let(onLeagueClick)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedEventCard(
    event: FeedEvent,
    onLeagueClick: () -> Unit
) {
    val (borderColor, typeLabel) = when (event) {
        is FeedEvent.Transaction -> when (event.event.type) {
            ActivityType.TRADE -> TradeColor to "🔄 TRADE"
            ActivityType.WAIVER_CLAIM -> WaiverColor to "📋 WAIVER"
            ActivityType.FREE_AGENT_ADD -> FreeAgentColor to "➕ FREE AGENT"
            ActivityType.DROP -> Color(0xFFEF5350) to "⬇️ DROP"
        }
        is FeedEvent.MatchupResult -> MatchupColor to "🏈 MATCHUP"
        is FeedEvent.DraftPickEvent -> DraftColor to "📝 DRAFT"
        is FeedEvent.CommissionerAction -> CommissionerColor to "⚙️ COMMISSIONER"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Color accent bar
            Surface(
                modifier = Modifier.width(3.dp).defaultMinSize(minHeight = 60.dp),
                color = borderColor
            ) {}

            Column(modifier = Modifier.padding(SleeperSpacing.sm + SleeperSpacing.xs)) {
                // Header: type label + league badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        typeLabel,
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = borderColor
                    )
                    Text(
                        event.leagueName,
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onLeagueClick() }
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.xs))

                // Event-specific content
                when (event) {
                    is FeedEvent.Transaction -> TransactionContent(event)
                    is FeedEvent.MatchupResult -> MatchupContent(event)
                    is FeedEvent.DraftPickEvent -> DraftContent(event)
                    is FeedEvent.CommissionerAction -> Text(event.description, fontSize = SleeperType.body)
                }
            }
        }
    }
}

@Composable
private fun TransactionContent(event: FeedEvent.Transaction) {
    val tx = event.event
    when (tx.type) {
        ActivityType.TRADE -> {
            Text(
                "${tx.userName} traded with ${tx.tradePartnerName ?: "unknown"}",
                fontSize = SleeperType.body
            )
            Spacer(modifier = Modifier.height(SleeperSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
            ) {
                // Received
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A1E))
                ) {
                    Column(modifier = Modifier.padding(SleeperSpacing.sm)) {
                        Text("RECEIVED", fontSize = 9.sp, color = Color(0xFF68D391))
                        tx.playersAdded.forEach { player ->
                            Text(
                                "${player.firstName?.first() ?: ""}. ${player.lastName ?: "Unknown"} · ${player.position ?: ""}",
                                fontSize = SleeperType.caption
                            )
                        }
                    }
                }
                // Sent
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1E1E))
                ) {
                    Column(modifier = Modifier.padding(SleeperSpacing.sm)) {
                        Text("SENT", fontSize = 9.sp, color = Color(0xFFFC8181))
                        tx.playersDropped.forEach { player ->
                            Text(
                                "${player.firstName?.first() ?: ""}. ${player.lastName ?: "Unknown"} · ${player.position ?: ""}",
                                fontSize = SleeperType.caption
                            )
                        }
                    }
                }
            }
        }
        else -> {
            if (tx.playersAdded.isNotEmpty()) {
                Text(
                    "${tx.userName} added ${tx.playersAdded.joinToString { "${it.firstName?.first() ?: ""}. ${it.lastName ?: "Unknown"}" }}",
                    fontSize = SleeperType.body
                )
            }
            if (tx.playersDropped.isNotEmpty()) {
                Text(
                    "Dropped ${tx.playersDropped.joinToString { "${it.firstName?.first() ?: ""}. ${it.lastName ?: "Unknown"}" }}",
                    fontSize = SleeperType.body,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MatchupContent(event: FeedEvent.MatchupResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
    ) {
        Text(
            if (event.won) "W" else "L",
            fontWeight = FontWeight.Bold,
            color = if (event.won) Color(0xFF68D391) else Color(0xFFFC8181),
            fontSize = SleeperType.body
        )
        Text(
            "%.1f".format(event.userScore),
            fontWeight = FontWeight.SemiBold,
            color = if (event.won) Color(0xFF68D391) else Color(0xFFFC8181),
            fontSize = SleeperType.body
        )
        Text("vs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = SleeperType.body)
        Text(
            "%.1f".format(event.opponentScore),
            fontSize = SleeperType.body
        )
        Text(event.opponentName, fontSize = SleeperType.body)
        if (event.margin < 10f) {
            Text(
                "🔥 Close game!",
                fontSize = SleeperType.caption,
                color = TradeColor
            )
        }
    }
}

@Composable
private fun DraftContent(event: FeedEvent.DraftPickEvent) {
    val playerName = event.player?.let {
        "${it.firstName ?: ""} ${it.lastName ?: "Unknown"}"
    } ?: "Player ${event.playerId}"
    val position = event.player?.position ?: ""

    Text(
        "${event.pickedByName} drafted $playerName · $position (Rd ${event.round}, Pick ${event.pickNumber})",
        fontSize = SleeperType.body
    )
}
```

**Note:** The `9.sp` import will need `import androidx.compose.ui.unit.sp` — make sure it's included in the imports.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. Fix any missing imports.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/CrossLeagueFeedScreen.kt
git commit -m "feat: add CrossLeagueFeedScreen with filter chips and event cards"
```

---

### Task 4: HomeShellScreen — Two-Tab Home Container

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/HomeShellScreen.kt`

Follows the same responsive NavigationBar/Rail pattern as `LeagueShellScreen`.

- [ ] **Step 1: Create HomeShellScreen composable**

```kotlin
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
            onLeagueClick = onLeagueSelected
        )
    }
}
```

**Implementation note:** This uses `LocalWindowInfo.current.containerSize.width >= 800` — the same pixel-based approach as `LeagueShellScreen` — to ensure consistent breakpoint behavior between the two shells.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. If `LeagueTable` doesn't match the expected signature, adjust the call.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/HomeShellScreen.kt
git commit -m "feat: add HomeShellScreen with Feed and Leagues tabs"
```

---

### Task 5: Update LeagueTable to Accept Pre-Fetched Leagues

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/LeagueTable.kt`

Currently `LeagueTable` fetches leagues internally. Add an optional `leagues` parameter so the HomeShellScreen can pass pre-fetched data, avoiding a redundant API call.

- [ ] **Step 1: Add optional `leagues` parameter**

In `LeagueTable.kt`, change the function signature to accept an optional pre-fetched list:

```kotlin
@Composable
fun LeagueTable(
    user: SleeperUser,
    onLeagueClick: (SleeperLeague) -> Unit = {},
    preloadedLeagues: List<SleeperLeague>? = null  // NEW
)
```

Then update the `LaunchedEffect` to skip fetching when `preloadedLeagues` is provided:

```kotlin
var leagues: List<SleeperLeague> by remember { mutableStateOf(preloadedLeagues ?: listOf()) }
var isLoading by remember { mutableStateOf(preloadedLeagues == null) }

LaunchedEffect(user.userId) {
    if (preloadedLeagues != null) return@LaunchedEffect  // Skip fetch
    scope.launch {
        try {
            isLoading = true
            errorMessage = null
            leagues = client.getLeaguesForUser(user.userId.toString(), "nfl", "2025")
        } catch (e: Exception) {
            errorMessage = "Failed to load leagues: ${e.message}"
        } finally {
            isLoading = false
        }
    }
}
```

- [ ] **Step 2: Update HomeShellScreen to pass pre-fetched leagues**

In `HomeShellScreen.kt`, update the `HomeTabContent` call:

```kotlin
HomeTab.LEAGUES -> LeagueTable(
    user = user,
    onLeagueClick = onLeagueSelected,
    preloadedLeagues = leagues
)
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/LeagueTable.kt
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/HomeShellScreen.kt
git commit -m "feat: allow LeagueTable to accept pre-fetched leagues"
```

---

### Task 6: Update LeagueShellScreen Back Button Label

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/shell/LeagueShellScreen.kt:147`

- [ ] **Step 1: Change back button text**

In `ShellHeader`, change line 147:

```kotlin
// Old:
Text("<- Leagues", color = MaterialTheme.colorScheme.primary)
// New:
Text("<- Home", color = MaterialTheme.colorScheme.primary)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/shell/LeagueShellScreen.kt
git commit -m "fix: update league shell back button label to 'Home'"
```

---

### Task 7: Rewire App.kt Navigation State Machine

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/App.kt`

This is the central change — replace the 3-state enum and rewire all transitions.

- [ ] **Step 1: Replace NavigationScreen enum**

```kotlin
// Old:
enum class NavigationScreen {
    USER_LOGIN,
    LEAGUE_LIST,
    LEAGUE_STATE
}

// New:
enum class NavigationScreen {
    USER_LOGIN,
    HOME_SHELL,
    LEAGUE_DETAIL
}
```

- [ ] **Step 2: Add `allLeagues` state and update navigation logic**

Replace the state block and `when` in `App()`:

```kotlin
var currentScreen by remember { mutableStateOf(NavigationScreen.USER_LOGIN) }
var username by remember { mutableStateOf("thehippokid") }
var user: SleeperUser? by remember { mutableStateOf(null) }
var selectedLeague: SleeperLeague? by remember { mutableStateOf(null) }
var allLeagues by remember { mutableStateOf<List<SleeperLeague>>(emptyList()) }
```

- [ ] **Step 3: Update USER_LOGIN handler**

After successful login, fetch leagues then navigate to HOME_SHELL:

```kotlin
NavigationScreen.USER_LOGIN -> {
    UserLoginScreen(
        initialUsername = username,
        onUserFound = { foundUser ->
            user = foundUser
            // Fetch leagues immediately after login
            scope.launch {
                val nflState = SleeperClient.getNflState()
                val season = nflState?.season ?: "2025"
                allLeagues = SleeperClient.getLeaguesForUser(
                    foundUser.userId.toString(), "nfl", season
                )
                currentScreen = NavigationScreen.HOME_SHELL
            }
        },
        onUsernameChanged = { username = it }
    )
}
```

**Note:** Add `val scope = rememberCoroutineScope()` inside `App()` before the `Box`. The existing `UserLoginScreen` composable calls `onUserFound` from its own coroutine scope, so the `scope.launch` here runs the league fetch asynchronously after login completes.

- [ ] **Step 4: Replace LEAGUE_LIST with HOME_SHELL**

```kotlin
NavigationScreen.HOME_SHELL -> {
    user?.let { currentUser ->
        HomeShellScreen(
            user = currentUser,
            leagues = allLeagues,
            onLeagueSelected = { league ->
                selectedLeague = league
                currentScreen = NavigationScreen.LEAGUE_DETAIL
            }
        )
    }
}
```

- [ ] **Step 5: Replace LEAGUE_STATE with LEAGUE_DETAIL**

```kotlin
NavigationScreen.LEAGUE_DETAIL -> {
    selectedLeague?.let { league ->
        user?.let { currentUser ->
            LeagueShellScreen(
                league = league,
                user = currentUser,
                onBack = { currentScreen = NavigationScreen.HOME_SHELL }
            )
        }
    }
}
```

- [ ] **Step 6: Add imports**

Add to `App.kt`:
```kotlin
import com.sleepyio.sleepyio.ui.home.HomeShellScreen
import kotlinx.coroutines.launch
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/App.kt
git commit -m "feat: rewire navigation to social-first home shell with cross-league feed"
```

---

### Task 8: Visual Verification

**Files:** None (testing only)

- [ ] **Step 1: Start the web dev server**

Run: `./gradlew :composeApp:jsBrowserDevelopmentRun`
The app should start at `http://localhost:8080`.

- [ ] **Step 2: Verify login flow**

Navigate to the app. Enter a username and submit. You should land on the Feed tab with activity loading progressively.

- [ ] **Step 3: Verify feed content**

Confirm:
- Filter chips are visible and functional (All / Trades / Waivers / Matchups / Drafts / Commissioner)
- Events are grouped by week, most recent first
- Each event shows a league name badge
- Trade cards show RECEIVED/SENT split
- Matchup cards show W/L, scores, close-game badge
- Progressive loading indicator shows "N/M leagues" while loading

- [ ] **Step 4: Verify Leagues tab**

Switch to the Leagues tab. Confirm the league list loads (should use pre-fetched data, no second API call).

- [ ] **Step 5: Verify league drill-down**

Tap a league name in the feed OR tap a league in the Leagues tab. Confirm:
- Full-screen league shell appears with its 4 tabs
- Back button says "<- Home"
- Tapping back returns to the Home Shell on the same tab you were on

- [ ] **Step 6: Verify responsive layout**

Resize the browser window:
- **< 800px:** Bottom navigation bar with Feed/Leagues icons
- **>= 800px:** Navigation rail on the left side

- [ ] **Step 7: Take screenshots for documentation**

Use Playwright to capture key states if desired.

---

### Task 9: Polish & Edge Cases

- [ ] **Step 1: Handle empty leagues**

If the user has no leagues, the Feed tab should show "No leagues found. Join a league on Sleeper to get started!" and the Leagues tab should show the same empty state.

- [ ] **Step 2: Handle feed loading errors**

If some leagues fail to load, the feed should still show events from successful leagues. Display an error banner at the top: "Could not load data from N league(s)."

- [ ] **Step 3: Verify matchup timestamps**

Matchup results currently use `timestamp = 0L`. Improve this by estimating timestamp from week number using NFL season start date from `SleeperState.seasonStartDate`. This ensures matchup results sort correctly relative to transactions in the same week.

- [ ] **Step 4: Commit polish changes**

```bash
git add composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/ui/home/
git commit -m "fix: polish feed edge cases - empty states, error handling, matchup ordering"
```
