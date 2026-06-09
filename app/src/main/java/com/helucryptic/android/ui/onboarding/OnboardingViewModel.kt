package com.helucryptic.android.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.helucryptic.android.BuildConfig
import com.helucryptic.android.crypto.CryptoManager
import com.helucryptic.android.crypto.Fingerprint
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.data.datastore.AppSettings
import com.helucryptic.android.data.datastore.AppSettings.Companion.DEFAULT_SIGNALING_URL
import com.helucryptic.android.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val crypto: CryptoManager,
    private val identityStore: IdentityStore,
    private val settings: AppSettings
) : ViewModel() {

    var step by mutableStateOf(1)
    var username by mutableStateOf("")
    var fingerprint by mutableStateOf("")
    var serverUrl by mutableStateOf(BuildConfig.SIGNALING_URL.ifEmpty { "wss://helucryptic-signaling.crypticmage00.workers.dev/" })
    var serverPassword by mutableStateOf(BuildConfig.SERVER_PASSWORD)
    var turnUrl by mutableStateOf(BuildConfig.TURN_URL)
    var turnUsername by mutableStateOf(BuildConfig.TURN_USERNAME)
    var turnPassword by mutableStateOf(BuildConfig.TURN_PASSWORD)
    var portForwardEnabled by mutableStateOf(false)
    var forwardedPort by mutableStateOf("")
    var usernameError by mutableStateOf<String?>(null)

    fun validateAndAdvance(): Boolean {
        return if (username.matches(Regex("[a-zA-Z0-9_]{2,32}"))) {
            usernameError = null
            true
        } else {
            usernameError = "2–32 chars: letters, digits, underscore"
            false
        }
    }

    fun generateKeys() {
        val keys = crypto.generateIdentityKeys()
        identityStore.saveIdentity(username, keys)
        fingerprint = Fingerprint.compute(keys.x25519Pub)
        step = 2
    }

    fun finish(nav: NavController) {
        viewModelScope.launch {
            settings.setSignalingUrl(serverUrl.ifEmpty { AppSettings.DEFAULT_SIGNALING_URL })
            settings.setTurnUrl(turnUrl)
            settings.setPortForwardEnabled(portForwardEnabled)
            settings.setForwardedPort(forwardedPort.toIntOrNull() ?: 0)
            // Credentials are synchronous (EncryptedSharedPreferences)
            settings.setServerPassword(serverPassword)
            settings.setTurnUsername(turnUsername)
            settings.setTurnPassword(turnPassword)
            nav.navigate(Screen.ChatList.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
            }
        }
    }
}
