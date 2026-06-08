package com.helucryptic.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.data.datastore.AppSettings
import com.helucryptic.android.ui.navigation.AppNavigation
import com.helucryptic.android.ui.navigation.Screen
import com.helucryptic.android.ui.theme.HelucrypticTheme
import com.helucryptic.android.webrtc.ConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var identityStore: IdentityStore
    @Inject lateinit var connectionManager: ConnectionManager
    @Inject lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = if (identityStore.isInitialized()) {
            connectionManager.connectGlobal()
            Screen.ChatList.route
        } else {
            Screen.Onboarding.route
        }
        setContent {
            val themePreference by appSettings.theme.collectAsState(initial = "system")
            val useDarkTheme = when (themePreference) {
                "light" -> false
                "dark"  -> true
                else    -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            HelucrypticTheme(darkTheme = useDarkTheme) {
                AppNavigation(start)
            }
        }
    }
}
