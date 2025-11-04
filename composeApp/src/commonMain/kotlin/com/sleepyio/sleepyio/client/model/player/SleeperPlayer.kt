package com.sleepyio.sleepyio.client.model.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SleeperPlayer(
    @SerialName("hashtag")
    val hashtag: String? = null,
    @SerialName("depth_chart_position")
    val depthChartPosition: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("sport")
    val sport: String = "nfl",
    @SerialName("fantasy_positions")
    val positions: List<String>? = null,
    @SerialName("number")
    val number: Long? = null,
    @SerialName("search_last_name")
    val searchLastName: String? = null,
    @SerialName("injury_start_date")
    val injuryStartDate: String? = null,
    @SerialName("weight")
    val weight: String? = null,
    @SerialName("position")
    val position: String? = null,
    @SerialName("practice_participation")
    val practiceParticipation: String? = null,
    @SerialName("sportradar_id")
    val sportRadarId: String? = null,
    @SerialName("team")
    val team: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("college")
    val college: String? = null,
    @SerialName("fantasy_data_id")
    val fantasyDataId: Long? = null,
    @SerialName("injury_status")
    val injuryStatus: String? = null,
    @SerialName("player_id")
    val playerId: String = "",
    @SerialName("height")
    val height: String? = null,
    @SerialName("search_full_name")
    val searchFullName: String? = null,
    @SerialName("age")
    val age: Long? = null,
    @SerialName("stats_id")
    val statsId: String? = null,
    @SerialName("birth_country")
    val birthCountry: String? = null,
    @SerialName("espn_id")
    val espnId: String? = null,
    @SerialName("search_rank")
    val searchRank: Long? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("depth_chart_order")
    val depthChartOrder: Long? = null,
    @SerialName("rotowire_id")
    val rotowireId: Long? = null,
    @SerialName("rotoworld_id")
    val rotoworldId: Long? = null,
    @SerialName("search_first_name")
    val searchFirstName: String? = null,
    @SerialName("yahoo_id")
    val yahooId: Long? = null,
)
