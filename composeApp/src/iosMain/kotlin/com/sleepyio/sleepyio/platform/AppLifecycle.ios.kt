package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * iOS [AppLifecycle] driven by `UIApplication` notifications.
 *
 * "Active" (not just "foreground") is the signal we want: when a
 * system alert or the share sheet covers the app the user is no longer
 * interacting with us and long polls should pause.
 */
actual class AppLifecycle {

    private val _isForeground = MutableStateFlow(true)
    actual val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = queue,
        ) { _isForeground.value = true }
        center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = queue,
        ) { _isForeground.value = false }
    }
}
