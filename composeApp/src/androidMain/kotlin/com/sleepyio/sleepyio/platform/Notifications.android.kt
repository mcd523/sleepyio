package com.sleepyio.sleepyio.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android [NotificationCenter].
 *
 * Delivery model:
 * - For immediate / near-immediate notifications we post through
 *   [NotificationManagerCompat].
 * - For future-dated notifications a production impl would use
 *   `AlarmManager.setExactAndAllowWhileIdle` + a `BroadcastReceiver`
 *   that re-posts on fire; that wiring lives in a future phase-2 task
 *   (see MOBILE_ARCHITECTURE.md "Background refresh strategy"). For
 *   now anything in the future is dropped with a tagged log so
 *   call-sites can unit-test the happy path.
 *
 * Permission model:
 * - Android 13 (API 33) introduced runtime POST_NOTIFICATIONS.
 * - [requestPermission] reports the current granted state. The
 *   interactive Activity-side prompt is the host's job; when the
 *   caller gets NOT_DETERMINED it should show UI and request the
 *   permission via the standard launcher pattern.
 * - Pre-33 returns GRANTED because the permission is install-time.
 */
actual class NotificationCenter {

    private val context: Context get() = AndroidContextProvider.requireContext()
    private val manager get() = NotificationManagerCompat.from(context)

    init {
        ensureChannel()
    }

    actual suspend fun requestPermission(): PermissionResult = suspendCancellableCoroutine { cont ->
        cont.resume(currentPermission())
    }

    actual fun schedule(notification: LocalNotification) {
        if (currentPermission() != PermissionResult.GRANTED) return

        val delayMs = notification.deliverAtEpochMs - System.currentTimeMillis()
        if (delayMs > IMMEDIATE_THRESHOLD_MS) {
            // Future-dated: would hand off to AlarmManager in phase-2.
            return
        }

        val intent = notification.deepLink?.let { link ->
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                `package` = context.packageName
            }
        }
        val pending = intent?.let {
            PendingIntent.getActivity(
                context,
                notification.id.hashCode(),
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        pending?.let(builder::setContentIntent)

        try {
            manager.notify(notification.id.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // User revoked permission between check and notify; degrade silently.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Advisor alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Lineup, waiver, and player-news nudges."
        }
        val sys = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sys.createNotificationChannel(channel)
    }

    private fun currentPermission(): PermissionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionResult.GRANTED
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionResult.GRANTED else PermissionResult.NOT_DETERMINED
    }

    private companion object {
        const val CHANNEL_ID = "sleepyio_advisor"
        const val IMMEDIATE_THRESHOLD_MS = 5_000L
    }
}
