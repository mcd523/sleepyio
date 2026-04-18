package com.sleepyio.sleepyio.platform

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

/**
 * iOS [DeviceInfo].
 *
 * `model` is the marketing string (e.g. "iPhone"); we don't surface the
 * machine identifier (e.g. "iPhone15,3") because it requires `uname()`
 * interop and isn't worth the complexity for telemetry.
 */
actual class DeviceInfo {

    private val device = UIDevice.currentDevice

    actual val model: String = device.model

    actual val osVersion: String = "${device.systemName()} ${device.systemVersion}"

    actual val isTablet: Boolean = device.userInterfaceIdiom == UIUserInterfaceIdiomPad

    actual val formFactor: FormFactor = if (isTablet) FormFactor.TABLET else FormFactor.PHONE
}
