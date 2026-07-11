package com.helucryptic.android.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helucryptic.android.crypto.InviteCodec
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
                val url  = appSettings.signalingUrl.first()
                if (room != null) {
                    // Desktop-compatible HELU-INV1 (base64url JSON + checksum) so
                    // an invite generated on Android can be redeemed on desktop.
                    // Rooms created before the ROOM-XXXX format fall back to the
                    // legacy colon format (Android-only, but still scannable).
                    val inviteStr = runCatching {
                        InviteCodec.encode(InviteCodec.Invite(
                            roomId       = room.roomCode,
                            signalingUrl = url,
                            psk          = room.psk.takeIf { it.isNotEmpty() },
                        ))
                    }.getOrElse { "HELU-INV1:${room.roomCode}:${room.psk}:$url" }
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
                val invite = InviteCodec.decode(inviteStr)

                roomRepository.upsert(
                    roomCode        = invite.roomId,
                    psk             = invite.psk ?: "",
                    creatorUsername = "unknown"
                )

                // Apply the invite's connection details — previously the scanned
                // URL was ignored, so an invite pointing at a different signaling
                // server could never actually connect.
                if (invite.signalingUrl.isNotBlank()) {
                    appSettings.setSignalingUrl(invite.signalingUrl)
                }
                invite.password?.takeIf { it.isNotBlank() }?.let {
                    appSettings.setServerPassword(it)
                }

                onSuccess(invite.roomId)
            } catch (e: Exception) {
                onFailure()
            }
        }
    }
}
