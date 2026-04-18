package com.sleepyio.sleepyio.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.model.WeeklyReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads and exposes the [WeeklyReport] for a given (league, roster, week).
 *
 * Failures from the service are caught and translated into
 * [ScreenState.Failed] so the UI has a single switch to render.
 */
class WeeklyReportViewModel(
    private val service: RecommendationService,
) : ViewModel() {
    private val _state = MutableStateFlow<ScreenState<WeeklyReport>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<WeeklyReport>> = _state.asStateFlow()

    fun load(leagueId: Long, rosterId: Long, week: Int) {
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            try {
                val report = service.weeklyReport(leagueId, rosterId, week)
                _state.value = ScreenState.Content(report)
            } catch (e: Exception) {
                _state.value = ScreenState.Failed(
                    message = "Couldn't load weekly report: ${e.message ?: "unknown error"}",
                    retry = { load(leagueId, rosterId, week) },
                )
            }
        }
    }
}
