package com.sleepyio.sleepyio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class BfsNode(
    val league: SleeperLeague,
    val users: List<SleeperUser>,
    val depth: Int,
    val discoveredBy: String? = null // Username or league name that led to this discovery
)

data class BfsState(
    val visited: Set<Long> = emptySet(),
    val channel: Channel<BfsNode> = Channel(UNLIMITED),
    val discovered: List<BfsNode> = emptyList(),
    val currentProcessing: BfsNode? = null,
    val isComplete: Boolean = false,
    val error: String? = null
)

private val logger = KotlinLogging.logger("LeagueBfsView")

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun LeagueBfsView(
    initialUser: SleeperUser,
    sport: String = "nfl",
    season: String = "2024",
    onBack: () -> Unit = {}
) {
    val client = remember { SleeperClient }
    var bfsState by remember { mutableStateOf(BfsState()) }
    var isRunning by remember { mutableStateOf(false) }
    var maxDepth by remember { mutableStateOf(3) }

    MyTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .safeContentPadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header with controls
                    BfsHeader(
                        initialUser = initialUser,
                        maxDepth = maxDepth,
                        onMaxDepthChange = { maxDepth = it },
                        isRunning = isRunning,
                        onStartBfs = {
                            if (!isRunning) {
                                isRunning = true
                                bfsState = BfsState()
                            }
                        },
                        onReset = {
                            isRunning = false
                            bfsState = BfsState()
                        },
                        onBack = onBack
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // BFS Status
                    BfsStatusCard(bfsState = bfsState)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Results visualization
                    BfsResultsView(bfsState = bfsState)
                }
            }
        }
    }

    // Run BFS when started
    LaunchedEffect(isRunning) {
        if (isRunning && bfsState.channel.isEmpty && bfsState.discovered.isEmpty()) {
            try {
                performBfs(
                    client = client,
                    initialUser = initialUser,
                    sport = sport,
                    season = season,
                    maxDepth = maxDepth,
                    onStateUpdate = { newState ->
                        bfsState = newState
                    }
                )
            } catch (e: Exception) {
                bfsState = bfsState.copy(
                    error = "Error during BFS: ${e.message}",
                    isComplete = true
                )
            } finally {
                isRunning = false
            }
        }
    }
}

@Composable
fun BfsHeader(
    initialUser: SleeperUser,
    maxDepth: Int,
    onMaxDepthChange: (Int) -> Unit,
    isRunning: Boolean,
    onStartBfs: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBack) {
                    Text("← Back")
                }

                Text(
                    text = "League Network Explorer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Starting from: ${initialUser.displayName ?: initialUser.userName ?: "Unknown User"}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Max Depth: $maxDepth",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = maxDepth.toFloat(),
                        onValueChange = { onMaxDepthChange(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        enabled = !isRunning,
                        modifier = Modifier.width(150.dp)
                    )
                }

                Row {
                    Button(
                        onClick = onStartBfs,
                        enabled = !isRunning
                    ) {
                        Text(if (isRunning) "Running..." else "Start BFS")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onReset,
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

@Composable
fun BfsStatusCard(bfsState: BfsState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                bfsState.error != null -> MaterialTheme.colorScheme.errorContainer
                bfsState.isComplete -> MaterialTheme.colorScheme.primaryContainer
                bfsState.currentProcessing != null -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "BFS Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                bfsState.error != null -> {
                    Text(
                        text = "❌ ${bfsState.error}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                bfsState.isComplete -> {
                    Text(
                        text = "✅ BFS Complete!",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                bfsState.currentProcessing != null -> {
                    Text(
                        text = "🔍 Processing: ${bfsState.currentProcessing.league.leagueName}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                else -> {
                    Text(
                        text = "⏳ Ready to start BFS",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Discovered: ${bfsState.discovered.size} leagues",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Visited: ${bfsState.visited.size} leagues",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun BfsResultsView(bfsState: BfsState) {
    if (bfsState.discovered.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No leagues discovered yet. Start BFS to explore the network!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val groupedByDepth = bfsState.discovered.groupBy { it.depth }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Group by depth for better visualization
        groupedByDepth.keys.sorted().forEach { depth ->
            val leagues = groupedByDepth[depth] ?: emptyList()

            item {
                DepthHeaderCard(depth = depth, leagueCount = leagues.size)
            }

            leagues.forEach { node ->
                item {
                    LeagueNodeCard(node = node)
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DepthHeaderCard(depth: Int, leagueCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = getDepthColor(depth)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Depth $depth",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "$leagueCount league${if (leagueCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun LeagueNodeCard(node: BfsNode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.league.leagueName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "League ID: ${node.league.leagueId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${node.league.leagueSize} teams • ${node.league.sport.uppercase()} ${node.league.season}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = getDepthColor(node.depth),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "D${node.depth}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            node.discoveredBy?.let { discoverer ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Discovered through: $discoverer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "👥 ${node.users.size} users in this league",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun getDepthColor(depth: Int): Color {
    return when (depth % 5) {
        0 -> Color(0xFF2196F3) // Blue
        1 -> Color(0xFF4CAF50) // Green
        2 -> Color(0xFFFF9800) // Orange
        3 -> Color(0xFF9C27B0) // Purple
        4 -> Color(0xFFF44336) // Red
        else -> Color(0xFF607D8B) // Blue Grey
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun performBfs(
    client: SleeperClient,
    initialUser: SleeperUser,
    sport: String,
    season: String,
    maxDepth: Int,
    onStateUpdate: (BfsState) -> Unit
) {
    val visited = ConcurrentSet<Long>()
    val channel = Channel<BfsNode>(UNLIMITED)
    val discovered = mutableListOf<BfsNode>()

    // Initialize with user's leagues
    initialUser.userId?.let { userId ->
        val initialLeagues = client.getLeaguesForUser(userId.toString(), sport, season)

        for (league in initialLeagues) {
            if (!visited.contains(league.leagueId)) {
                val users = client.getUsersInLeague(league.leagueId)
                val node = BfsNode(
                    league = league,
                    users = users,
                    depth = 0,
                    discoveredBy = initialUser.displayName ?: initialUser.userName
                )
                logger.info { "Discovered initial league: ${league.leagueName}" }
                channel.send(node)
                discovered.add(node)
                visited.add(league.leagueId)
            }
        }

        logger.info { "Discovered $discovered" }
        onStateUpdate(BfsState(
            visited = visited.toSet(),
            channel = channel,
            discovered = discovered.toList()
        ))
    }

    // BFS traversal
    coroutineScope {
        (1..4).map {
            async {
                logger.info { "BFS reader $it started" }
                while (!channel.isEmpty) {
                    val currentNode = channel.receive()
                    logger.info { "Processing league: ${currentNode.league.leagueName} at depth ${currentNode.depth}" }

                    onStateUpdate(
                        BfsState(
                            visited = visited.toSet(),
                            channel = channel,
                            discovered = discovered.toList(),
                            currentProcessing = currentNode
                        )
                    )

                    // Stop if we've reached max depth
                    if (currentNode.depth >= maxDepth) {
                        //delay(500) // Brief pause for visualization
                        continue
                    }

                    // Process all users in current league
                    val allUserLeagues = currentNode.users.map {
                        coroutineScope {
                            async {
                                try {
                                    client.getLeaguesForUser(it.userId.toString(), sport, season)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                    }.awaitAll().flatten()

                    val filteredLeagues = allUserLeagues.filter { !visited.contains(it.leagueId) }
                    filteredLeagues.map { league ->
                        async {
                            val users = client.getUsersInLeague(league.leagueId)
                            val node = BfsNode(
                                league = league,
                                users = users,
                                depth = currentNode.depth + 1,
                                discoveredBy = currentNode.league.leagueName
                            )
                            channel.send(node)
                            discovered.add(node)
                            visited.add(league.leagueId)
                        }
                    }.awaitAll()

                    // Update state after processing this node
                    onStateUpdate(
                        BfsState(
                            visited = visited.toSet(),
                            channel = channel,
                            discovered = discovered.toList()
                        )
                    )
                }
            }
        }.awaitAll()
    }

    // Mark as complete
    onStateUpdate(BfsState(
        visited = visited.toSet(),
        channel = Channel(UNLIMITED),
        discovered = discovered.toList(),
        isComplete = true
    ))
}




