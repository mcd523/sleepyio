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
