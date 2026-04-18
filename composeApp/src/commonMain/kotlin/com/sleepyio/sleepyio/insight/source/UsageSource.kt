package com.sleepyio.sleepyio.insight.source

import com.sleepyio.sleepyio.insight.model.UsageTrend

/**
 * Data source for rolling 3-week usage metrics (snap %, target share, carries,
 * red-zone opps). Week-scoped so the aggregator can batch-fetch the whole
 * league at once. Fail soft with `emptyMap()` on error.
 */
interface UsageSource {
    suspend fun fetchUsage(week: Int): Map<String, UsageTrend>
}
