package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop [Connectivity] fallback. JVM has no first-class "is internet
 * reachable" signal; we default to `true` so the UI doesn't gate actions
 * on a false negative. A future impl could probe `NetworkInterface.isUp`
 * + a cached HEAD to sleeper.app, but that belongs in the backend story.
 */
actual class Connectivity {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
}
