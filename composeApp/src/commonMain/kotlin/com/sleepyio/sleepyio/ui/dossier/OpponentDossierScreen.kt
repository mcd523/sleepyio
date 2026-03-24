package com.sleepyio.sleepyio.ui.dossier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.InsightRegistry
import com.sleepyio.sleepyio.intel.OpponentRepository
import com.sleepyio.sleepyio.intel.model.OpponentLeague
import com.sleepyio.sleepyio.ui.components.InsightCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// ---------- Threat colors (shared with OpponentCard) ----------

private val ThreatRed = Color(0xFFEF5350)
private val ThreatOrange = Color(0xFFFF9800)
private val ThreatTeal = Color(0xFF1ABC9C)

private fun threatColor(score: Int): Color = when {
    score > 70 -> ThreatRed
    score > 40 -> ThreatOrange
    else -> ThreatTeal
}

private fun threatLabel(score: Int): String = when {
    score > 70 -> "HIGH"
    score > 40 -> "MODERATE"
    else -> "LOW"
}

// ---------- Dossier state ----------

private data class DossierHeaderData(
    val opponentUser: SleeperUser,
    val sharedCount: Int,
    val shadowCount: Int,
    val firstSeasonTogether: String?,
    val threatScore: Int,
    val h2hRecord: Pair<Int, Int>, // wins, losses
    val avgPointsPerWeek: Float,
    val movesThisSeason: Int,
    val convergentPlayerCount: Int
)

private data class DossierState(
    val header: DossierHeaderData? = null,
    val isDiscovering: Boolean = true,
    val leagueHistory: Map<Long, List<SleeperLeague>> = emptyMap(),
    val discoveryComplete: Boolean = false,
    val error: String? = null
)

// ---------- Screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentDossierScreen(
    opponentUserId: String,
    myUserId: String,
    leagues: List<SleeperLeague>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember(myUserId) { OpponentRepository(myUserId) }
    var state by remember { mutableStateOf(DossierState()) }

    // Discovery and header data loading
    LaunchedEffect(opponentUserId, myUserId) {
        state = DossierState(isDiscovering = true)
        try {
            // Resolve opponent user
            val opponentUser = SleeperClient.getUserById(opponentUserId) ?: SleeperUser(
                userId = opponentUserId,
                displayName = "User $opponentUserId"
            )

            // Tier 1+2: Discover leagues
            val opponentLeagues = repository.discoverOpponentLeagues(opponentUserId)
            val sharedCount = opponentLeagues.count { it.isShared }
            val shadowCount = opponentLeagues.count { !it.isShared }

            // Build leagueHistory map for module analysis
            val leagueHistoryMap = mutableMapOf<Long, List<SleeperLeague>>()
            for (opponentLeague in opponentLeagues) {
                val leagueId = opponentLeague.league.leagueId
                leagueHistoryMap[leagueId] = listOf(opponentLeague.league)
            }

            // Find first season together
            val sharedSeasons = opponentLeagues.filter { it.isShared }.map { it.season }.sorted()
            val firstSeason = sharedSeasons.firstOrNull()

            // Compute H2H record across shared leagues
            var h2hWins = 0
            var h2hLosses = 0
            val sharedLeagues = opponentLeagues.filter { it.isShared }
            for (opponentLeague in sharedLeagues) {
                try {
                    val h2h = repository.getHeadToHeadHistory(
                        opponentLeague.league.leagueId,
                        opponentUserId
                    )
                    h2hWins += h2h.count { it.won }
                    h2hLosses += h2h.count { !it.won }
                } catch (_: Exception) { /* skip */ }
            }

            // Average points per week across shared leagues
            var totalPoints = 0f
            var totalWeeks = 0
            for (opponentLeague in sharedLeagues) {
                try {
                    val nflState = repository.getNflState()
                    val maxWeek = nflState?.week?.toInt() ?: 1
                    val rosters = repository.getRosters(opponentLeague.league.leagueId)
                    val opponentRosterIds = rosters.filter { it.ownerId == opponentUserId }
                        .map { it.rosterId.toLong() }.toSet()
                    for (week in 1 until maxWeek) {
                        val matchups = repository.getMatchups(
                            opponentLeague.league.leagueId, week
                        )
                        val opponentMatchup = matchups.find { it.rosterId in opponentRosterIds }
                        if (opponentMatchup != null) {
                            totalPoints += opponentMatchup.points
                            totalWeeks++
                        }
                    }
                } catch (_: Exception) { /* skip */ }
            }
            val avgPts = if (totalWeeks > 0) totalPoints / totalWeeks else 0f

            // Count transactions (moves) this season
            var movesCount = 0
            for (opponentLeague in sharedLeagues.take(5)) {
                try {
                    val txs = repository.getLeaguemateTransactions(
                        opponentLeague.league.leagueId, opponentUserId
                    )
                    movesCount += txs.size
                } catch (_: Exception) { /* skip */ }
            }

            // Convergent players
            val convergent = try {
                repository.getConvergentPlayers(opponentUserId)
            } catch (_: Exception) { emptyMap() }

            // Compute threat score
            val threatScore = computeDossierThreatScore(
                sharedCount = sharedCount,
                shadowCount = shadowCount,
                h2hWins = h2hWins,
                h2hLosses = h2hLosses,
                totalLeagues = opponentLeagues.size
            )

            state = state.copy(
                header = DossierHeaderData(
                    opponentUser = opponentUser,
                    sharedCount = sharedCount,
                    shadowCount = shadowCount,
                    firstSeasonTogether = firstSeason,
                    threatScore = threatScore,
                    h2hRecord = h2hWins to h2hLosses,
                    avgPointsPerWeek = avgPts,
                    movesThisSeason = movesCount,
                    convergentPlayerCount = convergent.size
                ),
                isDiscovering = false,
                leagueHistory = leagueHistoryMap,
                discoveryComplete = true
            )
        } catch (e: Exception) {
            state = state.copy(
                isDiscovering = false,
                error = "Failed to load dossier: ${e.message}"
            )
        }
    }

    val configuration = LocalWindowInfo.current
    val screenWidth = configuration.containerSize.width
    val isDesktop = screenWidth >= 1200

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with back button
        TopAppBar(
            title = {
                Text(
                    text = "DOSSIER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Text("<", fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        when {
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = SleeperType.body
                        )
                        Spacer(modifier = Modifier.height(SleeperSpacing.md))
                        OutlinedButton(onClick = onBack) {
                            Text("Back to War Room")
                        }
                    }
                }
            }

            state.isDiscovering && state.header == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                        Text(
                            text = "Building dossier...",
                            fontSize = SleeperType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.header != null -> {
                val header = state.header!!
                val modules = remember { InsightRegistry.getTwoPassModules() }

                if (isDesktop) {
                    // Desktop: scrollable column with 2-col module grid
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = SleeperSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(SleeperSpacing.md),
                            contentPadding = PaddingValues(vertical = SleeperSpacing.md)
                        ) {
                            item {
                                DossierHeader(header = header)
                            }

                            item {
                                QuickStatsRow(header = header)
                            }

                            // Module grid: pair modules into rows of 2
                            val modulePairs = modules.chunked(2)
                            items(modulePairs.size) { index ->
                                val pair = modulePairs[index]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.md)
                                ) {
                                    pair.forEach { module ->
                                        @Suppress("UNCHECKED_CAST")
                                        InsightCard(
                                            module = module as InsightModule<Any>,
                                            targetUserId = opponentUserId,
                                            myUserId = myUserId,
                                            leagueHistory = state.leagueHistory,
                                            repository = repository,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Fill remaining space if odd number
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mobile/tablet: single column
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = SleeperSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.md),
                        contentPadding = PaddingValues(vertical = SleeperSpacing.md)
                    ) {
                        item {
                            DossierHeader(header = header)
                        }

                        item {
                            QuickStatsRow(header = header)
                        }

                        items(modules.size) { index ->
                            val module = modules[index]
                            @Suppress("UNCHECKED_CAST")
                            InsightCard(
                                module = module as InsightModule<Any>,
                                targetUserId = opponentUserId,
                                myUserId = myUserId,
                                leagueHistory = state.leagueHistory,
                                repository = repository,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Header ----------

@Composable
private fun DossierHeader(header: DossierHeaderData) {
    val color = threatColor(header.threatScore)
    val user = header.opponentUser
    val displayChar = (user.displayName ?: user.userName ?: "?").first().uppercaseChar()
    val displayName = user.displayName ?: user.userName ?: "Unknown"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SleeperSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle with threat-colored border
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayChar.toString(),
                    fontSize = SleeperType.headline,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.width(SleeperSpacing.md))

            // Name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = SleeperType.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                Text(
                    text = buildString {
                        append("${header.sharedCount} shared")
                        if (header.shadowCount > 0) append(" \u00B7 ${header.shadowCount} shadow")
                        header.firstSeasonTogether?.let { append(" \u00B7 Since $it") }
                    },
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(SleeperSpacing.md))

            // Threat level box
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${header.threatScore}",
                    fontSize = SleeperType.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "THREAT LEVEL",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(
                            horizontal = SleeperSpacing.sm,
                            vertical = SleeperSpacing.xxs
                        )
                    )
                }
            }
        }
    }
}

// ---------- Quick stats row ----------

@Composable
private fun QuickStatsRow(header: DossierHeaderData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
    ) {
        QuickStatCard(
            value = "${header.h2hRecord.first}-${header.h2hRecord.second}",
            label = "H2H Record",
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            value = "%.1f".format(header.avgPointsPerWeek),
            label = "Avg Pts/Wk",
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            value = "${header.movesThisSeason}",
            label = "Moves",
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            value = "${header.convergentPlayerCount}",
            label = "Convergent",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SleeperSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = SleeperType.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
            Text(
                text = label,
                fontSize = SleeperType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------- Helpers ----------

private fun computeDossierThreatScore(
    sharedCount: Int,
    shadowCount: Int,
    h2hWins: Int,
    h2hLosses: Int,
    totalLeagues: Int
): Int {
    var score = 0

    // League overlap
    score += (sharedCount * 15).coerceAtMost(45)

    // Shadow league presence
    score += (shadowCount * 5).coerceAtMost(20)

    // H2H dominance (they beat us more = higher threat)
    val totalH2h = h2hWins + h2hLosses
    if (totalH2h > 0) {
        val lossRate = h2hLosses.toFloat() / totalH2h
        score += (lossRate * 25).toInt()
    }

    // Multi-league exposure
    if (totalLeagues >= 3) score += 10

    return score.coerceIn(0, 100)
}
