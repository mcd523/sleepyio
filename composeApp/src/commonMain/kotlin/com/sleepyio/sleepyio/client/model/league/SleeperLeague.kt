package com.sleepyio.sleepyio.client.model.league

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleeperLeague(
    @SerialName("total_rosters")
    val leagueSize: String,
    @SerialName("status")
    val status: String,
    @SerialName("sport")
    val sport: String,
    @SerialName("season")
    val season: String,
    @SerialName("name")
    val leagueName: String,
    @SerialName("league_id")
    val leagueId: Long,
    @SerialName("bracket_id")
    val bracketId: Long
)
