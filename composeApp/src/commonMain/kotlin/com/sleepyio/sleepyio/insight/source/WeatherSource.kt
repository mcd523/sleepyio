package com.sleepyio.sleepyio.insight.source

import kotlinx.serialization.Serializable

/**
 * Data source for game-day weather forecasts at an NFL venue.
 *
 * Implementations take a team tricode (the home team hosting the game) plus a
 * kickoff timestamp and return a forecast window centered on kickoff. Dome
 * games short-circuit to a controlled constant — see [WeatherForecast.dome].
 *
 * Fail-soft contract: on network or parse error, return `null`.
 */
interface WeatherSource {
    suspend fun fetchForecast(gameVenueTeam: String, kickoffEpochMs: Long): WeatherForecast?
}

/**
 * Forecast snapshot used by the analyzers' weather factor. [windMph] is
 * integer mph; [precipitationPct] is probability of precip as a whole percent
 * 0..100; [temperatureF] is Fahrenheit. When [dome] is true the other values
 * are controlled constants and should not drive the weather factor.
 */
@Serializable
data class WeatherForecast(
    val windMph: Int,
    val precipitationPct: Int,
    val temperatureF: Int,
    val dome: Boolean,
)
