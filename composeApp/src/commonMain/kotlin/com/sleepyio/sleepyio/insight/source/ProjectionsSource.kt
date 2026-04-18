package com.sleepyio.sleepyio.insight.source

import com.sleepyio.sleepyio.insight.model.ProjectionRange

/**
 * Data source for weekly fantasy point projections keyed by player id. Returns
 * a full week's worth in one call so the aggregator can amortise one fetch
 * across an entire roster. Fail soft with `emptyMap()` on error.
 */
interface ProjectionsSource {
    suspend fun fetchProjections(week: Int): Map<String, ProjectionRange>
}
