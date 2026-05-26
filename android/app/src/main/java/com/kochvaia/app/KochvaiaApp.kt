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
        // Re-arm the daily reminder after upgrade / device reboot, but only
        // if there's an active parent session. enqueueUniquePeriodicWork with
        // KEEP makes this idempotent.
        if (sessionStore.load()?.role == Role.parent) {
            reminderScheduler.enableDailyReminder()
        }
    }
}
