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
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
    weekOverride: Int? = null
) {
    var feedState by remember { mutableStateOf(FeedState()) }
    var selectedFilter by remember { mutableStateOf(FeedFilter.ALL) }

    // Progressive loading — re-triggers when leagues, user, or week override changes
    LaunchedEffect(leagues, user, weekOverride) {
        feedState = FeedState() // reset on reload
        loadFeed(leagues, user, weekOverride).collect { state ->
            feedState = state
        }
    }

    val filteredEvents = feedState.events.filter { it.matchesFilter(selectedFilter) }
    val groupedByWeek = filteredEvents.groupBy { it.week }.entries.sortedByDescending { it.key }

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
                    groupedByWeek.forEach { entry ->
                        val week = entry.key
                        val weekEvents = entry.value
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
            Surface(
                modifier = Modifier.width(3.dp).defaultMinSize(minHeight = 60.dp),
                color = borderColor
            ) {}

            Column(modifier = Modifier.padding(SleeperSpacing.sm + SleeperSpacing.xs)) {
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
            "${(event.userScore * 10).toInt() / 10.0}",
            fontWeight = FontWeight.SemiBold,
            color = if (event.won) Color(0xFF68D391) else Color(0xFFFC8181),
            fontSize = SleeperType.body
        )
        Text("vs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = SleeperType.body)
        Text(
            "${(event.opponentScore * 10).toInt() / 10.0}",
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
