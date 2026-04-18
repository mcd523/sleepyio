package com.sleepyio.sleepyio.platform

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Desktop notifications fallback. Could be wired to `java.awt.SystemTray`
 * in a future phase — for now we just log so the surface stays callable
 * without pulling AWT into the compose desktop build.
 */
actual class NotificationCenter {
    actual suspend fun requestPermission(): PermissionResult = PermissionResult.GRANTED

    actual fun schedule(notification: LocalNotification) {
        logger.info {
            "[Notifications:jvm noop] id=${notification.id} title=${notification.title} " +
                "deliverAt=${notification.deliverAtEpochMs}"
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
