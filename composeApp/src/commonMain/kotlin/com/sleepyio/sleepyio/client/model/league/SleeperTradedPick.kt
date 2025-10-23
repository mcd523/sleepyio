package com.sleepyio.sleepyio.client.model.league

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperTradedPick(
    @SerialName("season")
    val season: String,
    @SerialName("round")
    val round: Long,
    @SerialName("roster_id")
    val rosterId: Long,
    @SerialName("previous_owner_id")
    val previousOwnerId: Long,
    @SerialName("owner_id")
    val ownerId: Long
)
