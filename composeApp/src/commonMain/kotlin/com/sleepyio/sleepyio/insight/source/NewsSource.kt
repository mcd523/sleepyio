package com.sleepyio.sleepyio.insight.source

import com.sleepyio.sleepyio.insight.model.FantasyImpact
import kotlinx.serialization.Serializable

/**
 * Data source for per-player fantasy-relevant news items.
 *
 * Implementations typically call a beat-writer feed (ESPN hidden API,
 * RotoWorld, etc.) and surface only items newer than [sinceEpochMs]. Callers
 * use [sinceEpochMs] to avoid re-processing items they already have cached.
 *
 * Fail-soft contract: on network or parse error, return `emptyList()`.
 */
interface NewsSource {
    suspend fun fetchNewsForPlayer(playerId: String, sinceEpochMs: Long): List<NewsItem>
}

/**
 * Wire DTO for a single news item. [fantasyImpactHint] is the source's own
 * guess at the direction of impact (if any); the aggregator may override it
 * based on stronger signals (official designation change, etc.).
 */
@Serializable
data class NewsItem(
    val playerId: String,
    val headline: String,
    val body: String,
    val source: String,
    val publishedEpochMs: Long,
    val fantasyImpactHint: FantasyImpact?,
)
