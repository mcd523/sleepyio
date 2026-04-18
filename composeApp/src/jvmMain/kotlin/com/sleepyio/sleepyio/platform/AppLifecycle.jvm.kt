package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop lifecycle fallback. The compose-desktop window has focus
 * events but the process-level "foreground" notion is ambiguous; we
 * always emit `true`. Refiners are free to plug a `Window.windowFocus`
 * listener in a later pass.
 */
actual class AppLifecycle {
    private val _isForeground = MutableStateFlow(true)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
}
