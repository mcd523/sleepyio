package com.sleepyio.sleepyio.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.recommendation.RecommendationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads a single [PlayerInsight] for a given player+week. Starts [Empty] —
 * the user must pick a player before anything renders.
 */
class PlayerInsightViewModel(
    private val service: RecommendationService,
) : ViewModel() {
    private val _state = MutableStateFlow<ScreenState<PlayerInsight>>(ScreenState.Empty)
    val state: StateFlow<ScreenState<PlayerInsight>> = _state.asStateFlow()

    fun load(playerId: String, week: Int) {
        if (playerId.isBlank()) {
            _state.value = ScreenState.Empty
            return
        }
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            try {
                val insight = service.playerInsight(playerId, week)
                _state.value = ScreenState.Content(insight)
            } catch (e: Exception) {
                _state.value = ScreenState.Failed(
                    message = "Couldn't load player insight: ${e.message ?: "unknown error"}",
                    retry = { load(playerId, week) },
                )
            }
        }
    }

    fun clear() {
        _state.value = ScreenState.Empty
    }
}
