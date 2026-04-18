package com.sleepyio.sleepyio.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.model.StartSitRecommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads a head-to-head [StartSitRecommendation] for two player IDs at a given
 * week. Until the user submits a pair, state stays [ScreenState.Empty].
 */
class StartSitViewModel(
    private val service: RecommendationService,
) : ViewModel() {
    private val _state =
        MutableStateFlow<ScreenState<StartSitRecommendation>>(ScreenState.Empty)
    val state: StateFlow<ScreenState<StartSitRecommendation>> = _state.asStateFlow()

    fun load(playerAId: String, playerBId: String, week: Int) {
        if (playerAId.isBlank() || playerBId.isBlank()) {
            _state.value = ScreenState.Empty
            return
        }
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            try {
                val rec = service.startSit(playerAId, playerBId, week)
                _state.value = ScreenState.Content(rec)
            } catch (e: Exception) {
                _state.value = ScreenState.Failed(
                    message = "Couldn't compare players: ${e.message ?: "unknown error"}",
                    retry = { load(playerAId, playerBId, week) },
                )
            }
        }
    }

    fun clear() {
        _state.value = ScreenState.Empty
    }
}
