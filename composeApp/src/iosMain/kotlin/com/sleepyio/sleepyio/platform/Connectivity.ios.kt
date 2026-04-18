package com.sleepyio.sleepyio.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue

/**
 * iOS [Connectivity] backed by `NWPathMonitor` from `Network.framework`.
 *
 * The monitor runs on a default-priority global GCD queue; updates are
 * funnelled into a [MutableStateFlow] so Compose/ViewModel code observes
 * on whatever coroutine dispatcher they choose.
 *
 * Lifetime: monitor is started once at construction and intentionally
 * never cancelled — [PlatformCapabilities] is process-scoped, so
 * "forever" is correct.
 */
@OptIn(ExperimentalForeignApi::class)
actual class Connectivity {

    private val _isOnline = MutableStateFlow(false)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Retain a strong reference so the native monitor isn't reclaimed.
    @Suppress("unused")
    private val monitor: Any? = nw_path_monitor_create()?.also { m ->
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        nw_path_monitor_set_queue(m, queue)
        nw_path_monitor_set_update_handler(m) { path ->
            _isOnline.value =
                path != null && nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_start(m)
    }
}
