package com.sleepyio.sleepyio.client

import com.sleepyio.sleepyio.client.model.espn.EspnLeagueResponse
import com.sleepyio.sleepyio.client.model.espn.EspnPositionMap
import com.sleepyio.sleepyio.client.model.espn.EspnTeamMap
import com.sleepyio.sleepyio.getPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class EspnClient(
    private val espnS2: String,
    private val swid: String
) {
    private val logger = KotlinLogging.logger { }
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

    companion object {
        private const val BASE_URL = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl"
    }

    private fun leagueUrl(leagueId: Long, season: Int): String =
        "$BASE_URL/seasons/$season/segments/0/leagues/$leagueId"

    suspend fun getLeague(
        leagueId: Long,
        season: Int,
        views: List<String> = listOf("mTeam", "mRoster", "mSettings", "mMatchup", "mMatchupScore")
    ): EspnLeagueResponse? {
        return try {
            val viewParams = views.joinToString("&") { "view=$it" }
            client.get("${leagueUrl(leagueId, season)}?$viewParams") {
                header("Cookie", "espn_s2=$espnS2; SWID=$swid")
            }.body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching ESPN league $leagueId" }
            null
        }
    }

    suspend fun getScoreboard(
        leagueId: Long,
        season: Int,
        matchupPeriod: Int
    ): EspnLeagueResponse? {
        return try {
            client.get("${leagueUrl(leagueId, season)}?view=mScoreboard&scoringPeriodId=$matchupPeriod") {
                header("Cookie", "espn_s2=$espnS2; SWID=$swid")
            }.body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching ESPN scoreboard for league $leagueId week $matchupPeriod" }
            null
        }
    }

    suspend fun getRosters(
        leagueId: Long,
        season: Int
    ): EspnLeagueResponse? {
        return try {
            client.get("${leagueUrl(leagueId, season)}?view=mRoster") {
                header("Cookie", "espn_s2=$espnS2; SWID=$swid")
            }.body()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching ESPN rosters for league $leagueId" }
            null
        }
    }
}
