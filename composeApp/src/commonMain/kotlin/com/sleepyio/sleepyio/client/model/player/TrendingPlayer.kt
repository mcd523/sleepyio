package com.sleepyio.sleepyio.client.model.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TrendingPlayer(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("count")
    val count: Long
)
