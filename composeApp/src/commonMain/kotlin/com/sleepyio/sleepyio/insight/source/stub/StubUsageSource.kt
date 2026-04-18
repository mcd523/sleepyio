package com.sleepyio.sleepyio.insight.source.stub

import com.sleepyio.sleepyio.insight.model.UsageTrend
import com.sleepyio.sleepyio.insight.source.UsageSource

/**
 * Stub [UsageSource] — deterministic values so analyzers compile, run, and
 * unit-test without a real snap-count feed.
 *
 * TODO: wire a real usage source when available (PFR game logs, FantasyPros
 * advanced stats, NFL Next Gen). Until then, this returns an empty map so
 * downstream factors that depend on usage contribute nothing rather than lying
 * with fabricated data. Tests can substitute a richer stub via constructor.
 */
class StubUsageSource(
    private val overrides: Map<String, UsageTrend> = emptyMap(),
) : UsageSource {
    override suspend fun fetchUsage(week: Int): Map<String, UsageTrend> = overrides
}
