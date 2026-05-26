package com.kochvaia.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules — and cancels — the daily reminder worker. v1 hardcodes 21:00
 * in the device's local timezone (good enough for a household app; the
 * family TZ may differ but rarely in practice).
 *
 * PeriodicWorkRequest has a 15-minute flex window, so the worker fires
 * within ~15 minutes of 21:00. Doze mode can defer further.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enableDailyReminder() {
        val initialDelay = initialDelayUntil(LocalTime.of(21, 0))
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // KEEP: don't reset the schedule on every app launch; if the user
            // upgrades or restarts the app the existing schedule survives.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disableDailyReminder() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private fun initialDelayUntil(target: LocalTime, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Duration {
        val todayAt = ZonedDateTime.of(LocalDate.now(now.zone), target, now.zone)
        val firstRun = if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
        return Duration.between(now, firstRun)
    }

    private companion object {
        const val UNIQUE_NAME = "daily_star_reminder"
    }
}
