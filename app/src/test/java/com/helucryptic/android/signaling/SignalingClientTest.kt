package com.helucryptic.android.signaling

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class SignalingClientTest {
    private val server = MockWebServer()
    private val client = SignalingClient(ReconnectPolicy(), OkHttpClient())

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
        client.disconnect()
    }

    @Test
    fun `state transitions to SIGNALING after session_token`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) {
                ws.send("""{"type":"session_token","token":"abc"}""")
            }
        }))

        val wsUrl = server.url("/").toString().replace("http://", "ws://").replace("https://", "wss://")
        val job = launch { client.state.first { it == SignalingState.SIGNALING } }
        client.connect(wsUrl, "", "alice", "tok", null)

        job.join()
        assertEquals(SignalingState.SIGNALING, client.state.value)
    }

    @Test
    fun `messages emitted on receive`() = runTest {
        val messages = mutableListOf<SignalingMessage>()
        val job = launch { 
            client.messages.take(1).toList(messages) 
        }
        
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) {
                ws.send("""{"type":"session_token","token":"abc"}""")
            }
        }))
        
        val wsUrl = server.url("/").toString().replace("http://", "ws://").replace("https://", "wss://")
        client.connect(wsUrl, "", "alice", "tok", null)
        
        job.join()
        assertIs<SignalingMessage.SessionToken>(messages.first())
    }
}
