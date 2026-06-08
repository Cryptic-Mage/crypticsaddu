package com.helucryptic.android.ui.room

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material.icons.rounded.Share
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.ui.navigation.Screen
import javax.inject.Inject

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    nav: NavController,
    roomCode: String,
    vm: RoomViewModel = hiltViewModel()
) {
    val identityHelper = hiltViewModel<RoomIdentityHelper>()
    val myUsername = identityHelper.username
    val members by vm.members.collectAsState()

    LaunchedEffect(roomCode) { vm.loadMessages(roomCode) }

    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(roomCode, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${members.size} member${if (members.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Screen.Invite.go(roomCode)) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Invite")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = vm.inputText,
                        onValueChange = { vm.inputText = it },
                        placeholder   = { Text("Message room…") },
                        modifier      = Modifier.weight(1f).testTag("room_message_input"),
                        maxLines      = 4,
                        shape         = androidx.compose.foundation.shape.CircleShape
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick        = { vm.send(roomCode) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier       = Modifier.size(48.dp)
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages) { msg ->
                val isMe = msg.sender == myUsername
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    if (!isMe) {
                        Text(
                            msg.sender,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(
                            topStart    = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd   = if (isMe) 4.dp else 16.dp
                        ),
                        color    = if (isMe) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text     = msg.plaintextCache ?: "…",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color    = if (isMe) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurface,
                            style    = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class RoomIdentityHelper @Inject constructor(store: IdentityStore) : androidx.lifecycle.ViewModel() {
    val username = store.username ?: ""
}
