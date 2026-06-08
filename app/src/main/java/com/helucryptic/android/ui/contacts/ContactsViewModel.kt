package com.helucryptic.android.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.data.repository.ContactRepository
import com.helucryptic.android.signaling.SignalingClient
import com.helucryptic.android.signaling.SignalingState
import com.helucryptic.android.webrtc.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repo: ContactRepository,
    private val identityStore: IdentityStore,
    private val connectionManager: ConnectionManager,
    private val signalingClient: SignalingClient
) : ViewModel() {

    val contacts = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val username: String = identityStore.username ?: ""
    val connectionState: StateFlow<SignalingState> = signalingClient.state

    fun connect() = connectionManager.connectGlobal()
    fun disconnect() = connectionManager.disconnect()

    fun markVerified(username: String) {
        viewModelScope.launch { repo.markVerified(username) }
    }

    fun addContact(username: String) {
        viewModelScope.launch { repo.addContact(username) }
    }
}
