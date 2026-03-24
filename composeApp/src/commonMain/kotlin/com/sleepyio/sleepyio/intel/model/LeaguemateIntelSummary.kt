package com.sleepyio.sleepyio.intel.model

import com.sleepyio.sleepyio.client.model.graphql.LeagueStanding
import com.sleepyio.sleepyio.client.model.user.SleeperUser

data class LeaguemateIntelSummary(
    val user: SleeperUser,
    val record: LeagueStanding?,
    val h2hWins: Int,
    val h2hLosses: Int,
    val h2hSeasons: Int,
    val scoringTrend: String,
    val avgPointsPerWeek: Float,
    val recentTransactionCount: Int,
    val isCurrentOpponent: Boolean
)
