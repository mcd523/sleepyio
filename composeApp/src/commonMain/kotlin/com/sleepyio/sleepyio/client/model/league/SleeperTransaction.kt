package com.sleepyio.sleepyio.client.model.league

import com.sleepyio.sleepyio.client.model.draft.SleeperPick
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperTransaction(
    @SerialName("type")
    val type: String,
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("status_updated")
    val updatedTime: Long,
    @SerialName("status")
    val status: String,
    @SerialName("roster_ids")
    val rosterIds: List<Int>,
    @SerialName("metadata")
    val metadata: Map<String, String>?,
    @SerialName("leg")
    val week: Int,
    @SerialName("drops")
    val drops: Map<String, String>?,
    @SerialName("draft_picks")
    val draftPicks: List<SleeperPick>,
    @SerialName("creator")
    val creator: String,
    @SerialName("created")
    val createdTime: Long,
    @SerialName("consenter_ids")
    val consenterIds: List<Int>,
    @SerialName("adds")
    val adds: Map<String, String>?,
    @SerialName("waiver_budget")
    val waiverBudget: List<FAABTransaction>
)
