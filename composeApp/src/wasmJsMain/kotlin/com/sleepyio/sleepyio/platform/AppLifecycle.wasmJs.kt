package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class AppLifecycle {
    private val _isForeground = MutableStateFlow(true)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
}
