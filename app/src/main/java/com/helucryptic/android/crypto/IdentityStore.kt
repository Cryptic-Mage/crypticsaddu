package com.helucryptic.android.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "helucryptic_identity",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    var x25519Priv: String?
        get() = prefs.getString(KEY_X25519_PRIV, null)
        set(v) = prefs.edit().putString(KEY_X25519_PRIV, v).apply()

    var x25519Pub: String?
        get() = prefs.getString(KEY_X25519_PUB, null)
        set(v) = prefs.edit().putString(KEY_X25519_PUB, v).apply()

    var ed25519Priv: String?
        get() = prefs.getString(KEY_ED25519_PRIV, null)
        set(v) = prefs.edit().putString(KEY_ED25519_PRIV, v).apply()

    var ed25519Pub: String?
        get() = prefs.getString(KEY_ED25519_PUB, null)
        set(v) = prefs.edit().putString(KEY_ED25519_PUB, v).apply()

    /** True when all 5 fields are populated — identity is complete. */
    fun isInitialized(): Boolean =
        username != null && x25519Priv != null && x25519Pub != null &&
        ed25519Priv != null && ed25519Pub != null

    /** Wipe all identity data (e.g. for account reset). */
    fun clear() = prefs.edit().clear().apply()

    /** Save a freshly generated IdentityKeys + username in one transaction. */
    fun saveIdentity(username: String, keys: IdentityKeys) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_X25519_PRIV, keys.x25519Priv)
            .putString(KEY_X25519_PUB, keys.x25519Pub)
            .putString(KEY_ED25519_PRIV, keys.ed25519Priv)
            .putString(KEY_ED25519_PUB, keys.ed25519Pub)
            .apply()
    }

    companion object {
        private const val KEY_USERNAME    = "username"
        private const val KEY_X25519_PRIV = "x25519_priv"
        private const val KEY_X25519_PUB  = "x25519_pub"
        private const val KEY_ED25519_PRIV= "ed25519_priv"
        private const val KEY_ED25519_PUB = "ed25519_pub"
    }
}
