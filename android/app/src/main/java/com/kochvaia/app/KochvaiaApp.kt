package com.kochvaia.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kochvaia.app.data.Role
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.notify.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KochvaiaApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Belt-and-suspenders re-arm on app start so an upgrade picks up the
        // daily alarm without waiting for the next reboot. BootCompletedReceiver
        // handles the reboot case. setExactAndAllowWhileIdle with the same
        // PendingIntent atomically replaces any existing alarm, so this is safe
        // to call repeatedly.
        if (sessionStore.load()?.role == Role.parent) {
            reminderScheduler.enableDailyReminder()
        }
    }
}
