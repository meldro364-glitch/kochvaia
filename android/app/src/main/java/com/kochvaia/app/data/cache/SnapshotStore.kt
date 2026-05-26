package com.kochvaia.app.data.cache

import android.content.Context
import androidx.core.content.edit
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain key/value JSON store for resource snapshots, backed by
 * SharedPreferences. Deliberately *not* the EncryptedSharedPreferences used
 * by [com.kochvaia.app.data.SessionStore]: snapshots aren't sensitive (no
 * tokens, no PII beyond display names) and we want synchronous reads so we
 * can seed StateFlows from disk on cache construction.
 *
 * Scoping: every entry is wiped on sign-out via [clear] so a different
 * family/account starts clean.
 */
@Singleton
class SnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
    private val moshi: Moshi,
) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val adapterCache = HashMap<Type, JsonAdapter<Any>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> adapterFor(type: Type): JsonAdapter<T> =
        adapterCache.getOrPut(type) { moshi.adapter<Any>(type) } as JsonAdapter<T>

    fun <T> read(key: String, type: Type): T? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            adapterFor<T>(type).fromJson(raw)
        } catch (_: Exception) {
            // Corrupt snapshot (schema drift, partial write): treat as miss
            // rather than crashing. Next successful refresh will overwrite.
            prefs.edit { remove(key) }
            null
        }
    }

    fun <T> write(key: String, value: T?, type: Type) {
        prefs.edit {
            if (value == null) remove(key)
            else putString(key, adapterFor<T>(type).toJson(value))
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val FILE_NAME = "kochvaia_snapshots"
    }
}
