package com.helucryptic.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.webrtc.PeerConnectionFactory

@HiltAndroidApp
class HelucrypticApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
    }
}
