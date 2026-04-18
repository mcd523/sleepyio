package com.sleepyio.sleepyio.platform

actual class DeviceInfo {
    actual val model: String = System.getProperty("os.name") ?: "Desktop"
    actual val osVersion: String =
        "${System.getProperty("os.name") ?: "unknown"} ${System.getProperty("os.version") ?: ""}".trim()
    actual val isTablet: Boolean = false
    actual val formFactor: FormFactor = FormFactor.DESKTOP
}
