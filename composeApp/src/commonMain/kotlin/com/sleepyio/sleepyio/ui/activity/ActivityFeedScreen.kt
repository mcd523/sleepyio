package com.sleepyio.sleepyio.ui.activity

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
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.model.ActivityEvent
import com.sleepyio.sleepyio.intel.model.ActivityType
import kotlin.comparisons.compareByDescending

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityFeedScreen(
    leagueId: Long,
    modifier: Modifier = Modifier
) {
    var events by remember { mutableStateOf<List<ActivityEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf<ActivityType?>(null) }

    LaunchedEffect(leagueId) {
        try {
            isLoading = true
            val transactions = SleeperClient.getAllTransactionsInSeason(leagueId)
            val rosters = SleeperClient.getRostersInLeague(leagueId)
            val users = SleeperClient.getUsersInLeague(leagueId)

            val rosterToUser = mutableMapOf<Int, SleeperUser>()
            val userMap = users.associateBy { it.userId }
            for (roster in rosters) {
                roster.ownerId?.let { ownerId ->
                    userMap[ownerId]?.let { user ->
                        rosterToUser[roster.rosterId] = user
                    }
                }
            }

            events = transactions
                .filter { it.status == "complete" }
                .mapNotNull { tx ->
                    val primaryRosterId = tx.rosterIds.firstOrNull() ?: return@mapNotNull null
                    val primaryUser = rosterToUser[primaryRosterId] ?: return@mapNotNull null
                    val userId = primaryUser.userId ?: return@mapNotNull null
                    val userName = primaryUser.displayName ?: primaryUser.userName ?: "Unknown"

                    val addedPlayers = tx.adds?.keys?.mapNotNull { SleeperCache.getPlayer(it) } ?: emptyList()
                    val droppedPlayers = tx.drops?.keys?.mapNotNull { SleeperCache.getPlayer(it) } ?: emptyList()

                    val type = when (tx.type) {
                        "trade" -> ActivityType.TRADE
                        "waiver" -> ActivityType.WAIVER_CLAIM
                        "free_agent" -> ActivityType.FREE_AGENT_ADD
                        else -> return@mapNotNull null
                    }

                    val tradePartner = if (type == ActivityType.TRADE && tx.rosterIds.size > 1) {
                        val partnerRosterId = tx.rosterIds[1]
                        rosterToUser[partnerRosterId]
                    } else null

                    ActivityEvent(
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
                }
                .sortedByDescending { it.timestamp }

        } catch (e: Exception) {
            errorMessage = "Failed to load activity: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val filteredEvents = if (selectedFilter != null) {
        events.filter { it.type == selectedFilter }
    } else events

    val groupedByWeek = filteredEvents.groupBy { it.week }

    Column(modifier = modifier.fillMaxSize().padding(SleeperSpacing.md)) {
        Text(
            text = "Activity",
            fontSize = SleeperType.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        // Filter chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xs)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All", fontSize = SleeperType.caption) }
            )
            FilterChip(
                selected = selectedFilter == ActivityType.TRADE,
                onClick = { selectedFilter = if (selectedFilter == ActivityType.TRADE) null else ActivityType.TRADE },
                label = { Text("Trades", fontSize = SleeperType.caption) }
            )
            FilterChip(
                selected = selectedFilter == ActivityType.WAIVER_CLAIM,
                onClick = { selectedFilter = if (selectedFilter == ActivityType.WAIVER_CLAIM) null else ActivityType.WAIVER_CLAIM },
                label = { Text("Waivers", fontSize = SleeperType.caption) }
            )
            FilterChip(
                selected = selectedFilter == ActivityType.FREE_AGENT_ADD,
                onClick = { selectedFilter = if (selectedFilter == ActivityType.FREE_AGENT_ADD) null else ActivityType.FREE_AGENT_ADD },
                label = { Text("Free Agent", fontSize = SleeperType.caption) }
            )
        }

        Spacer(modifier = Modifier.height(SleeperSpacing.md))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(SleeperSpacing.md),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            filteredEvents.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No activity found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    groupedByWeek.forEach { (week, weekEvents) ->
                        item {
                            Text(
                                text = "WEEK $week",
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = SleeperSpacing.xs)
                            )
                        }
                        items(weekEvents) { event ->
                            ActivityEventCard(event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityEventCard(event: ActivityEvent) {
    val (borderColor, typeLabel) = when (event.type) {
        ActivityType.TRADE -> Color(0xFFFF9800) to "TRADE"
        ActivityType.WAIVER_CLAIM -> Color(0xFF1ABC9C) to "WAIVER CLAIM"
        ActivityType.FREE_AGENT_ADD -> Color(0xFF4CAF50) to "FREE AGENT"
        ActivityType.DROP -> Color(0xFFEF5350) to "DROP"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Color border
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .let {
                        // Use background color with fixed height as a workaround
                        it
                    }
            )
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
                        text = typeLabel,
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = borderColor
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.xs))

                when (event.type) {
                    ActivityType.TRADE -> {
                        Text(
                            text = "${event.userName} traded with ${event.tradePartnerName ?: "unknown"}",
                            fontSize = SleeperType.body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (event.playersAdded.isNotEmpty() || event.playersDropped.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                            ) {
                                if (event.playersAdded.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                                    ) {
                                        Column(modifier = Modifier.padding(SleeperSpacing.xs + SleeperSpacing.xxs)) {
                                            Text("RECEIVED", fontSize = SleeperType.caption, color = Color(0xFF4CAF50))
                                            event.playersAdded.forEach { player ->
                                                Text(
                                                    text = "${player.firstName ?: ""} ${player.lastName ?: ""} (${player.position ?: ""})",
                                                    fontSize = SleeperType.caption,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                if (event.playersDropped.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                                    ) {
                                        Column(modifier = Modifier.padding(SleeperSpacing.xs + SleeperSpacing.xxs)) {
                                            Text("SENT", fontSize = SleeperType.caption, color = Color(0xFFEF5350))
                                            event.playersDropped.forEach { player ->
                                                Text(
                                                    text = "${player.firstName ?: ""} ${player.lastName ?: ""} (${player.position ?: ""})",
                                                    fontSize = SleeperType.caption,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val addedNames = event.playersAdded.joinToString(", ") {
                            "${it.firstName ?: ""} ${it.lastName ?: ""} (${it.position ?: ""})"
                        }
                        val droppedNames = event.playersDropped.joinToString(", ") {
                            "${it.firstName ?: ""} ${it.lastName ?: ""} (${it.position ?: ""})"
                        }
                        if (addedNames.isNotEmpty()) {
                            Text(
                                text = "${event.userName} added $addedNames",
                                fontSize = SleeperType.body,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (droppedNames.isNotEmpty()) {
                            Text(
                                text = "Dropped: $droppedNames",
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
