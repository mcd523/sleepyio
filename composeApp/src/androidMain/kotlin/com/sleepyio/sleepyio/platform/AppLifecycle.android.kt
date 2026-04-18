package com.sleepyio.sleepyio.platform

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android [AppLifecycle] backed by [ProcessLifecycleOwner], which fires
 * START/STOP at the *process* (not activity) granularity — matches what
 * the advisor means by "is the user looking at us".
 */
actual class AppLifecycle {

    private val _isForeground = MutableStateFlow(false)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                _isForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                _isForeground.value = false
            }
        }
        // Observer must be attached on the main thread; PlatformCapabilities
        // is constructed from MainActivity.onCreate so we're safe.
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }
}
