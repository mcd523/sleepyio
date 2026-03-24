package com.sleepyio.sleepyio.intel.model

data class MatchupResult(
    val week: Int,
    val myPoints: Float,
    val theirPoints: Float,
    val won: Boolean,
    val season: String? = null
)
