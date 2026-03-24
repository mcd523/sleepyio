package com.sleepyio.sleepyio.client.model.graphql

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

// GraphQL request/response structures
@Serializable
data class GraphQLRequest(
    @SerialName("operationName")
    val operationName: String,
    @SerialName("variables")
    val variables: JsonObject? = null,
    @SerialName("query")
    val query: String
)

@Serializable
data class GraphQLResponse<T>(
    @SerialName("data")
    val data: T? = null,
    @SerialName("errors")
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLError(
    @SerialName("message")
    val message: String,
    @SerialName("locations")
    val locations: List<GraphQLLocation>? = null,
    @SerialName("path")
    val path: List<String>? = null
)

@Serializable
data class GraphQLLocation(
    @SerialName("line")
    val line: Int,
    @SerialName("column")
    val column: Int
)

// Player news related structures
@Serializable
data class PlayerNewsData(
    @SerialName("news")
    val news: List<PlayerNews>
)

@Serializable
data class PlayerNews(
    @SerialName("metadata")
    val metadata: JsonObject? = null,
    @SerialName("player_id")
    val playerId: String? = null,
    @SerialName("published")
    val published: String? = null,
    @SerialName("source")
    val source: String? = null,
    @SerialName("source_key")
    val sourceKey: String? = null,
    @SerialName("sport")
    val sport: String? = null
)

// League standings related structures
@Serializable
data class MetadataData(
    @SerialName("metadata")
    val metadata: LeagueMetadata? = null
)

@Serializable
data class LeagueMetadata(
    @SerialName("key")
    val key: String? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("data")
    val data: LeagueHistoryData? = null,
    @SerialName("last_updated")
    val lastUpdated: Long? = null,
    @SerialName("created")
    val created: Long? = null
)

@Serializable
data class LeagueHistoryData(
    @SerialName("standings")
    val standings: List<LeagueStanding>? = null
)

@Serializable
data class LeagueStanding(
    @SerialName("roster_id")
    val rosterId: Int? = null,
    @SerialName("owner_id")
    val ownerId: String? = null,
    @SerialName("league_id")
    val leagueId: String? = null,
    @SerialName("season")
    val season: String? = null,
    @SerialName("wins")
    val wins: Int = 0,
    @SerialName("losses")
    val losses: Int = 0,
    @SerialName("ties")
    val ties: Int = 0,
    @SerialName("fpts")
    val fpts: Double = 0.0,
    @SerialName("fpts_decimal")
    val fptsDecimal: Double? = null,
    @SerialName("fpts_against")
    val fptsAgainst: Double? = null,
    @SerialName("fpts_against_decimal")
    val fptsAgainstDecimal: Double? = null,
    @SerialName("ppts")
    val ppts: Double? = null,
    @SerialName("ppts_decimal")
    val pptsDecimal: Double? = null
)

