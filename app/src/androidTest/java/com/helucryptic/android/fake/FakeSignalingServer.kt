package com.helucryptic.android.fake

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject

class FakeSignalingServer {
    val server = MockWebServer()
    private val sessions = mutableMapOf<String, WebSocket>()

    fun start() {
        server.start()
        server.setDispatcher(object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(ws: WebSocket, text: String) {
                        try {
                            val obj = JSONObject(text)
                            when (obj.optString("type")) {
                                "hello" -> {
                                    val u = obj.getString("username")
                                    sessions[u] = ws
                                    ws.send("""{"type":"session_token","token":"fake-token-$u"}""")
                                }
                                else -> {
                                    val target = obj.optString("target")
                                    sessions[target]?.send(text)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore malformed messages in test server
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        super.onFailure(webSocket, t, response)
                    }
                })
            }
        })
    }

    fun url() = server.url("/").toString().replace("http://", "ws://").replace("https://", "wss://")
    fun stop() = server.shutdown()
}
