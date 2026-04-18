package com.sleepyio.sleepyio.insight.source

import kotlinx.serialization.Serializable

/**
 * Data source for weekly NFL injury reports.
 *
 * Exists as an interface so implementations (ESPN scrape, paid feed, unit-test
 * stub) can be swapped without touching the aggregator or analyzers. Every
 * implementation must fail soft — network/parse errors should degrade to
 * `emptyList()` rather than throwing.
 */
interface InjurySource {
    suspend fun fetchInjuries(week: Int): List<InjuryReport>
}

/**
 * Wire DTO carrying a single player's injury designation for a week.
 *
 * Fields mirror what the NFL official injury report publishes: [designation] is
 * the raw status string (Q / D / O / IR / "Questionable"), [bodyPart] is
 * optional free text, [practiceStatus] is the DNP / LP / FP trend indicator,
 * and [lastUpdatedEpochMs] is the publication timestamp so downstream logic can
 * weight recency.
 */
@Serializable
data class InjuryReport(
    val playerId: String,
    val designation: String?,
    val bodyPart: String?,
    val practiceStatus: String?,
    val lastUpdatedEpochMs: Long?,
)
