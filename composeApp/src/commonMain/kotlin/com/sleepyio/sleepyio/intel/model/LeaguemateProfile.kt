package com.sleepyio.sleepyio.intel.model

import com.sleepyio.sleepyio.client.model.graphql.LeagueStanding
import com.sleepyio.sleepyio.client.model.user.SleeperUser

data class LeaguemateProfile(
    val user: SleeperUser,
    val sharedLeagueCount: Int,
    val record: LeagueStanding?,
    val isCurrentOpponent: Boolean
)
