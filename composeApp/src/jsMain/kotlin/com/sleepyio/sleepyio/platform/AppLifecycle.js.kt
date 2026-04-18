package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Browser lifecycle fallback. A future impl could wire
 * `document.visibilitychange`. Today: always foreground.
 */
actual class AppLifecycle {
    private val _isForeground = MutableStateFlow(true)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
}
