package com.sleepyio.sleepyio.platform

/**
 * Local (on-device) notification scheduling.
 *
 * The advisor needs to nudge the user at pre-game kickoff, when a player
 * news event flips a start/sit, and when waivers process. All of these
 * are local — the server doesn't push. Remote push is intentionally out
 * of scope for this capability; when we add FCM/APNs it will be a
 * separate `RemoteNotifications` capability so permission UX stays
 * coherent.
 *
 * Why expect/actual:
 * - Android 13+ requires a runtime POST_NOTIFICATIONS permission grant.
 * - iOS requires `UNUserNotificationCenter.requestAuthorization`.
 * - Desktop/Web simply log; the scheduler is a best-effort surface,
 *   never a throwing API.
 */
expect class NotificationCenter {
    /** Prompts the OS permission dialog if needed, returns the resolved state. */
    suspend fun requestPermission(): PermissionResult

    /**
     * Schedule a local notification for [LocalNotification.deliverAtEpochMs].
     * If the OS rejects the schedule (e.g. denied permission) the call is
     * silently dropped — use [requestPermission] first if it matters.
     */
    fun schedule(notification: LocalNotification)
}

/**
 * @property id stable user-space identifier; scheduling the same id replaces
 *   the previous pending notification.
 * @property deliverAtEpochMs absolute epoch-ms; implementations clamp past
 *   values to "now".
 * @property deepLink a `sleepyio://…` URI (see [DeepLink]) opened on tap, or
 *   null to just foreground the app.
 */
data class LocalNotification(
    val id: String,
    val title: String,
    val body: String,
    val deliverAtEpochMs: Long,
    val deepLink: String? = null,
)

enum class PermissionResult {
    /** User granted the permission. */
    GRANTED,

    /** User denied. Don't re-prompt without UI explaining why. */
    DENIED,

    /** Initial state — we haven't asked yet, or the OS has no concept of a prompt. */
    NOT_DETERMINED,
}
