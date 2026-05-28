package com.kochvaia.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kochvaia.app.data.Role
import com.kochvaia.app.data.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires at 21:00 local. Enqueues a one-shot ReminderWorker (so the network
 * + notification logic runs with the same Hilt-injected dependencies as
 * before) and re-arms tomorrow's alarm. AlarmManager one-shots don't
 * repeat — re-arming on every fire is what keeps the daily cadence going.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        if (sessionStore.load()?.role == Role.parent) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>().build(),
            )
        }
        // Re-arm regardless of session state — if the parent signs back in
        // before tomorrow, the worker just runs as normal; otherwise it
        // no-ops in doWork() and the cost is one cheap alarm per day.
        scheduler.enableDailyReminder()
    }

    companion object {
        const val ACTION_FIRE = "com.kochvaia.app.action.FIRE_REMINDER"
    }
}
