package com.sleepyio.sleepyio.platform

actual class NotificationCenter {
    actual suspend fun requestPermission(): PermissionResult = PermissionResult.NOT_DETERMINED

    actual fun schedule(notification: LocalNotification) {
        println("[Notifications:wasmJs noop] id=${notification.id}")
    }
}
