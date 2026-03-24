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
import com.sleepyio.sleepyio.client.model.stats.PlayerStats
import com.sleepyio.sleepyio.client.model.stats.PlayerProjection
import com.sleepyio.sleepyio.client.model.stats.WeeklyProjections
import com.sleepyio.sleepyio.client.model.graphql.GraphQLRequest
import com.sleepyio.sleepyio.client.model.graphql.GraphQLResponse
import com.sleepyio.sleepyio.client.model.graphql.PlayerNewsData
import com.sleepyio.sleepyio.client.model.graphql.PlayerNews
import com.sleepyio.sleepyio.client.model.graphql.MetadataData
import com.sleepyio.sleepyio.client.model.graphql.LeagueStanding
import com.sleepyio.sleepyio.getPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

data object SleeperClient {
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
    private const val STATS_BASE_URL = "https://api.sleeper.com"
    private const val CDN_BASE_URL = "https://sleepercdn.com"
    private const val GRAPHQL_URL = "https://sleeper.com/graphql"
    private const val USER_PATH = "$BASE_URL/user"
    private const val LEAGUE_PATH = "$BASE_URL/league"
    private const val DRAFT_PATH = "$BASE_URL/draft"
    private const val PLAYERS_PATH = "$BASE_URL/players"
    private const val STATE_PATH = "$BASE_URL/state"

    private val DEFAULT_POSITIONS = listOf("DEF", "K", "QB", "RB", "TE", "WR")

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

    suspend fun getLeaguesForUser(userId: String, sport: String = "nfl", season: String? = null): List<SleeperLeague> {
        return try {
            val effectiveSeason = season ?: getNflState()?.season ?: "2024"
            client.get("$USER_PATH/$userId/leagues/$sport/$effectiveSeason").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching leagues for user $userId" }
            emptyList()
        }
    }

    suspend fun getDraftsForUser(userId: String, sport: String = "nfl", season: String? = null): List<SleeperDraft> {
        return try {
            val effectiveSeason = season ?: getNflState()?.season ?: "2024"
            client.get("$USER_PATH/$userId/drafts/$sport/$effectiveSeason").body()
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

    suspend fun getLeague(leagueId: String): SleeperLeague? {
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

    suspend fun getUsersInLeague(leagueId: String): List<SleeperUser> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/users").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching users in league $leagueId" }
            emptyList()
        }
    }

    suspend fun getRostersInLeague(leagueId: Long): List<SleeperRoster> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/rosters").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching rosters in league $leagueId" }
            emptyList()
        }
    }

    suspend fun getRostersInLeague(leagueId: String): List<SleeperRoster> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/rosters").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching rosters in league $leagueId" }
            emptyList()
        }
    }

    suspend fun getMatchupsInLeague(leagueId: Long, week: Int? = null): List<SleeperMatchup> {
        return try {
            val effectiveWeek = week ?: getNflState()?.displayWeek?.toInt() ?: 1
            client.get("$LEAGUE_PATH/$leagueId/matchups/$effectiveWeek").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching matchups for league $leagueId week $week" }
            emptyList()
        }
    }

    suspend fun getMatchupsInLeague(leagueId: String, week: Int? = null): List<SleeperMatchup> {
        return try {
            val effectiveWeek = week ?: getNflState()?.displayWeek?.toInt() ?: 1
            client.get("$LEAGUE_PATH/$leagueId/matchups/$effectiveWeek").body()
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

    suspend fun getTransactions(leagueId: Long, week: Int? = null): List<SleeperTransaction> {
        return try {
            val effectiveWeek = week ?: getNflState()?.displayWeek?.toInt() ?: 1
            client.get("$LEAGUE_PATH/$leagueId/transactions/$effectiveWeek").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching transactions for league $leagueId week $week" }
            emptyList()
        }
    }

    suspend fun getTransactions(leagueId: String, week: Int? = null): List<SleeperTransaction> {
        return try {
            val effectiveWeek = week ?: getNflState()?.displayWeek?.toInt() ?: 1
            client.get("$LEAGUE_PATH/$leagueId/transactions/$effectiveWeek").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching transactions for league $leagueId week $week" }
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

    suspend fun getDraftsForLeague(leagueId: String): List<SleeperDraft> {
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
    suspend fun getAllPlayers(sport: String = "nfl"): Map<String, SleeperPlayer> {
        return try {
            client.get("$PLAYERS_PATH/$sport").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching all players for sport $sport" }
            emptyMap()
        }
    }

    suspend fun getTrendingPlayers(
        sport: String = "nfl",
        type: String = "add",
        lookbackHours: Int = 24,
        limit: Int = 25
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

    // Stats API endpoints (from api.sleeper.com)
    /**
     * Get player stats for a specific player.
     * @param playerId The player ID
     * @param season The season year (defaults to current season)
     * @param groupByWeek If true, returns stats grouped by week
     */
    suspend fun getPlayerStats(
        playerId: String,
        season: String? = null,
        groupByWeek: Boolean = false
    ): PlayerStats? {
        return try {
            val effectiveSeason = season ?: getNflState()?.season ?: "2024"
            val grouping = if (groupByWeek) "&grouping=week" else ""
            client.get("$STATS_BASE_URL/stats/nfl/player/$playerId?season_type=regular&season=$effectiveSeason$grouping").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching player stats for player $playerId" }
            null
        }
    }

    /**
     * Get player projections for a specific player.
     * @param playerId The player ID
     * @param season The season year (defaults to current season)
     */
    suspend fun getPlayerProjections(
        playerId: String,
        season: String? = null
    ): PlayerProjection? {
        return try {
            val effectiveSeason = season ?: getNflState()?.season ?: "2024"
            client.get("$STATS_BASE_URL/projections/nfl/player/$playerId?season_type=regular&season=$effectiveSeason&grouping=week").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching player projections for player $playerId" }
            null
        }
    }

    /**
     * Get top players by projected points.
     * @param season The season year (defaults to current season)
     * @param limit Maximum number of players to return (default 800)
     * @param positions List of positions to include (defaults to all fantasy positions)
     */
    suspend fun getPlayers(
        season: String? = null,
        limit: Int = 800,
        positions: List<String> = DEFAULT_POSITIONS
    ): List<PlayerProjection> {
        return try {
            val effectiveSeason = season ?: getNflState()?.season ?: "2024"
            val positionParams = positions.joinToString("&") { "position[]=$it" }
            val result: List<PlayerProjection> = client.get(
                "$STATS_BASE_URL/projections/nfl/$effectiveSeason?season_type=regular&$positionParams&order_by=pts_ppr"
            ).body()
            if (limit > 0) result.take(limit) else result
        } catch (e: Exception) {
            logger.error(e) { "Error fetching players" }
            emptyList()
        }
    }

    /**
     * Get player ranks for the season.
     * @param season The season year (defaults to current season)
     * @param positions List of positions to include (defaults to all fantasy positions)
     */
    suspend fun getPlayerRanks(
        season: String? = null,
        positions: List<String> = DEFAULT_POSITIONS
    ): List<Map<String, String>> {
        return try {
            val effectiveSeason = season ?: getNflState()?.season ?: "2024"
            val positionParams = positions.joinToString("&") { "position[]=$it" }
            client.get(
                "$STATS_BASE_URL/stats/nfl/$effectiveSeason?season_type=regular&$positionParams&order_by=pts_ppr"
            ).body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching player ranks" }
            emptyList()
        }
    }

    /**
     * Get all weekly projections for a specific week.
     * @param season The season year (defaults to current season)
     * @param week The week number (defaults to current display week)
     * @param positions List of positions to include (defaults to all fantasy positions)
     */
    suspend fun getAllWeeklyProjections(
        season: String? = null,
        week: Int? = null,
        positions: List<String> = DEFAULT_POSITIONS
    ): List<WeeklyProjections> {
        return try {
            val state = getNflState()
            val effectiveSeason = season ?: state?.season ?: "2024"
            val effectiveWeek = week ?: state?.displayWeek?.toInt() ?: 1
            val positionParams = positions.joinToString("&") { "position[]=$it" }
            client.get(
                "$STATS_BASE_URL/projections/nfl/$effectiveSeason/$effectiveWeek?season_type=regular&$positionParams&order_by=pts_ppr"
            ).body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching weekly projections" }
            emptyList()
        }
    }

    // GraphQL endpoints
    /**
     * Get player news for a specific player.
     * @param playerId The player ID
     * @param limit Maximum number of news items to return (default 2)
     */
    suspend fun getPlayerNews(playerId: String, limit: Int = 2): List<PlayerNews> {
        return try {
            val query = """
                query get_player_news_for_ids {
                    news: get_player_news(sport: "nfl", player_id: "$playerId", limit: $limit) {
                        metadata
                        player_id
                        published
                        source
                        source_key
                        sport
                    }
                }
            """.trimIndent()

            val response: GraphQLResponse<PlayerNewsData> = client.post(GRAPHQL_URL) {
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(
                    operationName = "get_player_news_for_ids",
                    variables = buildJsonObject {},
                    query = query
                ))
            }.body()

            response.data?.news ?: emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching player news for player $playerId" }
            emptyList()
        }
    }

    /**
     * Get league standings from league history.
     * @param leagueId The league ID
     * @return List of standings sorted by wins and points
     */
    suspend fun getLeagueStandings(leagueId: String): List<LeagueStanding> {
        return try {
            val query = """
                query metadata {
                    metadata(type: "league_history", key: "$leagueId") {
                        key
                        type
                        data
                        last_updated
                        created
                    }
                }
            """.trimIndent()

            val response: GraphQLResponse<MetadataData> = client.post(GRAPHQL_URL) {
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(
                    operationName = "metadata",
                    variables = buildJsonObject {},
                    query = query
                ))
            }.body()

            response.data?.metadata?.data?.standings
                ?.sortedWith(compareByDescending<LeagueStanding> { it.wins }.thenByDescending { it.fpts })
                ?: emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching league standings for league $leagueId" }
            emptyList()
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
            "$CDN_BASE_URL/avatars/thumbs/$avatarId"
        } else {
            "$CDN_BASE_URL/avatars/$avatarId"
        }
    }

    /**
     * Get player headshot URL.
     * @param playerId The player ID
     * @param sport The sport (default nfl)
     */
    fun getPlayerHeadshotUrl(playerId: String, sport: String = "nfl"): String {
        return "$CDN_BASE_URL/content/$sport/players/$playerId.jpg"
    }

    /**
     * Get team logo URL.
     * @param team The team abbreviation (e.g., "KC", "NE")
     */
    fun getTeamLogoUrl(team: String): String {
        return "$CDN_BASE_URL/images/team_logos/nfl/${team.lowercase()}.png"
    }
}
