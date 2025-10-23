package com.sleepyio.sleepyio.client.model.draft

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperPick(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("picked_by")
    val pickedBy: String,
    @SerialName("roster_id")
    val rosterId: String,
    @SerialName("round")
    val round: Long,
    @SerialName("draft_slot")
    val draftSlot: Long,
    @SerialName("pick_no")
    val pickNumber: Long,
    @SerialName("metadata")
    val metadata: Map<String, String>?,
    @SerialName("is_keeper")
    val isKeeper: Map<String, String>?,
    @SerialName("draft_id")
    val draftId: String
)
