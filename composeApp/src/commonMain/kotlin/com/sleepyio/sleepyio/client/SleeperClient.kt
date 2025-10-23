package com.sleepyio.sleepyio.client

import com.sleepyio.sleepyio.client.model.user.SleeperUser
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.getPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data object SleeperClient {
    private val logger = KotlinLogging.logger {  }
    private val platform = getPlatform()
    private val client = HttpClient(platform.clientEngine) {
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

    suspend fun getUser(): SleeperUser {
        return try {
            val response = client.get("$USER_PATH/thehippokid")
            logger.info { "Status: ${response.status}" }
            response.body()
//            SleeperUser(response.status.toString(), 0L, "")
        } catch (e: Exception) {
            logger.error(e) { "Error fetching user data" }
            SleeperUser("Error: ${e.message}", 0L, e.cause?.message.toString(), null)
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

    suspend fun getUsersInLeague(leagueId: String): List<SleeperUser> {
        return try {
            client.get("$LEAGUE_PATH/$leagueId/users").body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching users in league $leagueId" }
            emptyList()
        }
    }
}