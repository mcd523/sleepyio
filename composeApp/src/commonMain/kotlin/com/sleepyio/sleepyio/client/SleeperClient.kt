package com.sleepyio.sleepyio.client

import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import com.sleepyio.sleepyio.client.model.league.SleeperTransaction
import com.sleepyio.sleepyio.client.model.league.SleeperState
import com.sleepyio.sleepyio.client.model.league.SleeperTradedPick
import com.sleepyio.sleepyio.client.model.league.bracket.Bracket
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.client.model.player.TrendingPlayer
import com.sleepyio.sleepyio.client.model.draft.SleeperDraft
import com.sleepyio.sleepyio.client.model.draft.SleeperPick
import com.sleepyio.sleepyio.getPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// NOTE: Phase 1 refactor — now implements [SleeperRepository]. All methods
// that the domain layer calls must match the interface signature. This is
// a narrow, additive change: no behavior change, the existing public
// surface is preserved for every other caller. See
// docs/architecture/REFACTOR_PLAN.md §4 + §11.
data object SleeperClient : SleeperRepository {
    private val logger = KotlinLogging.logger { }
    private val platform = getPlatform()
    private val client = HttpClient(platform.clientEngine) {
        engine {
            this.dispatcher
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private const val VERSION = "v1"
    private const val BASE_URL = "https://api.sleeper.app/$VERSION"
    private const val USER_PATH = "$BASE_URL/user"
    private const val LEAGUE_PATH = "$BASE_URL/league"
    private const val DRAFT_PATH = "$BASE_URL/draft"
    private const val PLAYERS_PATH = "$BASE_URL/players"
    private const val STATE_PATH = "$BASE_URL/state"

    // User endpoints
    suspend fun getUser(username: String): SleeperUser? {
        return try {
            client.get("$USER_PATH/$username").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching user by username: $username" }
            null
        }
    }

    suspend fun getUserById(userId: String): SleeperUser? {
        return try {
            client.get("$USER_PATH/$userId").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching user by ID: $userId" }
            null
        }
    }

    suspend fun getLeaguesForUser(userId: String, sport: String, season: String): List<SleeperLeague> {
        return try {
            client.get("$USER_PATH/$userId/leagues/$sport/$season").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching leagues for user $userId" }
            emptyList()
        }
    }

    suspend fun getDraftsForUser(userId: String, sport: String, season: String): List<SleeperDraft> {
        return try {
            client.get("$USER_PATH/$userId/drafts/$sport/$season").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching drafts for user $userId" }
            emptyList()
        }
    }

    // League endpoints
    suspend fun getLeague(leagueId: Long): SleeperLeague? {
        return try {
            client.get("$LEAGUE_PATH/$leagueId").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching league $leagueId" }
            null
        }
    }

    suspend fun getUsersInLeague(leagueId: Long): List<SleeperUser> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/users").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching users in league $leagueId" }
            emptyList()
        }
    }

    override suspend fun getRostersInLeague(leagueId: Long): List<SleeperRoster> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/rosters").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching rosters in league $leagueId" }
            emptyList()
        }
    }

    suspend fun getMatchupsInLeague(leagueId: Long, week: Int): List<SleeperMatchup> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/matchups/$week").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching matchups for league $leagueId week $week" }
            emptyList()
        }
    }

    suspend fun getWinnersBracket(leagueId: Long): List<Bracket> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/winners_bracket").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching winners bracket for league $leagueId" }
            emptyList()
        }
    }

    suspend fun getLosersBracket(leagueId: Long): List<Bracket> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/losers_bracket").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching losers bracket for league $leagueId" }
            emptyList()
        }
    }

    suspend fun getTransactions(leagueId: Long, round: Int): List<SleeperTransaction> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/transactions/$round").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching transactions for league $leagueId round $round" }
            emptyList()
        }
    }

    suspend fun getTradedPicks(leagueId: Long): List<SleeperTradedPick> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/traded_picks").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching traded picks for league $leagueId" }
            emptyList()
        }
    }

    // Draft endpoints
    suspend fun getDraftsForLeague(leagueId: Long): List<SleeperDraft> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/drafts").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching drafts for league $leagueId" }
            emptyList()
        }
    }

    suspend fun getDraft(draftId: String): SleeperDraft? {
        return try {
            client.get("$DRAFT_PATH/$draftId").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching draft $draftId" }
            null
        }
    }

    suspend fun getDraftPicks(draftId: String): List<SleeperPick> {
        return try {
            client.get("$DRAFT_PATH/$draftId/picks").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching picks for draft $draftId" }
            emptyList()
        }
    }

    suspend fun getTradedDraftPicks(draftId: String): List<SleeperTradedPick> {
        return try {
            client.get("$DRAFT_PATH/$draftId/traded_picks").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching traded picks for draft $draftId" }
            emptyList()
        }
    }

    // Player endpoints
    override suspend fun getAllPlayers(sport: String): Map<String, SleeperPlayer> {
        return try {
            client.get("$PLAYERS_PATH/$sport").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching all players for sport $sport" }
            emptyMap()
        }
    }

    override suspend fun getTrendingPlayers(
        sport: String,
        type: String,
        lookbackHours: Int,
        limit: Int,
    ): List<TrendingPlayer> {
        return try {
            client.get("$PLAYERS_PATH/$sport/trending/$type?lookback_hours=$lookbackHours&limit=$limit").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching trending players for sport $sport" }
            emptyList()
        }
    }

    // State endpoints
    suspend fun getNflState(): SleeperState? {
        return try {
            client.get("$STATE_PATH/nfl").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching NFL state" }
            null
        }
    }

    suspend fun getSportState(sport: String): SleeperState? {
        return try {
            client.get("$STATE_PATH/$sport").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching $sport state" }
            null
        }
    }

    // Convenience methods for common operations
    suspend fun getCurrentWeekMatchups(leagueId: Long): List<SleeperMatchup> {
        return try {
            val state = getNflState()
            val currentWeek = state?.week?.toInt() ?: 1
            getMatchupsInLeague(leagueId, currentWeek)
        } catch (e: Exception) {
            logger.error(e) { "Error fetching current week matchups for league $leagueId" }
            emptyList()
        }
    }

    suspend fun getAllTransactionsInSeason(leagueId: Long): List<SleeperTransaction> {
        return try {
            val state = getNflState()
            val currentWeek = state?.week?.toInt() ?: 18
            val allTransactions = mutableListOf<SleeperTransaction>()

            for (week in 1..currentWeek) {
                val weekTransactions = getTransactions(leagueId, week)
                allTransactions.addAll(weekTransactions)
            }

            allTransactions
        } catch (e: Exception) {
            logger.error(e) { "Error fetching all transactions for league $leagueId" }
            emptyList()
        }
    }

    // Utility methods for avatar URLs (based on documentation)
    fun getAvatarUrl(avatarId: String, thumbnail: Boolean = false): String {
        return if (thumbnail) {
            "https://sleepercdn.com/avatars/thumbs/$avatarId"
        } else {
            "https://sleepercdn.com/avatars/$avatarId"
        }
    }
}
