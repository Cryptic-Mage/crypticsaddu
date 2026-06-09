package com.helucryptic.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helucryptic.android.crypto.Fingerprint
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.data.datastore.AppSettings
import com.helucryptic.android.data.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val identityStore: IdentityStore,
    private val db: AppDatabase
) : ViewModel() {
    val signalingUrl       = settings.signalingUrl.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val serverPassword     = settings.serverPassword.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val turnUrl            = settings.turnUrl.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val turnUsername       = settings.turnUsername.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val turnPassword       = settings.turnPassword.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val portForwardEnabled = settings.portForwardEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val forwardedPort      = settings.forwardedPort.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val theme              = settings.theme.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val notificationSound  = settings.notificationSound.stateIn(viewModelScope, SharingStarted.Lazily, "notif_gta_bell")
    val ringtoneSound      = settings.ringtoneSound.stateIn(viewModelScope, SharingStarted.Lazily, "ringtone_solo_leveling")

    val notificationOptions = listOf(
        "notif_gta_bell"  to "GTA Bell",
        "notif_tone_1"    to "Tone 1",
        "notif_tone_2"    to "Tone 2",
        "notif_tone_3"    to "Tone 3"
    )
    val ringtoneOptions = listOf(
        "ringtone_solo_leveling"  to "Solo Leveling",
        "ringtone_sakura_wakeup"  to "Sakura Wake Up",
        "ringtone_sakura_daisy"   to "Sakura Daisy",
        "sfx_incoming_call"       to "ISAC Incoming"
    )
    val username        = identityStore.username ?: ""
    val fingerprint     = identityStore.x25519Pub?.let { Fingerprint.compute(it) } ?: ""

    private val _wiped     = MutableStateFlow(false)
    val wiped: StateFlow<Boolean> = _wiped

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut

    fun setUrl(url: String)               = viewModelScope.launch { settings.setSignalingUrl(url) }
    // setServerPassword/setTurnUsername/setTurnPassword are synchronous (EncryptedSharedPreferences)
    fun setServerPassword(pw: String)     { settings.setServerPassword(pw) }
    fun setTurnUrl(url: String)           = viewModelScope.launch { settings.setTurnUrl(url) }
    fun setTurnUsername(user: String)     { settings.setTurnUsername(user) }
    fun setTurnPassword(pw: String)       { settings.setTurnPassword(pw) }
    fun setPortForwardEnabled(e: Boolean) = viewModelScope.launch { settings.setPortForwardEnabled(e) }
    fun setForwardedPort(port: Int)       = viewModelScope.launch { settings.setForwardedPort(port) }
    fun resetUrl()                        = viewModelScope.launch { settings.resetToDefaults() }
    fun setTheme(t: String)               = viewModelScope.launch { settings.setTheme(t) }
    fun setNotificationSound(s: String)   = viewModelScope.launch { settings.setNotificationSound(s) }
    fun setRingtoneSound(s: String)       = viewModelScope.launch { settings.setRingtoneSound(s) }

    fun logOut() {
        identityStore.clear()
        _loggedOut.value = true
    }

    fun wipeAccount() {
        identityStore.clear()
        viewModelScope.launch(Dispatchers.IO) {
            settings.resetToDefaults()
            db.clearAllTables()
            _wiped.value = true
        }
    }
}
