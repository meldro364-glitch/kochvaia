package com.kochvaia.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kochvaia.app.data.Role
import com.kochvaia.app.data.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms the daily reminder alarm after device reboot. AlarmManager drops
 * every scheduled alarm across a reboot, so without this the parent would
 * silently lose their nightly reminder until they next opened the app.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (sessionStore.load()?.role == Role.parent) {
            scheduler.enableDailyReminder()
        }
    }
}
