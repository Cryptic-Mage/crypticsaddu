package com.helucryptic.android.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helucryptic.android.data.datastore.AppSettings
import com.helucryptic.android.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InviteState {
    object Loading : InviteState()
    data class Success(val inviteString: String) : InviteState()
    object Error : InviteState()
}

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _inviteState = MutableStateFlow<InviteState>(InviteState.Loading)
    val inviteState: StateFlow<InviteState> = _inviteState

    fun loadInvite(roomCode: String) {
        viewModelScope.launch {
            try {
                val room = roomRepository.getRoom(roomCode)
                val url = appSettings.signalingUrl.first()
                if (room != null) {
                    val inviteStr = "HELU-INV1:${room.roomCode}:${room.psk}:$url"
                    _inviteState.value = InviteState.Success(inviteStr)
                } else {
                    _inviteState.value = InviteState.Error
                }
            } catch (e: Exception) {
                _inviteState.value = InviteState.Error
            }
        }
    }

    fun handleScannedInvite(inviteStr: String, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            try {
                if (!inviteStr.startsWith("HELU-INV1:")) {
                    onFailure()
                    return@launch
                }
                
                val parts = inviteStr.removePrefix("HELU-INV1:").split(":")
                if (parts.size < 3) {
                    onFailure()
                    return@launch
                }
                
                val roomCode = parts[0]
                val psk = parts[1]
                val signalingUrl = parts.subList(2, parts.size).joinToString(":")
                
                if (roomCode.isBlank() || psk.isBlank() || signalingUrl.isBlank()) {
                    onFailure()
                    return@launch
                }
                
                // Save room to repository (mark creator as "unknown" since it's not in the invite format)
                roomRepository.upsert(
                    roomCode = roomCode,
                    psk = psk,
                    creatorUsername = "unknown"
                )
                
                // Optionally save/update signaling server URL if needed, or just keep it
                // For simplicity, we just save the room and connect to it
                onSuccess(roomCode)
            } catch (e: Exception) {
                onFailure()
            }
        }
    }
}
