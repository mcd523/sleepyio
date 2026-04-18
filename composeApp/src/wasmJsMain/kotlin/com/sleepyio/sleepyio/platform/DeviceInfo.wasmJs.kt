package com.sleepyio.sleepyio.platform

import kotlinx.browser.window

actual class DeviceInfo {
    actual val model: String = "Browser (Wasm)"
    actual val osVersion: String = window.navigator.userAgent
    actual val isTablet: Boolean = false
    actual val formFactor: FormFactor = FormFactor.WEB
}
