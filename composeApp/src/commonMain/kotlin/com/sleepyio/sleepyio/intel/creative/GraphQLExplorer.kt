package com.sleepyio.sleepyio.intel.creative

import com.sleepyio.sleepyio.client.SleeperClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val logger = KotlinLogging.logger("GraphQLExplorer")

data class GraphQLExplorationResult(
    val availableQueries: List<String>,
    val availableMutations: List<String>,
    val interestingFields: List<String>,
    val error: String?
)

private val INTERESTING_KEYWORDS = listOf(
    "activity", "chat", "online", "last_seen", "login",
    "notification", "message", "status", "presence", "session"
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Attempts GraphQL introspection on the Sleeper GraphQL endpoint to discover
 * undocumented queries and mutations. This is best-effort and gracefully
 * handles introspection being disabled.
 */
object GraphQLExplorer {

    private const val INTROSPECTION_QUERY = """
        query IntrospectionQuery {
            __schema {
                queryType {
                    fields {
                        name
                        description
                    }
                }
                mutationType {
                    fields {
                        name
                        description
                    }
                }
            }
        }
    """

    /**
     * Explore the Sleeper GraphQL schema via introspection.
     * Returns discovered queries, mutations, and interesting fields.
     * Returns an empty result with error message if introspection is disabled.
     */
    suspend fun explore(): GraphQLExplorationResult {
        return try {
        val rawResponse = SleeperClient.executeGraphQL(
            query = INTROSPECTION_QUERY.trimIndent(),
            operationName = "IntrospectionQuery"
        )

        if (rawResponse == null) {
            return GraphQLExplorationResult(
                availableQueries = emptyList(),
                availableMutations = emptyList(),
                interestingFields = emptyList(),
                error = "GraphQL request failed — no response"
            )
        }

        val responseJson = try {
            json.parseToJsonElement(rawResponse).jsonObject
        } catch (e: Exception) {
            return GraphQLExplorationResult(
                availableQueries = emptyList(),
                availableMutations = emptyList(),
                interestingFields = emptyList(),
                error = "Failed to parse GraphQL response: ${e.message}"
            )
        }

        // Check for errors in response
        val errors = responseJson["errors"]?.jsonArray
        if (errors != null && errors.isNotEmpty()) {
            val errorMsg = errors.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonPrimitive?.content
                ?: "Unknown GraphQL error"
            return GraphQLExplorationResult(
                availableQueries = emptyList(),
                availableMutations = emptyList(),
                interestingFields = emptyList(),
                error = errorMsg
            )
        }

        val data = responseJson["data"]?.jsonObject
        val schema = data?.get("__schema")?.jsonObject

        val queryFields = schema?.get("queryType")?.jsonObject
            ?.get("fields")?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            ?: emptyList()

        val mutationFields = schema?.get("mutationType")?.jsonObject
            ?.get("fields")?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            ?: emptyList()

        val allFields = queryFields + mutationFields
        val interesting = allFields.filter { field ->
            INTERESTING_KEYWORDS.any { keyword -> field.lowercase().contains(keyword) }
        }

        GraphQLExplorationResult(
            availableQueries = queryFields,
            availableMutations = mutationFields,
            interestingFields = interesting,
            error = null
        )
    } catch (e: Exception) {
        logger.error(e) { "GraphQLExplorer.explore failed" }
        GraphQLExplorationResult(
            availableQueries = emptyList(),
            availableMutations = emptyList(),
            interestingFields = emptyList(),
            error = "Exploration failed: ${e.message}"
            )
        }
    }
}
