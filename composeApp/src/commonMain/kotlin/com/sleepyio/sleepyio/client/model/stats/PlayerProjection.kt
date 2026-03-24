package com.sleepyio.sleepyio.client.model.stats

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class PlayerProjection(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("player")
    val player: PlayerInfo? = null,
    @SerialName("stats")
    val stats: JsonObject? = null,
    @SerialName("season")
    val season: String? = null,
    @SerialName("week")
    val week: Int? = null,
    @SerialName("sport")
    val sport: String? = null,
    @SerialName("season_type")
    val seasonType: String? = null,
    @SerialName("category")
    val category: String? = null,
    @SerialName("company")
    val company: String? = null,
    @SerialName("game_id")
    val gameId: String? = null,
    @SerialName("opponent")
    val opponent: String? = null,
    @SerialName("team")
    val team: String? = null,
    @SerialName("date")
    val date: String? = null
)

@Serializable
data class WeeklyProjections(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("player")
    val player: PlayerInfo? = null,
    @SerialName("stats")
    val stats: JsonObject? = null,
    @SerialName("season")
    val season: String? = null,
    @SerialName("week")
    val week: Int? = null,
    @SerialName("sport")
    val sport: String? = null,
    @SerialName("season_type")
    val seasonType: String? = null,
    @SerialName("opponent")
    val opponent: String? = null,
    @SerialName("team")
    val team: String? = null,
    @SerialName("game_id")
    val gameId: String? = null,
    @SerialName("date")
    val date: String? = null
)
