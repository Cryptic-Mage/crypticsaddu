package com.helucryptic.android.ui.room

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.helucryptic.android.data.db.RoomEntity
import com.helucryptic.android.data.repository.RoomRepository
import com.helucryptic.android.signaling.SignalingState
import com.helucryptic.android.ui.components.ListOrEmpty
import com.helucryptic.android.ui.navigation.Screen
import com.helucryptic.android.ui.theme.DarkSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(nav: NavController) {
    val vm = hiltViewModel<RoomListViewModel>()
    val context = LocalContext.current
    val rooms by vm.rooms.collectAsState(initial = emptyList())
    val username = vm.username
    val connectionState by vm.connectionState.collectAsState()
    var showRoomDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.connectionError.collect { error ->
            if (error.isNotBlank())
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    if (showRoomDialog) {
        AlertDialog(
            onDismissRequest = { showRoomDialog = false },
            icon = { Icon(Icons.Rounded.MeetingRoom, contentDescription = null) },
            title = { Text("Rooms") },
            text = { Text("Create a new encrypted room, or scan a friend's invite QR code to join theirs.") },
            confirmButton = {
                Button(onClick = {
                    showRoomDialog = false
                    vm.createRoom { code -> nav.navigate(Screen.Room.go(code)) }
                }) { Text("Create Room") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showRoomDialog = false
                    nav.navigate(Screen.Invite.go("scan"))
                }) { Text("Scan Invite") }
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
                                Text("Rooms", style = MaterialTheme.typography.titleMedium)
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
                            val isActive = connectionState != SignalingState.DISCONNECTED
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
                            IconButton(
                                onClick = { if (isActive) vm.disconnect() else vm.connect() },
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
                onClick = { showRoomDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Rounded.Add, contentDescription = "New Room") }
        }
    ) { padding ->
        ListOrEmpty(
            isEmpty    = rooms.isEmpty(),
            emptyIcon  = Icons.Rounded.MeetingRoom,
            emptyTitle = "No rooms yet",
            emptyBody  = "Tap + to create an encrypted room or scan an invite QR.",
            modifier   = Modifier.padding(padding)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rooms, key = { it.roomCode }) { room ->
                    RoomRow(room) { nav.navigate(Screen.Room.go(room.roomCode)) }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
fun PulsingDot(state: SignalingState) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    val alpha = when (state) {
        SignalingState.CONNECTING, SignalingState.RECONNECTING -> blinkAlpha
        else -> 1f
    }
    val color = when (state) {
        SignalingState.CONNECTED    -> DarkSuccess                           // green  — peer in room
        SignalingState.SIGNALING    -> Color(0xFF4488FF)                     // blue   — on relay, idle
        SignalingState.CONNECTING,
        SignalingState.RECONNECTING -> Color(0xFFFFAA00)                     // amber  — in-flight
        SignalingState.DISCONNECTED -> MaterialTheme.colorScheme.outline     // grey   — offline
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun RoomRow(room: RoomEntity, onClick: () -> Unit) {
    ListItem(
        modifier          = Modifier.clickable(onClick = onClick),
        headlineContent   = { Text(room.roomCode) },
        supportingContent = {
            Text(
                "Creator: ${room.creatorUsername}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                Icons.Rounded.MeetingRoom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
    )
}

@HiltViewModel
class RoomListViewModel @Inject constructor(
    private val repo: RoomRepository,
    private val identityStore: com.helucryptic.android.crypto.IdentityStore,
    private val connectionManager: com.helucryptic.android.webrtc.ConnectionManager,
    private val signalingClient: com.helucryptic.android.signaling.SignalingClient
) : ViewModel() {
    val rooms = repo.observeAll()
    val username = identityStore.username ?: ""
    val connectionState = signalingClient.state
    val connectionError  = signalingClient.lastError

    fun connect() = connectionManager.connectGlobal()
    fun disconnect() = connectionManager.disconnect()

    fun createRoom(onCreated: (String) -> Unit) {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rng = java.security.SecureRandom()
        val code = (1..8).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        val psk = ByteArray(32).also { rng.nextBytes(it) }
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        viewModelScope.launch {
            repo.upsert(code, psk, username)
            onCreated(code)
        }
    }
}
