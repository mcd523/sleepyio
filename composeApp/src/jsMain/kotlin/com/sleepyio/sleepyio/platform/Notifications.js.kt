package com.sleepyio.sleepyio.platform

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Browser notifications fallback. The real `Notification` Web API is
 * reachable from JS; wiring it is deferred to a phase-2 PWA story.
 */
actual class NotificationCenter {
    actual suspend fun requestPermission(): PermissionResult = PermissionResult.NOT_DETERMINED

    actual fun schedule(notification: LocalNotification) {
        logger.info { "[Notifications:js noop] id=${notification.id}" }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
