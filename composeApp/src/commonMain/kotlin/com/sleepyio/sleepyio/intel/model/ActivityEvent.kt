package com.sleepyio.sleepyio.intel.model

import com.sleepyio.sleepyio.client.model.player.SleeperPlayer

enum class ActivityType { TRADE, WAIVER_CLAIM, FREE_AGENT_ADD, DROP }

data class ActivityEvent(
    val type: ActivityType,
    val userId: String,
    val userName: String,
    val week: Int,
    val timestamp: Long,
    val playersAdded: List<SleeperPlayer>,
    val playersDropped: List<SleeperPlayer>,
    val tradePartnerUserId: String?,
    val tradePartnerName: String?
)
