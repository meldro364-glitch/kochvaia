package com.kochvaia.app.data.repo

import com.kochvaia.app.data.remote.ApiService
import com.kochvaia.app.data.remote.AwardStarRequest
import com.kochvaia.app.data.remote.DaysResponse
import com.kochvaia.app.data.remote.DeductRequest
import com.kochvaia.app.data.remote.SeenResponse
import com.kochvaia.app.data.remote.SummaryResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StarRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun award(kidId: String, date: String) =
        api.awardStar(kidId, AwardStarRequest(date))

    suspend fun undo(kidId: String, date: String) =
        api.undoStar(kidId, date)

    suspend fun deduct(kidId: String, count: Int, reason: String?) =
        api.deduct(kidId, DeductRequest(count, reason))

    suspend fun days(kidId: String, from: String, to: String): DaysResponse =
        api.days(kidId, from, to)

    suspend fun summary(kidId: String): SummaryResponse =
        api.summary(kidId)

    suspend fun seen(kidId: String): SeenResponse =
        api.seen(kidId)
}
