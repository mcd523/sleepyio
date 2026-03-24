package com.sleepyio.sleepyio.intel.model

import com.sleepyio.sleepyio.client.model.league.SleeperLeague

data class OpponentLeague(
    val league: SleeperLeague,
    val isShared: Boolean,
    val season: String
)
