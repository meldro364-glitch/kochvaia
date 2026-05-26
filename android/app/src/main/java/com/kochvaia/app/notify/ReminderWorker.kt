package com.kochvaia.app.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kochvaia.app.data.Role
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.repo.KidRepository
import com.kochvaia.app.data.repo.StarRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Daily check at ~21:00 local: for the current parent session, find any
 * kids that haven't received a star today and post a reminder notification.
 * Cancels any existing reminder if every kid has a star (so a notification
 * doesn't linger past the moment it became stale).
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionStore: SessionStore,
    private val kidsRepo: KidRepository,
    private val starRepo: StarRepository,
    private val notifier: ReminderNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = sessionStore.load()
        if (session?.role != Role.parent) {
            notifier.cancel()
            return Result.success()
        }

        val tz = runCatching { kidsRepo.me().family.tz }.getOrElse {
            // Transient network failure — let WorkManager retry within the
            // periodic window, but don't crash the chain.
            return Result.retry()
        }
        val today = LocalDate.now(ZoneId.of(tz)).toString()

        val kids = runCatching { kidsRepo.list() }.getOrElse { return Result.retry() }
        val missing = kids.filter { kid -> isMissingToday(kid, today) }

        if (missing.isEmpty()) notifier.cancel() else notifier.post(missing, today)
        return Result.success()
    }

    private suspend fun isMissingToday(kid: KidDto, today: String): Boolean {
        val status = runCatching {
            starRepo.days(kid.id, today, today).days.firstOrNull()?.status
        }.getOrNull()
        return status == "none"
    }
}
