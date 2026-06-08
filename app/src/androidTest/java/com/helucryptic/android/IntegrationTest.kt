package com.helucryptic.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.helucryptic.android.fake.FakeSignalingServer
import com.helucryptic.android.signaling.ReconnectPolicy
import com.helucryptic.android.signaling.SignalingClient
import com.helucryptic.android.signaling.SignalingMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HandshakeIntegrationTest {
    @get:Rule 
    val hiltRule = HiltAndroidRule(this)
    
    private val fakeServer = FakeSignalingServer()

    @Before 
    fun setUp() { 
        hiltRule.inject()
        fakeServer.start() 
    }
    
    @After  
    fun tearDown() { 
        fakeServer.stop() 
    }

    @Test 
    fun hello_handshake_completes_and_session_token_received() = runTest {
        val client = SignalingClient(ReconnectPolicy(), OkHttpClient())
        val tokens = mutableListOf<SignalingMessage.SessionToken>()
        
        val job = launch { 
            client.messages
                .filterIsInstance<SignalingMessage.SessionToken>()
                .take(1)
                .toList(tokens) 
        }
        
        client.connect(fakeServer.url(), "", "alice", "dummy-token", null)
        job.join()
        
        assertEquals(1, tokens.size)
        assertTrue(tokens.first().token.startsWith("fake-token-"))
        client.disconnect()
    }
}
