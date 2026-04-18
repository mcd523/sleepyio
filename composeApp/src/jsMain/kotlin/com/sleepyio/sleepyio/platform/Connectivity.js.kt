package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Browser connectivity fallback. A future impl should subscribe to
 * `window.addEventListener("online"/"offline")` and seed from
 * `navigator.onLine`. For now we assume online so UI never falsely
 * disables itself in dev.
 */
actual class Connectivity {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
}
