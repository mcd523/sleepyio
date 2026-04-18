package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.PlayerInsight

/**
 * Thin wrapper that hides [InsightAggregator] behind the
 * [com.sleepyio.sleepyio.recommendation.RecommendationService] contract.
 *
 * The service interface exposes `playerInsight(playerId, week)` — having a
 * dedicated composer here means viewmodels talk to one class per analyzer
 * family, keeping the aggregator a pure data-layer concern.
 */
class PlayerInsightComposer(
    private val aggregator: InsightAggregator,
) {
    suspend fun compose(playerId: String, week: Int): PlayerInsight =
        aggregator.insightFor(playerId, week)
}
