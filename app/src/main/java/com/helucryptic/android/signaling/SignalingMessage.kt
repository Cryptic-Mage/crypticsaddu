package com.helucryptic.android.signaling

import org.json.JSONObject

sealed class SignalingMessage {
    data class SessionToken(val token: String, val reflectedHost: String?) : SignalingMessage()
    data class Forward(val sender: String, val type: String, val data: Any?) : SignalingMessage()
    data class Error(val message: String) : SignalingMessage()
    data class PeerLeft(val username: String) : SignalingMessage()

    companion object {
        fun parse(json: String): SignalingMessage? = runCatching {
            val obj = JSONObject(json)
            when (obj.optString("type")) {
                "session_token" -> {
                    val dataObj = obj.getJSONObject("data")
                    SessionToken(
                        token         = dataObj.getString("token"),
                        reflectedHost = dataObj.optString("reflected_host").takeIf { it.isNotEmpty() }
                    )
                }
                "error"     -> Error(obj.optString("data"))
                "peer_left" -> PeerLeft(obj.optString("sender"))
                else        -> Forward(
                    sender = obj.optString("sender"),
                    type   = obj.optString("type"),
                    data   = obj.opt("data")
                )
            }
        }.getOrNull()

        fun hello(username: String, pasetoToken: String, password: String, room: String?): String =
            JSONObject().apply {
                put("type", "hello")
                put("username", username)
                put("token", pasetoToken)
                if (password.isNotEmpty()) put("password", password)
                if (!room.isNullOrEmpty()) put("room", room)
            }.toString()

        fun forward(target: String, type: String, data: Any?): String =
            JSONObject().apply {
                put("target", target)
                put("type", type)
                if (data != null) put("data", data)
            }.toString()
    }
}
