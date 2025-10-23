package com.sleepyio.sleepyio.client.model.league.bracket

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PlayoffMatchup(
    @SerialName("r")
    val round: Int,
    @SerialName("m")
    val matchupId: Int,
    @SerialName("t1")
    val team1: Int,
    @SerialName("t2")
    val team2: Int,
    @SerialName("w")
    val winnerRosterId: Int?,
    @SerialName("l")
    val loserRosterId: Int?,
    @SerialName("t1_from")
    val team1Origin: TeamOrigin?,
    @SerialName("t2_from")
    val team2Origin: TeamOrigin?
)
