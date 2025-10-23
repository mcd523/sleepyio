package com.sleepyio.sleepyio.client.model.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperPlayer(
    @SerialName("hashtag")
    val hashtag: String?,
    @SerialName("depth_chart_position")
    val depthChartPosition: String?,
    @SerialName("status")
    val status: String?,
    @SerialName("sport")
    val sport: String,
    @SerialName("fantasy_positions")
    val positions: List<String>?,
    @SerialName("number")
    val number: Long?,
    @SerialName("search_last_name")
    val searchLastName: String?,
    @SerialName("injury_start_date")
    val injuryStartDate: String?,
    @SerialName("weight")
    val weight: String?,
    @SerialName("position")
    val position: String?,
    @SerialName("practice_participation")
    val practiceParticipation: Map<String, String>?,
    @SerialName("sportradar_id")
    val sportRadarId: String?,
    @SerialName("team")
    val team: String?,
    @SerialName("last_name")
    val lastName: String?,
    @SerialName("college")
    val college: String?,
    @SerialName("fantasy_data_id")
    val fantasyDataId: Long?,
    @SerialName("injury_status")
    val injuryStatus: Map<String, String>?,
    @SerialName("player_id")
    val playerId: String,
    @SerialName("height")
    val height: String?,
    @SerialName("search_full_name")
    val searchFullName: String?,
    @SerialName("age")
    val age: Long?,
    @SerialName("stats_id")
    val statsId: String?,
    @SerialName("birth_country")
    val birthCountry: String?,
    @SerialName("espn_id")
    val espnId: String?,
    @SerialName("search_rank")
    val searchRank: Long?,
    @SerialName("first_name")
    val firstName: String?,
    @SerialName("depth_chart_order")
    val depthChartOrder: Long?,
    @SerialName("rotowire_id")
    val rotowireId: Long?,
    @SerialName("rotoworld_id")
    val rotoworldId: Long?,
    @SerialName("search_first_name")
    val searchFirstName: String?,
    @SerialName("yahoo_id")
    val yahooId: Long?,
)
