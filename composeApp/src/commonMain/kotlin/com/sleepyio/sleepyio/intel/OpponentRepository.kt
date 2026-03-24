package com.sleepyio.sleepyio.intel

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.draft.SleeperPick
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.league.SleeperTransaction
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.intel.model.OpponentLeague
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class OpponentRepository(myUserId: String) : LeaguemateRepository(myUserId) {

    private val rateLimiter = RateLimiter()
    private val shadowLeagueCache = mutableMapOf<String, List<OpponentLeague>>()
    private val shadowMetadataCache = mutableMapOf<Long, ShadowLeagueMetadata>()

    data class ShadowLeagueMetadata(
        val league: SleeperLeague,
        val rosters: List<SleeperRoster>,
        val users: List<SleeperUser>
    )

    data class DeepIntelData(
        val transactions: List<SleeperTransaction>,
        val matchupsByWeek: Map<Int, List<SleeperMatchup>>,
        val draftPicks: List<SleeperPick>
    )

    /**
     * Tier 1: Discovery — find all leagues for an opponent across seasons.
     * Tags each league as shared (also contains myUserId) or shadow (opponent-only).
     */
    suspend fun discoverOpponentLeagues(
        opponentId: String,
        seasons: List<String> = listOf("2024", "2025", "2026")
    ): List<OpponentLeague> {
        shadowLeagueCache[opponentId]?.let { return it }

        val myLeagueIds = coroutineScope {
            seasons.map { season ->
                async {
                    rateLimiter.withLimit {
                        SleeperClient.getLeaguesForUser(myUserId, season = season)
                    }
                }
            }.awaitAll().flatten().map { it.leagueId }.toSet()
        }

        val opponentLeagues = coroutineScope {
            seasons.map { season ->
                async {
                    rateLimiter.withLimit {
                        SleeperClient.getLeaguesForUser(opponentId, season = season)
                            .map { league ->
                                OpponentLeague(
                                    league = league,
                                    isShared = league.leagueId in myLeagueIds,
                                    season = season
                                )
                            }
                    }
                }
            }.awaitAll().flatten()
        }

        shadowLeagueCache[opponentId] = opponentLeagues
        return opponentLeagues
    }

    /**
     * Tier 2: Metadata — fetch league details, rosters, and users for a shadow league.
     */
    suspend fun fetchShadowMetadata(leagueId: Long): ShadowLeagueMetadata? {
        shadowMetadataCache[leagueId]?.let { return it }

        val league = rateLimiter.withLimit { SleeperClient.getLeague(leagueId) } ?: return null
        val rosters = rateLimiter.withLimit { SleeperClient.getRostersInLeague(leagueId) }
        val users = rateLimiter.withLimit { SleeperClient.getUsersInLeague(leagueId) }

        val metadata = ShadowLeagueMetadata(league, rosters, users)
        shadowMetadataCache[leagueId] = metadata
        return metadata
    }

    /**
     * Tier 3: Deep Intel — fetch transactions, matchups, and draft picks for a league.
     */
    suspend fun fetchDeepIntel(leagueId: Long): DeepIntelData {
        val transactions = rateLimiter.withLimit {
            SleeperClient.getAllTransactionsInSeason(leagueId)
        }

        val nflState = getNflState()
        val maxWeek = nflState?.week?.toInt() ?: 18

        val matchupsByWeek = coroutineScope {
            (1..maxWeek).map { week ->
                async {
                    week to rateLimiter.withLimit {
                        SleeperClient.getMatchupsInLeague(leagueId, week)
                    }
                }
            }.awaitAll().toMap()
        }

        val draftPicks = coroutineScope {
            val drafts = rateLimiter.withLimit { SleeperClient.getDraftsForLeague(leagueId) }
            drafts.map { draft ->
                async {
                    rateLimiter.withLimit { SleeperClient.getDraftPicks(draft.draftId) }
                }
            }.awaitAll().flatten()
        }

        return DeepIntelData(transactions, matchupsByWeek, draftPicks)
    }

    /**
     * Discover all opponents across a set of my leagues, then find their shadow leagues.
     */
    suspend fun discoverAllOpponents(myLeagueIds: List<Long>): Map<String, List<OpponentLeague>> {
        val uniqueOpponentIds = coroutineScope {
            myLeagueIds.map { leagueId ->
                async {
                    rateLimiter.withLimit {
                        SleeperClient.getUsersInLeague(leagueId)
                            .mapNotNull { it.userId }
                            .filter { it != myUserId }
                    }
                }
            }.awaitAll().flatten().toSet()
        }

        return coroutineScope {
            uniqueOpponentIds.map { opponentId ->
                async { opponentId to discoverOpponentLeagues(opponentId) }
            }.awaitAll().toMap()
        }
    }

    /**
     * Get cached leagues for an opponent (null if not yet discovered).
     */
    fun getOpponentLeagues(opponentId: String): List<OpponentLeague>? {
        return shadowLeagueCache[opponentId]
    }

    /**
     * Get convergent players — players rostered by an opponent across multiple leagues.
     * Returns playerId -> count of leagues they appear in.
     */
    suspend fun getConvergentPlayers(opponentId: String): Map<String, Int> {
        val leagues = shadowLeagueCache[opponentId] ?: return emptyMap()

        val playerCounts = mutableMapOf<String, Int>()

        for (opponentLeague in leagues) {
            val leagueId = opponentLeague.league.leagueId
            val metadata = fetchShadowMetadata(leagueId) ?: continue
            val opponentRoster = metadata.rosters.find { it.ownerId == opponentId } ?: continue
            for (playerId in opponentRoster.players) {
                playerCounts[playerId] = (playerCounts[playerId] ?: 0) + 1
            }
        }

        return playerCounts.filter { it.value > 1 }
    }
}
