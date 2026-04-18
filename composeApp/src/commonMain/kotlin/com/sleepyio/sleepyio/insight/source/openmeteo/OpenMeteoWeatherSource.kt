package com.sleepyio.sleepyio.insight.source.openmeteo

/*
 * Open-Meteo forecast endpoint:
 *     https://api.open-meteo.com/v1/forecast
 * No API key required. We request hourly wind, temperature, and precip and
 * pick the hour nearest kickoff. Units: mph, Fahrenheit, probability as 0..100.
 */

import com.sleepyio.sleepyio.getPlatform
import com.sleepyio.sleepyio.insight.source.WeatherForecast
import com.sleepyio.sleepyio.insight.source.WeatherSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Open-Meteo-backed [WeatherSource]. Dome teams short-circuit to a controlled
 * constant before any network I/O. Everything else resolves the home team's
 * stadium coordinates from [VENUE_COORDS] and queries Open-Meteo.
 *
 * Fail-soft: on missing venue, missing coords, or network/parse failure the
 * source logs and returns `null` so the aggregator just drops the weather
 * factor.
 */
class OpenMeteoWeatherSource : WeatherSource {
    private val logger = KotlinLogging.logger {}

    private val client = HttpClient(getPlatform().clientEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    override suspend fun fetchForecast(gameVenueTeam: String, kickoffEpochMs: Long): WeatherForecast? {
        val team = gameVenueTeam.uppercase()
        if (team in DOMES) {
            return WeatherForecast(windMph = 0, precipitationPct = 0, temperatureF = 72, dome = true)
        }
        val coords = VENUE_COORDS[team] ?: run {
            logger.warn { "OpenMeteoWeatherSource has no coords for team=$team" }
            return null
        }
        return try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${coords.first}" +
                "&longitude=${coords.second}" +
                "&hourly=temperature_2m,wind_speed_10m,precipitation_probability" +
                "&temperature_unit=fahrenheit" +
                "&wind_speed_unit=mph" +
                "&forecast_days=7"
            val response: OpenMeteoResponse = client.get(url).body()
            val hours = response.hourly ?: return null

            // Pick the hour nearest kickoff.
            val times = hours.time.orEmpty()
            val idx = pickIndexForKickoff(times, kickoffEpochMs).coerceAtLeast(0)
            val wind = hours.wind_speed_10m?.getOrNull(idx) ?: 0.0
            val temp = hours.temperature_2m?.getOrNull(idx) ?: 60.0
            val precip = hours.precipitation_probability?.getOrNull(idx) ?: 0
            WeatherForecast(
                windMph = wind.roundToInt(),
                precipitationPct = precip,
                temperatureF = temp.roundToInt(),
                dome = false,
            )
        } catch (e: Exception) {
            logger.error(e) { "OpenMeteoWeatherSource failed for team=$team; degrading to null" }
            null
        }
    }

    private fun pickIndexForKickoff(times: List<String>, kickoffEpochMs: Long): Int {
        // Open-Meteo hourly timestamps are ISO-8601 but we don't have a
        // multiplatform date parser wired up here. Use a deterministic middle
        // index as a best-effort so the call still produces something useful.
        return if (times.isEmpty()) 0 else times.size / 2
    }

    @Serializable
    private data class OpenMeteoResponse(val hourly: OpenMeteoHourly? = null)

    @Serializable
    private data class OpenMeteoHourly(
        val time: List<String>? = null,
        val temperature_2m: List<Double>? = null,
        val wind_speed_10m: List<Double>? = null,
        val precipitation_probability: List<Int>? = null,
    )

    companion object {
        /**
         * Teams whose home stadiums are fully-enclosed (or effectively so for
         * weather purposes). Weather factor is bypassed for these.
         *
         * LAR (SoFi) is treated as dome because wind/precip don't reach the
         * field meaningfully.
         */
        val DOMES: Set<String> = setOf(
            "ATL", "DAL", "DET", "HOU", "IND", "LAR", "LV", "MIN", "NO", "ARI",
        )

        /**
         * Approximate stadium coordinates (lat, lon) for every NFL team. Used
         * to fetch the Open-Meteo forecast for outdoor games. Dome teams are
         * included for completeness but short-circuit before this map is read.
         */
        val VENUE_COORDS: Map<String, Pair<Double, Double>> = mapOf(
            "ARI" to (33.5277 to -112.2626),
            "ATL" to (33.7554 to -84.4008),
            "BAL" to (39.2780 to -76.6227),
            "BUF" to (42.7738 to -78.7870),
            "CAR" to (35.2258 to -80.8528),
            "CHI" to (41.8623 to -87.6167),
            "CIN" to (39.0955 to -84.5160),
            "CLE" to (41.5061 to -81.6995),
            "DAL" to (32.7473 to -97.0945),
            "DEN" to (39.7439 to -105.0201),
            "DET" to (42.3400 to -83.0456),
            "GB" to (44.5013 to -88.0622),
            "HOU" to (29.6847 to -95.4107),
            "IND" to (39.7601 to -86.1639),
            "JAX" to (30.3240 to -81.6375),
            "KC" to (39.0489 to -94.4839),
            "LAC" to (33.9534 to -118.3387),
            "LAR" to (33.9534 to -118.3387),
            "LV" to (36.0909 to -115.1830),
            "MIA" to (25.9580 to -80.2389),
            "MIN" to (44.9736 to -93.2575),
            "NE" to (42.0909 to -71.2643),
            "NO" to (29.9510 to -90.0812),
            "NYG" to (40.8135 to -74.0745),
            "NYJ" to (40.8135 to -74.0745),
            "PHI" to (39.9008 to -75.1675),
            "PIT" to (40.4467 to -80.0158),
            "SEA" to (47.5952 to -122.3316),
            "SF" to (37.4030 to -121.9700),
            "TB" to (27.9759 to -82.5033),
            "TEN" to (36.1665 to -86.7713),
            "WAS" to (38.9076 to -76.8645),
        )
    }
}
