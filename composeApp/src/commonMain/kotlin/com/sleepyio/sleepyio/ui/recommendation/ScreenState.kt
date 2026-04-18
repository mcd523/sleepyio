package com.sleepyio.sleepyio.ui.recommendation

/**
 * Canonical state envelope used by every recommendation-tab ViewModel.
 *
 * UI layers render one of four cases; the service layer never leaks exceptions
 * up — ViewModels wrap calls and translate failures into [Failed] so the UI
 * has a single, uniform switch to write against.
 */
sealed interface ScreenState<out T> {
    /** In-flight; UI shows a skeleton / spinner. */
    data object Loading : ScreenState<Nothing>

    /** Service returned successfully but produced no content (e.g. pre-season). */
    data object Empty : ScreenState<Nothing>

    /** Service returned a value to render. */
    data class Content<T>(val value: T) : ScreenState<T>

    /** Service failed; UI renders [message] and hooks up [retry] to a button. */
    data class Failed(
        val message: String,
        val retry: () -> Unit,
    ) : ScreenState<Nothing>
}
