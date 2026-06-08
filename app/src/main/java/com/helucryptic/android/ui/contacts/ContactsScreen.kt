package com.helucryptic.android.ui.contacts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.helucryptic.android.ui.room.PulsingDot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.helucryptic.android.signaling.SignalingState
import com.helucryptic.android.ui.navigation.Screen
import com.helucryptic.android.ui.theme.DarkSuccess
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(nav: NavController, vm: ContactsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val username = vm.username
    val connectionState by vm.connectionState.collectAsState()

    var showMyQrDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var addContactUsernameInput by remember { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            val scanned = result.contents
            val prefix = "HELUCRYPTIC-CONTACT:"
            if (scanned.startsWith(prefix)) {
                val name = scanned.removePrefix(prefix).trim()
                if (name.isNotEmpty()) {
                    vm.addContact(name)
                    Toast.makeText(context, "Contact '$name' added!", Toast.LENGTH_SHORT).show()
                }
            } else {
                val name = scanned.trim()
                if (name.isNotEmpty() && !name.contains(":")) {
                    vm.addContact(name)
                    Toast.makeText(context, "Contact '$name' added!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid QR code format for adding contact", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showMyQrDialog) {
        val qrString = "HELUCRYPTIC-CONTACT:$username"
        val qrBitmap = remember(qrString) {
            try {
                BarcodeEncoder().encodeBitmap(qrString, BarcodeFormat.QR_CODE, 400, 400)
            } catch (e: Exception) {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { showMyQrDialog = false },
            title = { Text("My Contact QR") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Let others scan this QR to add you as a contact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    if (qrBitmap != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            modifier = Modifier.size(200.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "My QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyQrDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false; addContactUsernameInput = "" },
            title = { Text("Add Contact") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Enter the username of the contact you want to add.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = addContactUsernameInput,
                        onValueChange = { addContactUsernameInput = it },
                        label = { Text("Username") },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan a contact's username QR code")
                                setBeepEnabled(false)
                            }
                            scanLauncher.launch(options)
                            showAddContactDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR Code")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = addContactUsernameInput.trim()
                        if (name.isNotEmpty()) {
                            vm.addContact(name)
                            Toast.makeText(context, "Contact '$name' added!", Toast.LENGTH_SHORT).show()
                        }
                        showAddContactDialog = false
                        addContactUsernameInput = ""
                    },
                    shape = CircleShape
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false; addContactUsernameInput = "" }) { Text("Cancel") }
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
                                Text("Contacts", style = MaterialTheme.typography.titleMedium)
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
                },
                actions = {
                    IconButton(onClick = { showMyQrDialog = true }) {
                        Icon(Icons.Rounded.QrCode, contentDescription = "My QR Code")
                    }
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Contact")
                    }
                }
            )
        }
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No contacts yet. Connect with someone to add them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(contacts) { contact ->
                    ListItem(
                        modifier        = Modifier
                            .clickable { nav.navigate(Screen.ContactDetail.go(contact.username)) }
                            .testTag("contact_row_${contact.username}"),
                        headlineContent = { Text(contact.username) },
                        supportingContent = {
                            Text(
                                if (contact.fingerprint.isNotEmpty()) contact.fingerprint.take(9) + "…" else "Not connected yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                color    = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        contact.username.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        },
                        trailingContent = if (contact.verified) ({
                            Icon(
                                Icons.Rounded.Verified,
                                contentDescription = "Verified",
                                tint               = DarkSuccess,
                                modifier           = Modifier.size(20.dp)
                            )
                        }) else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}
