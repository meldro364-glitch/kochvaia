package com.kochvaia.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules — and cancels — the daily reminder alarm at 21:00 local. Uses
 * AlarmManager.setExactAndAllowWhileIdle so the alarm fires on time even
 * under Doze. The previous implementation used a PeriodicWorkRequest which
 * could be deferred by tens of minutes when the device was idle.
 *
 * AlarmManager one-shots don't repeat, so [ReminderAlarmReceiver] re-arms
 * the next day's alarm each time it fires. [BootCompletedReceiver] re-arms
 * after reboots (alarms don't survive a reboot).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager = context.getSystemService<AlarmManager>()!!

    fun enableDailyReminder() {
        // Cancel any leftover periodic work from the WorkManager-era scheduler.
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)

        val triggerAt = nextRunMillis(LocalTime.of(21, 0))
        val pi = alarmPendingIntent(context)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // SCHEDULE_EXACT_ALARM revoked on API 31-32 — fall back to inexact
            // rather than crashing. The user can re-grant in system settings.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun disableDailyReminder() {
        alarmManager.cancel(alarmPendingIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun nextRunMillis(target: LocalTime): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val todayAt = ZonedDateTime.of(LocalDate.now(now.zone), target, now.zone)
        val firstRun = if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
        return firstRun.toInstant().toEpochMilli()
    }

    private companion object {
        const val LEGACY_WORK_NAME = "daily_star_reminder"
        const val REQUEST_CODE = 0x53544152 // "STAR"

        fun alarmPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
