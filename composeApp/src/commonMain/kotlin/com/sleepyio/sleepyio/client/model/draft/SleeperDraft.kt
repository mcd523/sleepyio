package com.sleepyio.sleepyio.client.model.draft

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperDraft(
    @SerialName("type")
    val type: String,
    @SerialName("status")
    val status: String,
    @SerialName("start_time")
    val startTime: Long,
    @SerialName("sport")
    val sport: String,
    
    @SerialName("settings")
    val settings: Map<String, String>?,
    @SerialName("season_type")
    val seasonType: String,
    @SerialName("season")
    val season: String,
    @SerialName("metadata")
    val metadata: Map<String, String>?,
    @SerialName("league_id")
    val leagueId: String,
    @SerialName("last_picked")
    val last_picked: Long,
    @SerialName("last_message_time")
    val lastMessageTime: Long,
    @SerialName("last_message_id")
    val lastMessageId: String,
    @SerialName("draft_order")
    val draftOrder: Map<String, String>?,
    @SerialName("draft_id")
    val draftId: String,
    @SerialName("creators")
    val creators: Map<String, String>?,
    @SerialName("created")
    val created: Long,
)
