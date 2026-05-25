package com.kochvaia.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the current session token + role across app launches.
 * Backed by EncryptedSharedPreferences so the token isn't in plaintext at rest.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        val familyId = prefs.getString(KEY_FAMILY_ID, null) ?: return null
        return Session(
            token = token,
            role = Role.valueOf(role),
            familyId = familyId,
            parentId = prefs.getString(KEY_PARENT_ID, null),
            kidId = prefs.getString(KEY_KID_ID, null),
        )
    }

    fun save(session: Session) {
        prefs.edit().apply {
            putString(KEY_TOKEN, session.token)
            putString(KEY_ROLE, session.role.name)
            putString(KEY_FAMILY_ID, session.familyId)
            putString(KEY_PARENT_ID, session.parentId)
            putString(KEY_KID_ID, session.kidId)
            apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    private companion object {
        const val FILE_NAME = "kochvaia_secure_session"
        const val KEY_TOKEN = "token"
        const val KEY_ROLE = "role"
        const val KEY_FAMILY_ID = "family_id"
        const val KEY_PARENT_ID = "parent_id"
        const val KEY_KID_ID = "kid_id"
    }
}

enum class Role { parent, kid }

data class Session(
    val token: String,
    val role: Role,
    val familyId: String,
    val parentId: String?,
    val kidId: String?,
)
