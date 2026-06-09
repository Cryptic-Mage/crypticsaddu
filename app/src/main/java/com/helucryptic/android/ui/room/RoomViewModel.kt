package com.helucryptic.android.ui.room

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.data.db.MessageEntity
import com.helucryptic.android.data.repository.MessageRepository
import com.helucryptic.android.data.repository.RoomRepository
import com.helucryptic.android.webrtc.RoomManager
import com.helucryptic.android.webrtc.WebRtcEngine
import com.helucryptic.android.webrtc.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoomViewModel @Inject constructor(
    private val msgRepo: MessageRepository,
    private val roomRepo: RoomRepository,
    private val engine: WebRtcEngine,
    private val roomManager: RoomManager,
    private val identityStore: IdentityStore,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    val messages = mutableStateListOf<MessageEntity>()
    var inputText by mutableStateOf("")
    val members = roomManager.members.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun loadMessages(roomCode: String) {
        viewModelScope.launch {
            val room = roomRepo.getRoom(roomCode)
            val psk     = room?.psk             ?: ""
            val creator = room?.creatorUsername ?: ""
            connectionManager.connectRoom(roomCode, psk, creator)
            msgRepo.observe(roomCode).collect {
                messages.clear()
                messages.addAll(it)
            }
        }
    }

    fun send(roomCode: String) {
        val text = inputText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val sent = engine.broadcastMessage(text)
            if (sent) {
                msgRepo.save(
                    roomOrPeerId = roomCode,
                    sender       = identityStore.username ?: "",
                    ciphertext   = "encrypted",
                    plaintext    = text
                )
                inputText = ""
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionManager.disconnect()
    }
}
