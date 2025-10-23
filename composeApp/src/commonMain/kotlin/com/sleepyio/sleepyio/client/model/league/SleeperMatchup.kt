package com.sleepyio.sleepyio.client.model.league

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperMatchup(
    @SerialName("starters")
    val starters: List<String>,
    @SerialName("roster_id")
    val rosterId: Long,
    @SerialName("players")
    val players: List<String>,
    @SerialName("matchup_id")
    val matchupId: Long,
    @SerialName("points")
    val points: Float,
    @SerialName("custom_points")
    val customPoints: Float?
)
