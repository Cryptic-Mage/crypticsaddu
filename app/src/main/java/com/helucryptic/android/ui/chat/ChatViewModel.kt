package com.helucryptic.android.ui.chat

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.data.db.MessageEntity
import com.helucryptic.android.data.repository.MessageRepository
import com.helucryptic.android.webrtc.WebRtcEngine
import com.helucryptic.android.webrtc.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val msgRepo: MessageRepository,
    private val engine: WebRtcEngine,
    private val identityStore: IdentityStore,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    val messages = mutableStateListOf<MessageEntity>()
    var inputText by mutableStateOf("")

    fun loadMessages(peerId: String) {
        // connectGlobal() starts an async WebSocket handshake; onChannelOpen() must NOT be
        // called here — it fires only once the signaling server confirms the peer is present
        // (via room_state / peer_joined in ConnectionManager).
        connectionManager.connectGlobal()
        viewModelScope.launch {
            msgRepo.observe(peerId).collect {
                messages.clear()
                messages.addAll(it)
            }
        }
    }

    fun send(peerId: String) {
        val text = inputText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            // Persist FIRST with a known id (status "sent"); the engine sends the
            // same id on the wire, and the peer's ack flips the row to
            // "delivered" via ConnectionManager.onDelivery → markDelivered(id).
            val id = java.util.UUID.randomUUID().toString()
            msgRepo.save(
                roomOrPeerId = peerId,
                sender       = identityStore.username ?: "",
                ciphertext   = "encrypted",
                plaintext    = text,
                status       = "sent",
                id           = id
            )
            engine.sendMessage(peerId, text, id)
            inputText = ""
        }
    }
}
