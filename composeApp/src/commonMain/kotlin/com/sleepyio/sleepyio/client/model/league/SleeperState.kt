package com.sleepyio.sleepyio.client.model.league

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperState(
    @SerialName("week")
    val week: Long,
    @SerialName("season_type")
    val seasonType: String,
    @SerialName("season_start_date")
    val seasonStartDate: String?,
    @SerialName("season")
    val season: String,
    @SerialName("previous_season")
    val previousSeason: String,
    @SerialName("leg")
    val leg: Long,
    @SerialName("league_season")
    val leagueSeason: String,
    @SerialName("league_create_season")
    val leagueCreateSeason: String,
    @SerialName("display_week")
    val displayWeek: Long
)
