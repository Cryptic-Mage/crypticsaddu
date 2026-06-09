package com.helucryptic.android.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.helucryptic.android.data.db.ContactEntity
import com.helucryptic.android.data.repository.ContactRepository
import com.helucryptic.android.signaling.SignalingState
import com.helucryptic.android.ui.components.AvatarCircle
import com.helucryptic.android.ui.components.ListOrEmpty
import com.helucryptic.android.ui.navigation.Screen
import com.helucryptic.android.ui.room.PulsingDot
import javax.inject.Inject

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(nav: NavController) {
    val contactRepo = hiltViewModel<ChatListContactsHelper>()
    val contacts by contactRepo.contacts.collectAsState(initial = emptyList())
    val username = contactRepo.username
    val connectionState by contactRepo.connectionState.collectAsState()

    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatInput by remember { mutableStateOf("") }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false; newChatInput = "" },
            icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
            title = { Text("New Chat") },
            text = {
                OutlinedTextField(
                    value = newChatInput,
                    onValueChange = { newChatInput = it },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = newChatInput.trim()
                        if (target.isNotEmpty()) {
                            showNewChatDialog = false
                            newChatInput = ""
                            nav.navigate(Screen.Chat.go(target))
                        }
                    },
                    enabled = newChatInput.trim().isNotEmpty()
                ) { Text("Start Chat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false; newChatInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ShieldMoon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("helucryptic", style = MaterialTheme.typography.titleMedium)
                                if (username.isNotEmpty()) {
                                    Text(
                                        text = "@$username",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            PulsingDot(connectionState)
                            Text(
                                text = when (connectionState) {
                                    SignalingState.DISCONNECTED -> "Offline"
                                    SignalingState.CONNECTING   -> "Connecting"
                                    SignalingState.SIGNALING    -> "Signaling"
                                    SignalingState.CONNECTED    -> "Connected"
                                    SignalingState.RECONNECTING -> "Reconnecting"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val isActive = connectionState != SignalingState.DISCONNECTED
                            IconButton(
                                onClick = { if (isActive) contactRepo.disconnect() else contactRepo.connect() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Rounded.CloudOff else Icons.Rounded.CloudQueue,
                                    contentDescription = "Connection Toggle",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Rounded.Edit, contentDescription = "New Chat") }
        }
    ) { padding ->
        ListOrEmpty(
            isEmpty    = contacts.isEmpty(),
            emptyIcon  = Icons.AutoMirrored.Rounded.Chat,
            emptyTitle = "No chats yet",
            emptyBody  = "Tap the edit button to start a conversation.",
            modifier   = Modifier.padding(padding)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts, key = { it.username }) { contact ->
                    ContactRow(contact) { nav.navigate(Screen.Chat.go(contact.username)) }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onClick: () -> Unit) {
    ListItem(
        modifier        = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(contact.username, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "Tap to chat",
                style   = MaterialTheme.typography.bodySmall,
                color   = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        },
        leadingContent = { AvatarCircle(username = contact.username) }
    )
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ChatListContactsHelper @Inject constructor(
    repo: ContactRepository,
    private val identityStore: com.helucryptic.android.crypto.IdentityStore,
    private val connectionManager: com.helucryptic.android.webrtc.ConnectionManager,
    private val signalingClient: com.helucryptic.android.signaling.SignalingClient
) : androidx.lifecycle.ViewModel() {
    val contacts = repo.observeAll()
    val username = identityStore.username ?: ""
    val connectionState = signalingClient.state

    fun connect() = connectionManager.connectGlobal()
    fun disconnect() = connectionManager.disconnect()
}
