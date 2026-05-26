package com.kochvaia.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.repo.KidRepository
import com.kochvaia.app.data.repo.StarRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Triggered when the parent taps a per-kid "⭐ Name" action on the daily
 * reminder. Awards a star for the date the notification was issued for,
 * then refreshes the notification (or cancels it if no kids are missing
 * anymore).
 */
@AndroidEntryPoint
class StarActionReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var kidsRepo: KidRepository
    @Inject lateinit var starRepo: StarRepository
    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val kidId = intent.getStringExtra(EXTRA_KID_ID) ?: return
        val dateIso = intent.getStringExtra(EXTRA_DATE) ?: return
        val kidName = intent.getStringExtra(EXTRA_KID_NAME)
        val kidEmoji = intent.getStringExtra(EXTRA_KID_EMOJI)

        // Instant feedback — buzz the device and flash a "✓ Star awarded"
        // notification so the parent immediately knows the tap registered.
        // The real award + reminder refresh happen async below.
        hapticTap(context)
        if (kidName != null) {
            notifier.postAwarded(kidName, kidEmoji ?: "⭐")
        }

        // Broadcasts are killed after onReceive returns; goAsync lets us
        // finish the API work without dropping the process.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (sessionStore.load()?.role != com.kochvaia.app.data.Role.parent) return@launch
                runCatching { starRepo.award(kidId, dateIso) }
                refreshReminder(dateIso)
            } finally {
                pending.finish()
            }
        }
    }

    private fun hapticTap(context: Context) {
        val vib: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
        runCatching {
            vib?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private suspend fun refreshReminder(dateIso: String) {
        val tz = runCatching { kidsRepo.me().family.tz }.getOrDefault("UTC")
        val today = LocalDate.now(ZoneId.of(tz)).toString()
        // If the notification was for an older day (e.g. tapped past midnight),
        // we already awarded the star; just dismiss the notification.
        if (today != dateIso) {
            notifier.cancel()
            return
        }
        val kids = runCatching { kidsRepo.list() }.getOrDefault(emptyList())
        val missing = kids.filter { kid ->
            runCatching {
                starRepo.days(kid.id, today, today).days.firstOrNull()?.status
            }.getOrNull() == "none"
        }
        if (missing.isEmpty()) notifier.cancel() else notifier.post(missing, today)
    }

    companion object {
        const val ACTION_AWARD = "com.kochvaia.app.action.AWARD_STAR"
        const val EXTRA_KID_ID = "kid_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_KID_NAME = "kid_name"
        const val EXTRA_KID_EMOJI = "kid_emoji"

        fun pendingIntent(
            context: Context,
            kidId: String,
            kidName: String,
            kidEmoji: String,
            dateIso: String,
        ): PendingIntent {
            val intent = Intent(context, StarActionReceiver::class.java).apply {
                action = ACTION_AWARD
                putExtra(EXTRA_KID_ID, kidId)
                putExtra(EXTRA_DATE, dateIso)
                putExtra(EXTRA_KID_NAME, kidName)
                putExtra(EXTRA_KID_EMOJI, kidEmoji)
            }
            // Unique requestCode per (kid, date) so different kids' actions
            // don't collide in PendingIntent caches.
            val requestCode = (kidId + dateIso).hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
