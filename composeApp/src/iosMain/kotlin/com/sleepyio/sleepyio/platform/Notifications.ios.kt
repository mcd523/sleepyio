package com.sleepyio.sleepyio.platform

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS [NotificationCenter] backed by `UNUserNotificationCenter`.
 *
 * Permission prompt uses `requestAuthorizationWithOptions`. Users who
 * tap "Don't allow" are recorded as DENIED; the OS won't re-show the
 * sheet — the app must deep-link to Settings (out of scope here).
 *
 * Scheduling uses `UNTimeIntervalNotificationTrigger` (relative delay)
 * instead of `UNCalendarNotificationTrigger` to avoid per-component
 * NSDateComponents interop. Deep-link payload lives in userInfo["deepLink"].
 */
actual class NotificationCenter {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    actual suspend fun requestPermission(): PermissionResult =
        suspendCancellableCoroutine { cont ->
            val options = UNAuthorizationOptionAlert or
                UNAuthorizationOptionBadge or
                UNAuthorizationOptionSound
            center.requestAuthorizationWithOptions(options) { granted, _ ->
                if (cont.isActive) {
                    cont.resume(if (granted) PermissionResult.GRANTED else PermissionResult.DENIED)
                }
            }
        }

    actual fun schedule(notification: LocalNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            notification.deepLink?.let { setUserInfo(mapOf<Any?, Any?>("deepLink" to it)) }
        }

        val delaySec = ((notification.deliverAtEpochMs - nowEpochMs()) / 1000.0).coerceAtLeast(1.0)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = delaySec,
            repeats = false,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = notification.id,
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request) { _ -> /* error silently ignored */ }
    }

    private fun nowEpochMs(): Long =
        (NSDate().timeIntervalSince1970 * 1000).toLong()

    @Suppress("unused")
    private fun mapAuthorizationStatus(status: Long): PermissionResult = when (status) {
        UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> PermissionResult.GRANTED
        UNAuthorizationStatusDenied -> PermissionResult.DENIED
        UNAuthorizationStatusNotDetermined -> PermissionResult.NOT_DETERMINED
        else -> PermissionResult.NOT_DETERMINED
    }
}
