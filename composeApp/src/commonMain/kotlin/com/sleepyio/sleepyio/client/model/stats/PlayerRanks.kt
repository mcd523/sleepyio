package com.sleepyio.sleepyio.client.model.stats

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PlayerRanks(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("rank_ppr")
    val rankPpr: Int? = null,
    @SerialName("pos_rank_ppr")
    val posRankPpr: Int? = null
)

@Serializable
data class RankedPlayer(
    @SerialName("player_id")
    val playerId: String,
    @SerialName("player")
    val player: PlayerInfo? = null,
    @SerialName("stats")
    val stats: RankStats? = null
)

@Serializable
data class RankStats(
    @SerialName("rank_ppr")
    val rankPpr: Int? = null,
    @SerialName("pos_rank_ppr")
    val posRankPpr: Int? = null,
    @SerialName("pts_ppr")
    val ptsPpr: Double? = null
)

