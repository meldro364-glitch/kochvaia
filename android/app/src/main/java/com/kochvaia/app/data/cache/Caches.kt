package com.kochvaia.app.data.cache

import com.kochvaia.app.data.remote.ApiService
import com.kochvaia.app.data.remote.DaysResponse
import com.kochvaia.app.data.remote.ItemDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.MeResponse
import com.kochvaia.app.data.remote.SummaryResponse
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache layer follows the stale-while-revalidate pattern:
 *
 *  - Each cache exposes a [StateFlow] that emits the last known value (or
 *    null if we've never loaded it).
 *  - On app start the flow is seeded from a SharedPreferences snapshot so
 *    the first frame of every screen can render real data without waiting
 *    for the network.
 *  - ViewModels collect the flow + call refresh() in the background. On
 *    success the new value is published to subscribers AND persisted; on
 *    failure the cached value is kept (no flicker).
 *
 * Mutations from the client (award/deduct/add kid/edit item/...) call the
 * cache's `put` helpers directly to update the flow optimistically. The
 * next refresh confirms or corrects.
 */

@Singleton
class KidsCache @Inject constructor(
    private val api: ApiService,
    private val snapshots: SnapshotStore,
) {
    private val type = Types.newParameterizedType(List::class.java, KidDto::class.java)
    private val _flow = MutableStateFlow<List<KidDto>?>(snapshots.read(KEY, type))
    val flow: StateFlow<List<KidDto>?> = _flow.asStateFlow()

    fun put(value: List<KidDto>) {
        _flow.value = value
        snapshots.write(KEY, value, type)
    }

    suspend fun refresh(): Result<List<KidDto>> = runCatching {
        val list = api.listKids().kids
        put(list)
        list
    }

    fun clear() {
        _flow.value = null
        snapshots.write<List<KidDto>>(KEY, null, type)
    }

    private companion object { const val KEY = "kids" }
}

@Singleton
class ItemsCache @Inject constructor(
    private val api: ApiService,
    private val snapshots: SnapshotStore,
) {
    private val type = Types.newParameterizedType(List::class.java, ItemDto::class.java)
    private val _flow = MutableStateFlow<List<ItemDto>?>(snapshots.read(KEY, type))
    val flow: StateFlow<List<ItemDto>?> = _flow.asStateFlow()

    fun put(value: List<ItemDto>) {
        _flow.value = value
        snapshots.write(KEY, value, type)
    }

    suspend fun refresh(): Result<List<ItemDto>> = runCatching {
        val list = api.listItems().items
        put(list)
        list
    }

    fun clear() {
        _flow.value = null
        snapshots.write<List<ItemDto>>(KEY, null, type)
    }

    private companion object { const val KEY = "items" }
}

@Singleton
class MeCache @Inject constructor(
    private val api: ApiService,
    private val snapshots: SnapshotStore,
) {
    private val type = MeResponse::class.java
    private val _flow = MutableStateFlow<MeResponse?>(snapshots.read(KEY, type))
    val flow: StateFlow<MeResponse?> = _flow.asStateFlow()

    fun put(value: MeResponse) {
        _flow.value = value
        snapshots.write(KEY, value, type)
    }

    suspend fun refresh(): Result<MeResponse> = runCatching {
        val me = api.me()
        put(me)
        me
    }

    fun clear() {
        _flow.value = null
        snapshots.write<MeResponse>(KEY, null, type)
    }

    private companion object { const val KEY = "me" }
}

/**
 * Per-kid summary cache. The full map is kept in one snapshot blob so a
 * single write covers any number of kids — the parent dashboard rebuilds
 * every per-kid summary from one /dashboard fetch.
 */
@Singleton
class SummariesCache @Inject constructor(
    private val api: ApiService,
    private val snapshots: SnapshotStore,
) {
    private val type = Types.newParameterizedType(
        Map::class.java, String::class.java, SummaryResponse::class.java,
    )
    private val _flow = MutableStateFlow<Map<String, SummaryResponse>>(
        snapshots.read(KEY, type) ?: emptyMap(),
    )
    val flow: StateFlow<Map<String, SummaryResponse>> = _flow.asStateFlow()

    fun put(kidId: String, summary: SummaryResponse) {
        val next = _flow.value.toMutableMap().apply { put(kidId, summary) }
        _flow.value = next
        snapshots.write(KEY, next, type)
    }

    fun replaceAll(map: Map<String, SummaryResponse>) {
        _flow.value = map
        snapshots.write(KEY, map, type)
    }

    suspend fun refresh(kidId: String): Result<SummaryResponse> = runCatching {
        val s = api.summary(kidId)
        put(kidId, s)
        s
    }

    fun clear() {
        _flow.value = emptyMap()
        snapshots.write<Map<String, SummaryResponse>>(KEY, null, type)
    }

    private companion object { const val KEY = "summaries" }
}

/**
 * Days cache, keyed by "$kidId|$from|$to". In-memory only — week ranges
 * change as the user pages around the calendar; persisting all of them
 * would bloat the snapshot for marginal benefit. Cold start refetches.
 */
@Singleton
class DaysCache @Inject constructor(
    private val api: ApiService,
) {
    private val _flow = MutableStateFlow<Map<String, DaysResponse>>(emptyMap())
    val flow: StateFlow<Map<String, DaysResponse>> = _flow.asStateFlow()

    private fun key(kidId: String, from: String, to: String) = "$kidId|$from|$to"

    fun get(kidId: String, from: String, to: String): DaysResponse? =
        _flow.value[key(kidId, from, to)]

    suspend fun refresh(kidId: String, from: String, to: String): Result<DaysResponse> =
        runCatching {
            val d = api.days(kidId, from, to)
            val k = key(kidId, from, to)
            _flow.value = _flow.value + (k to d)
            d
        }

    fun clear() {
        _flow.value = emptyMap()
    }
}
