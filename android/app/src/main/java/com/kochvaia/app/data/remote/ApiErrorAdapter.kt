package com.kochvaia.app.data.remote

import com.squareup.moshi.Moshi
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Pulls the structured `{ "error": "..." }` body out of a 4xx/5xx response.
 * Falls back to the HTTP status string when the body is missing or malformed.
 */
class ApiErrorAdapter @Inject constructor(moshi: Moshi) {
    private val adapter = moshi.adapter(ErrorBody::class.java)

    fun message(t: Throwable): String = when (t) {
        is HttpException -> {
            val raw = t.response()?.errorBody()?.string()
            val parsed = raw?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            parsed?.error ?: "http_${t.code()}"
        }
        else -> t.message ?: t.javaClass.simpleName
    }
}
