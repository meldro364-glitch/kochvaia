package com.kochvaia.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.kochvaia.app.MainActivity
import com.kochvaia.app.R
import com.kochvaia.app.data.remote.KidDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts (and cancels) the daily "no star yet today" reminder for a parent.
 *
 * The notification is grouped: one summary covering all kids that didn't
 * receive a star today, with up to 3 per-kid action buttons that award a
 * star inline without opening the app. If more than 3 kids are missing,
 * the remainder still appear in the body text and tapping the notification
 * opens the app where the parent can deal with them individually.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "daily_star_reminder"
        const val NOTIFICATION_ID = 0x57415253 // "WARS" — distinctive constant
        const val MAX_INLINE_ACTIONS = 3
    }

    private val nm: NotificationManager =
        context.getSystemService<NotificationManager>()!!

    init {
        ensureChannel()
    }

    fun post(missingKids: List<KidDto>, dateIso: String) {
        if (missingKids.isEmpty()) {
            cancel()
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val names = missingKids.joinToString(", ") { it.displayName }
        val title = "Star reminder"
        val body = if (missingKids.size == 1) {
            "${missingKids[0].displayName} hasn't received a star today"
        } else {
            "${missingKids.size} kids haven't received a star today: $names"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_star)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        missingKids.take(MAX_INLINE_ACTIONS).forEach { kid ->
            builder.addAction(
                R.drawable.ic_notif_star,
                "${kid.avatarEmoji} ${kid.displayName}",
                StarActionReceiver.pendingIntent(
                    context = context,
                    kidId = kid.id,
                    kidName = kid.displayName,
                    kidEmoji = kid.avatarEmoji,
                    dateIso = dateIso,
                ),
            )
        }

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Instant confirmation after a parent taps an action button. Replaces the
     * grouped reminder with a short "✓ Awarded" notification while the real
     * API call + state refresh runs in the background; that refresh ends with
     * either a fresh reminder for remaining kids or a cancel().
     */
    fun postAwarded(kidName: String, kidEmoji: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_star)
            .setContentTitle("$kidEmoji $kidName got a star!")
            .setContentText("Updating…")
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(8_000L)
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() {
        nm.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        // Channel creation is a no-op on Android 7 and below; safe to call.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily star reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Evening reminder when kids haven't received a star yet today."
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
