package com.kochvaia.app.data.cache

import com.kochvaia.app.data.remote.ApiService
import com.kochvaia.app.data.remote.MeResponse
import com.kochvaia.app.data.remote.SummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point that fetches the consolidated /dashboard payload and
 * fans it into the individual caches. Replaces the per-screen N+1 chains
 * (`/me + /kids + /kids/:id/summary x N + /items`) with one round-trip.
 *
 * The mutex makes concurrent calls coalesce: if two screens both call
 * `refresh()` at the same moment, the second waits for the first instead
 * of issuing a duplicate request.
 */
@Singleton
class DashboardLoader @Inject constructor(
    private val api: ApiService,
    private val meCache: MeCache,
    private val kidsCache: KidsCache,
    private val itemsCache: ItemsCache,
    private val summariesCache: SummariesCache,
) {
    private val mutex = Mutex()

    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        runCatching {
            val res = api.dashboard()

            // Reconstruct MeResponse from the role-typed sub-fields.
            val me = MeResponse(
                role = res.role,
                family = res.family,
                parent = res.parent,
                kid = res.kid,
            )
            meCache.put(me)
            kidsCache.put(res.kids)
            itemsCache.put(res.items)
            summariesCache.replaceAll(
                res.summaries.mapValues { (kidId, s) ->
                    SummaryResponse(
                        kidId = kidId,
                        availableStars = s.availableStars,
                        totalEarned = s.totalEarned,
                        totalUsed = s.totalUsed,
                    )
                },
            )
            _lastError.value = null
        }.onFailure { _lastError.value = it }
    }
}
