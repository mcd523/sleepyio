package com.sleepyio.sleepyio.insight.model

/**
 * Opponent defense ranking vs position for a given week.
 *
 * [rankVsPos] follows NFL convention: 1 = toughest defense vs this position,
 * 32 = easiest. [fantasyPointsAllowedPerGame] is the raw per-game average the
 * defense has allowed to players at [position] in the trailing window used by
 * the data source (typically last 6 weeks — check the source documentation).
 */
data class DefenseVsPosition(
    val team: String,
    val position: String,
    val rankVsPos: Int,
    val fantasyPointsAllowedPerGame: Double,
)
