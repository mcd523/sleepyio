package com.sleepyio.sleepyio.platform

import android.content.res.Configuration
import android.os.Build

/**
 * Android [DeviceInfo].
 *
 * `isTablet` uses Android's documented `smallestScreenWidthDp >= 600` heuristic.
 * `formFactor` biases towards TABLET/PHONE; we don't yet detect foldables
 * (would need `WindowLayoutInfo` from `androidx.window`) — follow-up.
 */
actual class DeviceInfo {

    actual val model: String = "${Build.MANUFACTURER} ${Build.MODEL}"

    actual val osVersion: String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    actual val isTablet: Boolean = run {
        val config: Configuration = AndroidContextProvider.requireContext().resources.configuration
        config.smallestScreenWidthDp >= TABLET_SW_DP
    }

    actual val formFactor: FormFactor = if (isTablet) FormFactor.TABLET else FormFactor.PHONE

    private companion object {
        const val TABLET_SW_DP = 600
    }
}
