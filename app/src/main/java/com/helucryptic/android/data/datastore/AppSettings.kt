package com.helucryptic.android.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.helucryptic.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("app_settings")

/**
 * AppSettings stores non-sensitive preferences in DataStore and
 * sensitive credentials (passwords) in EncryptedSharedPreferences.
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext private val ctx: Context) {

    // ── Encrypted prefs (serverPassword, turnUsername, turnPassword) ──────────

    private val encPrefs by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "helucryptic_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _serverPassword = MutableStateFlow(
        encPrefs.getString(ENC_KEY_SERVER_PASSWORD, BuildConfig.SERVER_PASSWORD) ?: ""
    )
    private val _turnUsername = MutableStateFlow(
        encPrefs.getString(ENC_KEY_TURN_USERNAME, BuildConfig.TURN_USERNAME) ?: ""
    )
    private val _turnPassword = MutableStateFlow(
        encPrefs.getString(ENC_KEY_TURN_PASSWORD, BuildConfig.TURN_PASSWORD) ?: ""
    )

    val serverPassword: Flow<String> = _serverPassword
    val turnUsername:   Flow<String> = _turnUsername
    val turnPassword:   Flow<String> = _turnPassword

    fun setServerPassword(pw: String) {
        encPrefs.edit().putString(ENC_KEY_SERVER_PASSWORD, pw).apply()
        _serverPassword.value = pw
    }

    fun setTurnUsername(user: String) {
        encPrefs.edit().putString(ENC_KEY_TURN_USERNAME, user).apply()
        _turnUsername.value = user
    }

    fun setTurnPassword(pw: String) {
        encPrefs.edit().putString(ENC_KEY_TURN_PASSWORD, pw).apply()
        _turnPassword.value = pw
    }

    // ── Plain DataStore (non-sensitive) ───────────────────────────────────────

    private object Keys {
        val SIGNALING_URL        = stringPreferencesKey("signaling_url")
        val TURN_URL             = stringPreferencesKey("turn_url")
        val THEME                = stringPreferencesKey("theme")
        val PORT_FORWARD_ENABLED = booleanPreferencesKey("port_forward_enabled")
        val FORWARDED_PORT       = intPreferencesKey("forwarded_port")
        val NOTIFICATION_SOUND   = stringPreferencesKey("notification_sound")
        val RINGTONE_SOUND       = stringPreferencesKey("ringtone_sound")
    }

    val signalingUrl: Flow<String> = ctx.dataStore.data.map {
        val saved = it[Keys.SIGNALING_URL] ?: BuildConfig.SIGNALING_URL
        if (saved.isEmpty()) DEFAULT_SIGNALING_URL else saved
    }
    val turnUrl:           Flow<String>  = ctx.dataStore.data.map { it[Keys.TURN_URL]             ?: BuildConfig.TURN_URL }
    val theme:             Flow<String>  = ctx.dataStore.data.map { it[Keys.THEME]                ?: "system" }
    val portForwardEnabled: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.PORT_FORWARD_ENABLED] ?: false }
    val forwardedPort:     Flow<Int>     = ctx.dataStore.data.map { it[Keys.FORWARDED_PORT]        ?: 0 }
    val notificationSound: Flow<String>  = ctx.dataStore.data.map { it[Keys.NOTIFICATION_SOUND]   ?: "notif_gta_bell" }
    val ringtoneSound:     Flow<String>  = ctx.dataStore.data.map { it[Keys.RINGTONE_SOUND]        ?: "ringtone_solo_leveling" }

    suspend fun setSignalingUrl(url: String)  { ctx.dataStore.edit { it[Keys.SIGNALING_URL] = url } }
    suspend fun setTurnUrl(url: String)        { ctx.dataStore.edit { it[Keys.TURN_URL] = url } }
    suspend fun setTheme(t: String)            { ctx.dataStore.edit { it[Keys.THEME] = t } }
    suspend fun setPortForwardEnabled(e: Boolean) { ctx.dataStore.edit { it[Keys.PORT_FORWARD_ENABLED] = e } }
    suspend fun setForwardedPort(port: Int)    { ctx.dataStore.edit { it[Keys.FORWARDED_PORT] = port } }
    suspend fun setNotificationSound(s: String) { ctx.dataStore.edit { it[Keys.NOTIFICATION_SOUND] = s } }
    suspend fun setRingtoneSound(s: String)    { ctx.dataStore.edit { it[Keys.RINGTONE_SOUND] = s } }

    suspend fun resetToDefaults() {
        ctx.dataStore.edit {
            it[Keys.SIGNALING_URL]        = DEFAULT_SIGNALING_URL
            it[Keys.TURN_URL]             = ""
            it[Keys.PORT_FORWARD_ENABLED] = false
            it[Keys.FORWARDED_PORT]       = 0
        }
        setServerPassword("")
        setTurnUsername("")
        setTurnPassword("")
    }

    companion object {
        const val DEFAULT_SIGNALING_URL = "wss://helucryptic-signaling.crypticmage00.workers.dev/"
        private const val ENC_KEY_SERVER_PASSWORD = "server_password"
        private const val ENC_KEY_TURN_USERNAME   = "turn_username"
        private const val ENC_KEY_TURN_PASSWORD   = "turn_password"
    }
}
