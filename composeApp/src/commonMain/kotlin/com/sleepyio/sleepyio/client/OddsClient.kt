package com.sleepyio.sleepyio.client

import com.sleepyio.sleepyio.client.model.odds.GameOdds
import com.sleepyio.sleepyio.client.model.odds.NflTeamAbbreviations
import com.sleepyio.sleepyio.client.model.odds.OddsApiResponse
import com.sleepyio.sleepyio.getPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class OddsClient(private val apiKey: String) {
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
        private const val BASE_URL = "https://api.the-odds-api.com/v4"
        private const val NFL_SPORT = "americanfootball_nfl"
    }

    suspend fun getNflOdds(): List<GameOdds> {
        return try {
            val response: List<OddsApiResponse> = client.get(
                "$BASE_URL/sports/$NFL_SPORT/odds/?apiKey=$apiKey&regions=us&markets=spreads,totals&oddsFormat=american"
            ).body()

            response.mapNotNull { game -> parseGameOdds(game) }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching NFL odds" }
            emptyList()
        }
    }

    private fun parseGameOdds(response: OddsApiResponse): GameOdds? {
        // Use the first bookmaker with both spreads and totals
        val bookmaker = response.bookmakers.firstOrNull { bm ->
            bm.markets.any { it.key == "spreads" } && bm.markets.any { it.key == "totals" }
        } ?: return null

        val spreadsMarket = bookmaker.markets.firstOrNull { it.key == "spreads" } ?: return null
        val totalsMarket = bookmaker.markets.firstOrNull { it.key == "totals" } ?: return null

        val homeSpreadOutcome = spreadsMarket.outcomes.firstOrNull { it.name == response.homeTeam }
        val totalOverOutcome = totalsMarket.outcomes.firstOrNull { it.name == "Over" }

        val spread = homeSpreadOutcome?.point ?: return null
        val total = totalOverOutcome?.point ?: return null

        // Implied team totals: (total - spread) / 2 for home, (total + spread) / 2 for away
        // When home spread is negative (favored), home implied goes up
        val homeImplied = (total - spread) / 2
        val awayImplied = (total + spread) / 2

        return GameOdds(
            homeTeam = response.homeTeam,
            awayTeam = response.awayTeam,
            homeTeamAbbrev = NflTeamAbbreviations.abbreviation(response.homeTeam),
            awayTeamAbbrev = NflTeamAbbreviations.abbreviation(response.awayTeam),
            gameTime = response.commenceTime,
            spread = spread,
            total = total,
            homeImpliedTotal = homeImplied,
            awayImpliedTotal = awayImplied
        )
    }
}
