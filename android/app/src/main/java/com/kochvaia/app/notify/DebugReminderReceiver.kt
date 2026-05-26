package com.kochvaia.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kochvaia.app.BuildConfig

/**
 * Debug-only: lets us trigger the reminder worker on demand from adb so we
 * don't have to wait until 21:00 to test.
 *
 *   adb shell am broadcast -a com.kochvaia.app.debug.TRIGGER_REMINDER \
 *     -n com.kochvaia.app.debug/com.kochvaia.app.notify.DebugReminderReceiver
 *
 * Gated by BuildConfig.DEBUG so it can't be reached in release builds.
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }
}
