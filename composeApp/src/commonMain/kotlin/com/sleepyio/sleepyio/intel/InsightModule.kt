package com.sleepyio.sleepyio.intel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sleepyio.sleepyio.client.model.league.SleeperLeague

interface InsightModule<T> {
    val id: String
    val displayName: String

    suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): T?

    @Composable
    fun Render(data: T, modifier: Modifier)
}
