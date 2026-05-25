package com.kochvaia.app.data.repo

import com.kochvaia.app.data.Role
import com.kochvaia.app.data.Session
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.remote.ApiService
import com.kochvaia.app.data.remote.GoogleAuthRequest
import com.kochvaia.app.data.remote.QrCreateRequest
import com.kochvaia.app.data.remote.QrCreateResponse
import com.kochvaia.app.data.remote.QrRedeemRequest
import com.kochvaia.app.data.remote.QrRedeemResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) {
    fun currentSession(): Session? = sessionStore.load()

    suspend fun signInWithGoogle(
        idToken: String,
        inviteCode: String?,
        familyTz: String,
        displayName: String?,
    ): Session {
        val res = api.signInWithGoogle(
            GoogleAuthRequest(
                idToken = idToken,
                inviteCode = inviteCode,
                familyTz = familyTz,
                displayName = displayName,
            ),
        )
        val session = Session(
            token = res.sessionToken,
            role = Role.valueOf(res.role),
            familyId = res.familyId,
            parentId = res.parentId,
            kidId = res.kidId,
        )
        sessionStore.save(session)
        return session
    }

    suspend fun redeemKidQr(code: String, deviceLabel: String?): Session {
        val res: QrRedeemResponse = api.redeemQr(QrRedeemRequest(code = code, deviceLabel = deviceLabel))
        if (res.role != "kid" || res.sessionToken == null || res.kidId == null || res.familyId == null) {
            error("expected_kid_redemption_got_${res.role}")
        }
        val session = Session(
            token = res.sessionToken,
            role = Role.kid,
            familyId = res.familyId,
            parentId = null,
            kidId = res.kidId,
        )
        sessionStore.save(session)
        return session
    }

    /**
     * Peek a parent-invite QR before going through Google sign-in. The code is
     * still single-use after Google sign-in consumes it via /auth/google.
     */
    suspend fun peekParentInvite(code: String): QrRedeemResponse =
        api.redeemQr(QrRedeemRequest(code = code))

    suspend fun createKidPairQr(kidId: String): QrCreateResponse =
        api.createQr(QrCreateRequest(kind = "kid", kidId = kidId))

    suspend fun createCoParentInviteQr(): QrCreateResponse =
        api.createQr(QrCreateRequest(kind = "parent"))

    suspend fun logout() {
        runCatching { api.logout() } // best-effort revoke
        sessionStore.clear()
    }
}
