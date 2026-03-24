package com.sleepyio.sleepyio.intel

import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.draft.SleeperPick
import com.sleepyio.sleepyio.client.model.graphql.LeagueStanding
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.league.SleeperTransaction
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.model.LeaguemateIntelSummary
import com.sleepyio.sleepyio.intel.model.MatchupResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

open class LeaguemateRepository(
    protected val myUserId: String
) {
    protected val sharedLeaguesCache = mutableMapOf<String, List<SleeperLeague>>()
    protected val rosterCache = mutableMapOf<Long, List<SleeperRoster>>()
    protected val userCache = mutableMapOf<Long, List<SleeperUser>>()
    protected val leagueHistoryCache = mutableMapOf<Long, List<SleeperLeague>>()
    protected val matchupCache = mutableMapOf<Pair<Long, Int>, List<SleeperMatchup>>()
    protected val transactionCache = mutableMapOf<Long, List<SleeperTransaction>>()
    private var nflStateCache: com.sleepyio.sleepyio.client.model.league.SleeperState? = null

    suspend fun getNflState(): com.sleepyio.sleepyio.client.model.league.SleeperState? {
        nflStateCache?.let { return it }
        return SleeperClient.getNflState().also { nflStateCache = it }
    }

    suspend fun getMatchups(leagueId: Long, week: Int): List<SleeperMatchup> {
        return matchupCache.getOrPut(leagueId to week) {
            SleeperClient.getMatchupsInLeague(leagueId, week)
        }
    }

    suspend fun getAllTransactions(leagueId: Long): List<SleeperTransaction> {
        return transactionCache.getOrPut(leagueId) {
            SleeperClient.getAllTransactionsInSeason(leagueId)
        }
    }

    suspend fun getLeagueHistory(leagueId: Long): List<SleeperLeague> {
        return leagueHistoryCache.getOrPut(leagueId) {
            val history = mutableListOf<SleeperLeague>()
            var currentId: Long? = leagueId
            while (currentId != null) {
                val league = SleeperClient.getLeague(currentId) ?: break
                history.add(league)
                currentId = league.previousLeagueId
            }
            history
        }
    }

    suspend fun getLeaguemates(leagueId: Long): List<SleeperUser> {
        return userCache.getOrPut(leagueId) {
            SleeperClient.getUsersInLeague(leagueId)
        }
    }

    suspend fun getRosters(leagueId: Long): List<SleeperRoster> {
        return rosterCache.getOrPut(leagueId) {
            SleeperClient.getRostersInLeague(leagueId)
        }
    }

    suspend fun getSharedLeagues(targetUserId: String): List<SleeperLeague> {
        return sharedLeaguesCache.getOrPut(targetUserId) {
            val myLeagues = SleeperClient.getLeaguesForUser(myUserId)
            val theirLeagues = SleeperClient.getLeaguesForUser(targetUserId)
            val theirLeagueIds = theirLeagues.map { it.leagueId }.toSet()
            myLeagues.filter { it.leagueId in theirLeagueIds }
        }
    }

    suspend fun getLeaguemateRoster(leagueId: Long, userId: String): SleeperRoster? {
        val rosters = getRosters(leagueId)
        return rosters.find { it.ownerId == userId }
    }

    suspend fun getLeaguemateTransactions(leagueId: Long, userId: String): List<SleeperTransaction> {
        val rosters = getRosters(leagueId)
        val userRosterIds = rosters.filter { it.ownerId == userId }.map { it.rosterId }.toSet()
        val allTransactions = getAllTransactions(leagueId)
        return allTransactions.filter { tx ->
            tx.rosterIds.any { it in userRosterIds }
        }
    }

    suspend fun getLeaguemateDraftPicks(leagueId: Long, userId: String): List<SleeperPick> {
        val rosters = getRosters(leagueId)
        val userRosterIds = rosters.filter { it.ownerId == userId }.map { it.rosterId.toString() }.toSet()
        val drafts = SleeperClient.getDraftsForLeague(leagueId)
        return drafts.flatMap { draft ->
            SleeperClient.getDraftPicks(draft.draftId).filter { pick ->
                pick.rosterId in userRosterIds
            }
        }
    }

    suspend fun getCurrentMatchupOpponent(leagueId: Long): SleeperUser? {
        val nflState = getNflState() ?: return null
        val currentWeek = nflState.week.toInt()
        val matchups = getMatchups(leagueId, currentWeek)
        val rosters = getRosters(leagueId)

        val myRosterIds = rosters.filter { it.ownerId == myUserId }.map { it.rosterId.toLong() }.toSet()
        val myMatchup = matchups.find { it.rosterId in myRosterIds } ?: return null

        val opponentMatchup = matchups.find {
            myMatchup.matchupId != null && it.matchupId == myMatchup.matchupId && it.rosterId !in myRosterIds
        } ?: return null

        val opponentRoster = rosters.find { it.rosterId.toLong() == opponentMatchup.rosterId }
        val opponentUserId = opponentRoster?.ownerId ?: return null

        val users = getLeaguemates(leagueId)
        return users.find { it.userId == opponentUserId }
    }

    suspend fun getHeadToHeadHistory(leagueId: Long, opponentUserId: String): List<MatchupResult> {
        val nflState = getNflState() ?: return emptyList()
        val currentWeek = nflState.week.toInt()
        val rosters = getRosters(leagueId)

        val myRosterIds = rosters.filter { it.ownerId == myUserId }.map { it.rosterId.toLong() }.toSet()
        val opponentRosterIds = rosters.filter { it.ownerId == opponentUserId }.map { it.rosterId.toLong() }.toSet()

        val results = mutableListOf<MatchupResult>()
        for (week in 1 until currentWeek) {
            val matchups = getMatchups(leagueId, week)
            val myMatchup = matchups.find { it.rosterId in myRosterIds } ?: continue
            val opponentMatchup = matchups.find {
                myMatchup.matchupId != null && it.matchupId == myMatchup.matchupId && it.rosterId in opponentRosterIds
            } ?: continue

            results.add(
                MatchupResult(
                    week = week,
                    myPoints = myMatchup.points,
                    theirPoints = opponentMatchup.points,
                    won = myMatchup.points > opponentMatchup.points
                )
            )
        }
        return results
    }

    suspend fun getHeadToHeadHistoryAllTime(
        currentLeagueId: Long,
        opponentUserId: String
    ): Map<String, List<MatchupResult>> {
        val history = getLeagueHistory(currentLeagueId)
        val resultsBySeason = mutableMapOf<String, List<MatchupResult>>()

        for (league in history) {
            val rosters = getRosters(league.leagueId)
            val myRosterIds = rosters.filter { it.ownerId == myUserId }.map { it.rosterId.toLong() }.toSet()
            val opponentRosterIds = rosters.filter { it.ownerId == opponentUserId }.map { it.rosterId.toLong() }.toSet()

            if (myRosterIds.isEmpty() || opponentRosterIds.isEmpty()) continue

            val isCurrentSeason = league.leagueId == currentLeagueId
            val maxWeek = if (isCurrentSeason) {
                (getNflState()?.week?.toInt() ?: 1)
            } else {
                18
            }

            val seasonResults = mutableListOf<MatchupResult>()
            for (week in 1 until maxWeek) {
                val matchups = getMatchups(league.leagueId, week)
                val myMatchup = matchups.find { it.rosterId in myRosterIds } ?: continue
                val opponentMatchup = matchups.find {
                    myMatchup.matchupId != null && it.matchupId == myMatchup.matchupId && it.rosterId in opponentRosterIds
                } ?: continue

                seasonResults.add(
                    MatchupResult(
                        week = week,
                        myPoints = myMatchup.points,
                        theirPoints = opponentMatchup.points,
                        won = myMatchup.points > opponentMatchup.points,
                        season = league.season
                    )
                )
            }
            if (seasonResults.isNotEmpty()) {
                resultsBySeason[league.season] = seasonResults
            }
        }
        return resultsBySeason
    }

    suspend fun getStandings(leagueId: Long): List<LeagueStanding> {
        return SleeperClient.getLeagueStandings(leagueId.toString())
    }

    fun getUserRosterIds(rosters: List<SleeperRoster>, userId: String): Set<Int> {
        return rosters.filter { it.ownerId == userId }.map { it.rosterId }.toSet()
    }

    suspend fun getLeagueIntelSummaries(leagueId: Long): List<LeaguemateIntelSummary> = coroutineScope {
        val users = getLeaguemates(leagueId)
        val standings = getStandings(leagueId)
        val currentOpponent = try { getCurrentMatchupOpponent(leagueId) } catch (_: Exception) { null }
        val standingsByOwner = standings.associateBy { it.ownerId }

        val nflState = getNflState()
        val currentWeek = nflState?.week?.toInt() ?: 1
        val rosters = getRosters(leagueId)

        // Build a map of rosterId -> ownerId for fast lookup
        val rosterToOwner = rosters.associate { it.rosterId.toLong() to it.ownerId }

        // Fetch all weekly matchups once (not per-user), populating the cache
        val scoresByUserId = mutableMapOf<String, MutableList<Float>>()
        for (week in 1 until currentWeek) {
            val matchups = getMatchups(leagueId, week)
            for (matchup in matchups) {
                val ownerId = rosterToOwner[matchup.rosterId] ?: continue
                scoresByUserId.getOrPut(ownerId) { mutableListOf() }.add(matchup.points)
            }
        }

        // Compute H2H and transactions in parallel per user
        users.filter { it.userId != myUserId }.map { user ->
            async {
                val userId = user.userId ?: return@async null

                val h2hBySeason = try {
                    getHeadToHeadHistoryAllTime(leagueId, userId)
                } catch (_: Exception) { emptyMap() }
                val allH2h = h2hBySeason.values.flatten()

                val weeklyScores = scoresByUserId[userId] ?: emptyList()
                val avgPoints = if (weeklyScores.isNotEmpty()) weeklyScores.average().toFloat() else 0f
                val trend = if (weeklyScores.size >= 3) {
                    val recent = weeklyScores.takeLast(3).average()
                    val overall = weeklyScores.average()
                    if (recent > overall * 1.05) "up"
                    else if (recent < overall * 0.95) "down"
                    else "steady"
                } else "steady"

                val txCount = try {
                    getLeaguemateTransactions(leagueId, userId).size
                } catch (_: Exception) { 0 }

                LeaguemateIntelSummary(
                    user = user,
                    record = standingsByOwner[userId],
                    h2hWins = allH2h.count { it.won },
                    h2hLosses = allH2h.count { !it.won },
                    h2hSeasons = h2hBySeason.size,
                    scoringTrend = trend,
                    avgPointsPerWeek = avgPoints,
                    recentTransactionCount = txCount,
                    isCurrentOpponent = currentOpponent?.userId == userId
                )
            }
        }.awaitAll().filterNotNull()
            .sortedWith(compareByDescending<LeaguemateIntelSummary> { it.isCurrentOpponent }
                .thenByDescending { it.avgPointsPerWeek })
    }
}
