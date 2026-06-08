package com.helucryptic.android.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.helucryptic.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("app_settings")

@Singleton
class AppSettings @Inject constructor(@ApplicationContext private val ctx: Context) {

    private object Keys {
        val SIGNALING_URL    = stringPreferencesKey("signaling_url")
        val SERVER_PASSWORD  = stringPreferencesKey("server_password")
        val TURN_URL         = stringPreferencesKey("turn_url")
        val TURN_USERNAME    = stringPreferencesKey("turn_username")
        val TURN_PASSWORD    = stringPreferencesKey("turn_password")
        val THEME            = stringPreferencesKey("theme")
        val PORT_FORWARD_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("port_forward_enabled")
        val FORWARDED_PORT   = androidx.datastore.preferences.core.intPreferencesKey("forwarded_port")
        val NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")
        val RINGTONE_SOUND     = stringPreferencesKey("ringtone_sound")
    }

    val signalingUrl = ctx.dataStore.data.map { 
        val saved = it[Keys.SIGNALING_URL] ?: BuildConfig.SIGNALING_URL
        if (saved.isEmpty()) "wss://helucryptic-signaling.crypticmage00.workers.dev/" else saved
    }
    val serverPassword = ctx.dataStore.data.map { it[Keys.SERVER_PASSWORD] ?: BuildConfig.SERVER_PASSWORD }
    val turnUrl = ctx.dataStore.data.map { it[Keys.TURN_URL] ?: BuildConfig.TURN_URL }
    val turnUsername = ctx.dataStore.data.map { it[Keys.TURN_USERNAME] ?: BuildConfig.TURN_USERNAME }
    val turnPassword = ctx.dataStore.data.map { it[Keys.TURN_PASSWORD] ?: BuildConfig.TURN_PASSWORD }
    val theme = ctx.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val portForwardEnabled   = ctx.dataStore.data.map { it[Keys.PORT_FORWARD_ENABLED] ?: false }
    val forwardedPort        = ctx.dataStore.data.map { it[Keys.FORWARDED_PORT] ?: 0 }
    val notificationSound    = ctx.dataStore.data.map { it[Keys.NOTIFICATION_SOUND] ?: "notif_gta_bell" }
    val ringtoneSound        = ctx.dataStore.data.map { it[Keys.RINGTONE_SOUND] ?: "ringtone_solo_leveling" }

    suspend fun setNotificationSound(s: String) { ctx.dataStore.edit { it[Keys.NOTIFICATION_SOUND] = s } }
    suspend fun setRingtoneSound(s: String)      { ctx.dataStore.edit { it[Keys.RINGTONE_SOUND] = s } }

    suspend fun setSignalingUrl(url: String) { ctx.dataStore.edit { it[Keys.SIGNALING_URL] = url } }
    suspend fun setServerPassword(pw: String) { ctx.dataStore.edit { it[Keys.SERVER_PASSWORD] = pw } }
    suspend fun setTurnUrl(url: String) { ctx.dataStore.edit { it[Keys.TURN_URL] = url } }
    suspend fun setTurnUsername(user: String) { ctx.dataStore.edit { it[Keys.TURN_USERNAME] = user } }
    suspend fun setTurnPassword(pw: String) { ctx.dataStore.edit { it[Keys.TURN_PASSWORD] = pw } }
    suspend fun setPortForwardEnabled(enabled: Boolean) { ctx.dataStore.edit { it[Keys.PORT_FORWARD_ENABLED] = enabled } }
    suspend fun setForwardedPort(port: Int) { ctx.dataStore.edit { it[Keys.FORWARDED_PORT] = port } }
    suspend fun setTheme(t: String) { ctx.dataStore.edit { it[Keys.THEME] = t } }
    suspend fun resetToDefaults() {
        ctx.dataStore.edit {
            it[Keys.SIGNALING_URL]   = "wss://helucryptic-signaling.crypticmage00.workers.dev/"
            it[Keys.SERVER_PASSWORD] = ""
            it[Keys.TURN_URL]        = ""
            it[Keys.TURN_USERNAME]   = ""
            it[Keys.TURN_PASSWORD]   = ""
            it[Keys.PORT_FORWARD_ENABLED] = false
            it[Keys.FORWARDED_PORT]   = 0
        }
    }
}
