package com.sleepyio.sleepyio.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android [Connectivity] backed by [ConnectivityManager.NetworkCallback].
 *
 * The callback is registered once, for the process lifetime, because the
 * host [PlatformCapabilities] is itself process-scoped. We intentionally
 * do NOT unregister: the cost is negligible and re-registering on every
 * screen entry would be a correctness footgun.
 *
 * We report online ONLY when the active network is VALIDATED
 * (has internet, not a captive portal) — matches what the advisor UI
 * actually cares about.
 */
actual class Connectivity(context: Context) {

    private val _isOnline = MutableStateFlow(false)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (cm != null) {
            _isOnline.value = cm.hasValidated()

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = cm.hasValidated()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
            }
            try {
                cm.registerNetworkCallback(request, callback)
            } catch (_: SecurityException) {
                // Some OEMs throw on restricted networks; fall back to last-known value.
            }
        }
    }

    private fun ConnectivityManager.hasValidated(): Boolean {
        val active = activeNetwork ?: return false
        val caps = getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
