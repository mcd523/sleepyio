package com.sleepyio.sleepyio.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT

/**
 * iOS [Connectivity] backed by `NWPathMonitor` from `Network.framework`.
 *
 * The monitor runs on a default-priority global GCD queue; updates are
 * funnelled into a [MutableStateFlow] so Compose/ViewModel code observes
 * on whatever coroutine dispatcher they choose.
 *
 * Lifetime: monitor is started once at construction and intentionally
 * never cancelled — [PlatformCapabilities] is process-scoped, so
 * "forever" is correct. A finalizer/close hook is intentionally NOT
 * added; Kotlin/Native `deinit` ordering around shared code is a known
 * footgun we'd rather avoid.
 */
@OptIn(ExperimentalForeignApi::class)
actual class Connectivity {

    private val _isOnline = MutableStateFlow(false)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val monitor = nw_path_monitor_create()
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_set_update_handler(monitor) { path ->
            val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            _isOnline.value = satisfied
        }
        nw_path_monitor_start(monitor)
        // Retain the monitor by keeping a strong reference.
        retainedMonitor = monitor
    }

    @Suppress("unused")
    private var retainedMonitor: Any? = null

    /**
     * For tests / teardown hooks only. Not part of the common contract.
     */
    internal fun cancelForTests() {
        (retainedMonitor as? platform.Network.nw_path_monitor_t)?.let { nw_path_monitor_cancel(it) }
    }
}
