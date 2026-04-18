package com.sleepyio.sleepyio.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.model.WaiverTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the week's [WaiverTarget] list for a league+roster. Empty list from
 * the service collapses to [ScreenState.Empty] so the UI can differentiate
 * "no recommended adds" from "still loading".
 */
class WaiversViewModel(
    private val service: RecommendationService,
) : ViewModel() {
    private val _state = MutableStateFlow<ScreenState<List<WaiverTarget>>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<List<WaiverTarget>>> = _state.asStateFlow()

    fun load(leagueId: Long, rosterId: Long, week: Int) {
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            try {
                val targets = service.waiverTargets(leagueId, rosterId, week)
                _state.value = if (targets.isEmpty()) {
                    ScreenState.Empty
                } else {
                    ScreenState.Content(targets)
                }
            } catch (e: Exception) {
                _state.value = ScreenState.Failed(
                    message = "Couldn't load waiver targets: ${e.message ?: "unknown error"}",
                    retry = { load(leagueId, rosterId, week) },
                )
            }
        }
    }
}
