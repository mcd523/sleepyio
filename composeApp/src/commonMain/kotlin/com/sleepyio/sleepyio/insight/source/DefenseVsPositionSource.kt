package com.sleepyio.sleepyio.insight.source

import com.sleepyio.sleepyio.insight.model.DefenseVsPosition

/**
 * Data source for defense-vs-position rankings. One fetch returns every
 * team × position combo for the week so the aggregator avoids N+1 lookups.
 * Fail soft with `emptyList()` on error.
 */
interface DefenseVsPositionSource {
    suspend fun fetchDvp(week: Int): List<DefenseVsPosition>
}
