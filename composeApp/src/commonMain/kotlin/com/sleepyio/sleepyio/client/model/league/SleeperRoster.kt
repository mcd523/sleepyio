package com.sleepyio.sleepyio.client.model.league

import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperRoster(
    @SerialName("starters")
    val starters: List<String>,
    @SerialName("roster_id")
    val rosterId: Int,
    @SerialName("players")
    val players: List<String> = listOf(),
    @SerialName("owner_id")
    val ownerId: Long?,
    @SerialName("league_id")
    val leagueId: Long,

    val fullStarters: List<SleeperPlayer> = listOf(),
    val fullPlayers: List<SleeperPlayer> = listOf()
)
