package com.sleepyio.sleepyio.insight.source.espn

/*
 * ESPN hidden news endpoint:
 *     https://site.api.espn.com/apis/site/v2/sports/football/nfl/news
 *
 * Response (abbreviated):
 *     { "articles": [ {
 *         "headline": "...",
 *         "description": "...",
 *         "published": "ISO-8601",
 *         "categories": [ { "type": "athlete", "athlete": { "id": 4361741 } } ]
 *     } ] }
 *
 * Player id filtering is client-side because the endpoint does not take a
 * player filter parameter.
 */

import com.sleepyio.sleepyio.insight.model.FantasyImpact
import com.sleepyio.sleepyio.insight.source.NewsItem
import com.sleepyio.sleepyio.insight.source.NewsSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/**
 * ESPN-backed [NewsSource]. Pulls the league-wide news feed once per call,
 * filters to the requested player by ESPN athlete id, then maps to
 * [NewsItem] with naive sentiment heuristics.
 *
 * Implementation note: this source identifies players by ESPN id rather than
 * Sleeper id. In the aggregator we pass the player's `espnId` when available.
 */
class EspnNewsSource : NewsSource {
    private val logger = KotlinLogging.logger {}

    override suspend fun fetchNewsForPlayer(playerId: String, sinceEpochMs: Long): List<NewsItem> {
        return try {
            val response: EspnNewsResponse = EspnHttp.client.get(URL).body()
            response.articles.orEmpty()
                .filter { article ->
                    article.categories.orEmpty().any { cat ->
                        cat.athlete?.id?.toString() == playerId || cat.athleteId?.toString() == playerId
                    }
                }
                .map { article ->
                    NewsItem(
                        playerId = playerId,
                        headline = article.headline.orEmpty(),
                        body = article.description.orEmpty(),
                        source = "ESPN",
                        publishedEpochMs = 0L, // not parsing ISO timestamps here
                        fantasyImpactHint = classifyImpact(
                            (article.headline.orEmpty() + " " + article.description.orEmpty())
                        ),
                    )
                }
        } catch (e: Exception) {
            logger.error(e) { "EspnNewsSource failed for player=$playerId; degrading to empty list" }
            emptyList()
        }
    }

    /**
     * Naive keyword-based fantasy impact classifier. Downstream aggregator may
     * override with stronger signals (official OUT designation, etc.).
     */
    internal fun classifyImpact(text: String): FantasyImpact {
        val lower = text.lowercase()
        val negative = listOf("ruled out", "ir", "injury", "concussion", "hamstring", "injured")
        val positive = listOf("returns", "cleared", "active", "expected to play", "practices in full")
        return when {
            negative.any { lower.contains(it) } -> FantasyImpact.NEGATIVE
            positive.any { lower.contains(it) } -> FantasyImpact.POSITIVE
            else -> FantasyImpact.NEUTRAL
        }
    }

    companion object {
        private const val URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/news?limit=50"
    }

    @Serializable
    private data class EspnNewsResponse(
        val articles: List<EspnArticle>? = null,
    )

    @Serializable
    private data class EspnArticle(
        val headline: String? = null,
        val description: String? = null,
        val published: String? = null,
        val categories: List<EspnCategory>? = null,
    )

    @Serializable
    private data class EspnCategory(
        val type: String? = null,
        val athleteId: Long? = null,
        val athlete: EspnCategoryAthlete? = null,
    )

    @Serializable
    private data class EspnCategoryAthlete(
        val id: Long? = null,
    )
}
