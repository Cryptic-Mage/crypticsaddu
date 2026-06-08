package com.helucryptic.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.ui.navigation.Screen
import javax.inject.Inject

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nav: NavController,
    peerId: String,
    vm: ChatViewModel = hiltViewModel()
) {
    val identityStore = androidx.hilt.navigation.compose.hiltViewModel<ChatIdentityHelper>()
    val myUsername    = identityStore.username

    LaunchedEffect(peerId) { vm.loadMessages(peerId) }

    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerId) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Screen.Call.go(peerId)) }) {
                        Icon(Icons.Rounded.Call, contentDescription = "Call")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                value    = vm.inputText,
                onChange = { vm.inputText = it },
                onSend   = { vm.send(peerId) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // E2EE badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "End-to-end encrypted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                state          = listState,
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.messages) { msg ->
                    val isMe = msg.sender == myUsername
                    MessageBubble(
                        text  = msg.plaintextCache ?: "…",
                        isMe  = isMe
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isMe: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart    = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd   = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text     = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color    = if (isMe) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurface,
                style    = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MessageInputBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = value,
                onValueChange = onChange,
                placeholder   = { Text("Message…") },
                modifier      = Modifier.weight(1f).testTag("message_input"),
                maxLines      = 4,
                shape         = androidx.compose.foundation.shape.CircleShape
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick          = onSend,
                containerColor   = MaterialTheme.colorScheme.primary,
                modifier         = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

// Helper to access IdentityStore.username in the composable scope
@dagger.hilt.android.lifecycle.HiltViewModel
class ChatIdentityHelper @Inject constructor(store: IdentityStore) : androidx.lifecycle.ViewModel() {
    val username = store.username ?: ""
}
