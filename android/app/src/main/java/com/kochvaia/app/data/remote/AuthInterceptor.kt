package com.kochvaia.app.data.remote

import com.kochvaia.app.data.SessionStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches Authorization: Bearer <token> to every request when we have a
 * session. Routes that don't need auth (e.g. /auth/google) still get the
 * header if one happens to exist — the backend ignores it for those.
 */
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val token = sessionStore.token()
        val out = if (token != null && req.header("Authorization") == null) {
            req.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            req
        }
        return chain.proceed(out)
    }
}
