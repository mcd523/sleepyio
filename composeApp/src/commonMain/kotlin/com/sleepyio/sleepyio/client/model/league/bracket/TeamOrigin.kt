package com.sleepyio.sleepyio.client.model.league.bracket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamOrigin(
    @SerialName("w")
    val winnerFromMatchup: Int?,
    @SerialName("l")
    val loserFromMatchup: Int?
)
