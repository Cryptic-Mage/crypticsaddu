package com.helucryptic.android.ui.settings

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.helucryptic.android.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val wiped     by viewModel.wiped.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    LaunchedEffect(wiped) {
        if (wiped) nav.navigate(Screen.Onboarding.route) { popUpTo(0) { inclusive = true } }
    }
    LaunchedEffect(loggedOut) {
        if (loggedOut) nav.navigate(Screen.Onboarding.route) { popUpTo(0) { inclusive = true } }
    }

    var showWipeDialog   by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val previewPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { previewPlayer.value?.release() } }

    fun playPreview(key: String) {
        previewPlayer.value?.release()
        val resId = context.resources.getIdentifier(key, "raw", context.packageName)
        if (resId == 0) return
        val mp = MediaPlayer.create(context, resId) ?: return
        mp.setOnCompletionListener { it.release(); previewPlayer.value = null }
        mp.start()
        previewPlayer.value = mp
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset account?") },
            text = {
                Text(
                    "This will permanently delete your identity keys, all messages, contacts, and rooms. " +
                    "You will be sent back to onboarding. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWipeDialog = false; viewModel.wipeAccount() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe & Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text("Log out?") },
            text = { Text("Your identity keys will be cleared from this device. Your rooms and messages stay but will be inaccessible until you restore your keys. Use Backup Keys first if you haven't already.") },
            confirmButton = {
                Button(onClick = { showLogoutDialog = false; viewModel.logOut() }) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    val signalingUrl by viewModel.signalingUrl.collectAsState()
    val serverPassword by viewModel.serverPassword.collectAsState()
    val turnUrl by viewModel.turnUrl.collectAsState()
    val turnUsername by viewModel.turnUsername.collectAsState()
    val turnPassword by viewModel.turnPassword.collectAsState()
    val portForwardEnabled by viewModel.portForwardEnabled.collectAsState()
    val forwardedPort by viewModel.forwardedPort.collectAsState()
    val theme             by viewModel.theme.collectAsState()
    val notifSound        by viewModel.notificationSound.collectAsState()
    val ringSound         by viewModel.ringtoneSound.collectAsState()

    var urlInput          by remember(signalingUrl) { mutableStateOf(signalingUrl) }
    var passwordInput     by remember(serverPassword) { mutableStateOf(serverPassword) }
    var turnUrlInput      by remember(turnUrl) { mutableStateOf(turnUrl) }
    var turnUsernameInput by remember(turnUsername) { mutableStateOf(turnUsername) }
    var turnPasswordInput by remember(turnPassword) { mutableStateOf(turnPassword) }
    var portForwardInput  by remember(portForwardEnabled) { mutableStateOf(portForwardEnabled) }
    var forwardedPortInput by remember(forwardedPort) { mutableStateOf(if (forwardedPort == 0) "" else forwardedPort.toString()) }
    var themeExpanded     by remember { mutableStateOf(false) }
    var notifExpanded     by remember { mutableStateOf(false) }
    var ringExpanded      by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // PROFILE SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROFILE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Rounded.Person,
                            title = "Username",
                            subtitle = viewModel.username,
                            onClick = {}
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Fingerprint,
                            title = "My fingerprint",
                            subtitle = viewModel.fingerprint.ifEmpty { "Not generated" },
                            onClick = {
                                if (viewModel.fingerprint.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(viewModel.fingerprint))
                                    Toast.makeText(context, "Fingerprint copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Backup,
                            title = "Backup keys",
                            subtitle = "Export or import your E2EE key identity",
                            showChevron = true,
                            onClick = { nav.navigate("key_backup") }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            title = "Log out",
                            subtitle = "Sign out and clear identity keys from device",
                            onClick = { showLogoutDialog = true }
                        )
                    }
                }
            }

            // CONNECTION SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CONNECTION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Connection Settings Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = "Connection Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Connection Settings",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        // Connection Fields
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = {
                                    urlInput = it
                                    viewModel.setUrl(it)
                                },
                                label = { Text("Signaling Server URL") },
                                placeholder = { Text("wss://...") },
                                singleLine = true,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    viewModel.setServerPassword(it)
                                },
                                label = { Text("Server Password") },
                                singleLine = true,
                                shape = CircleShape,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = turnUrlInput,
                                onValueChange = {
                                    turnUrlInput = it
                                    viewModel.setTurnUrl(it)
                                },
                                label = { Text("TURN URL (turn:host:port)") },
                                placeholder = { Text("turn:...") },
                                singleLine = true,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = turnUsernameInput,
                                onValueChange = {
                                    turnUsernameInput = it
                                    viewModel.setTurnUsername(it)
                                },
                                label = { Text("TURN Username") },
                                singleLine = true,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = turnPasswordInput,
                                onValueChange = {
                                    turnPasswordInput = it
                                    viewModel.setTurnPassword(it)
                                },
                                label = { Text("TURN Password") },
                                singleLine = true,
                                shape = CircleShape,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = portForwardInput,
                                    onCheckedChange = {
                                        portForwardInput = it
                                        viewModel.setPortForwardEnabled(it)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Enable port forwarding (VPN/router)", style = MaterialTheme.typography.bodyMedium)
                            }

                            if (portForwardInput) {
                                OutlinedTextField(
                                    value = forwardedPortInput,
                                    onValueChange = {
                                        forwardedPortInput = it
                                        val port = it.toIntOrNull() ?: 0
                                        viewModel.setForwardedPort(port)
                                    },
                                    label = { Text("Forwarded Port") },
                                    singleLine = true,
                                    shape = CircleShape,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.resetUrl()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Reset to Defaults")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Theme Dropdown Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = "Theme",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        text = "Theme",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = theme.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = themeExpanded,
                                onExpandedChange = { themeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = theme.replaceFirstChar { it.uppercase() },
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .width(130.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = themeExpanded,
                                    onDismissRequest = { themeExpanded = false }
                                ) {
                                    listOf("system", "light", "dark").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                viewModel.setTheme(option)
                                                themeExpanded = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Export Keys Row (Alternate access to backup)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate("key_backup") }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = "Export Keys",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export keys",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Save E2EE identity keys to file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // SOUNDS SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SOUNDS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Notification sound
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        "Notification sound",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = viewModel.notificationOptions.find { it.first == notifSound }?.second ?: notifSound,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { playPreview(notifSound) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview notification sound", tint = MaterialTheme.colorScheme.primary)
                            }
                            ExposedDropdownMenuBox(
                                expanded = notifExpanded,
                                onExpandedChange = { notifExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = viewModel.notificationOptions.find { it.first == notifSound }?.second ?: notifSound,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = notifExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .width(150.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = notifExpanded,
                                    onDismissRequest = { notifExpanded = false }
                                ) {
                                    viewModel.notificationOptions.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setNotificationSound(key)
                                                notifExpanded = false
                                                playPreview(key)
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Ringtone (calls)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        "Ringtone",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = viewModel.ringtoneOptions.find { it.first == ringSound }?.second ?: ringSound,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { playPreview(ringSound) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview ringtone", tint = MaterialTheme.colorScheme.primary)
                            }
                            ExposedDropdownMenuBox(
                                expanded = ringExpanded,
                                onExpandedChange = { ringExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = viewModel.ringtoneOptions.find { it.first == ringSound }?.second ?: ringSound,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ringExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .width(150.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = ringExpanded,
                                    onDismissRequest = { ringExpanded = false }
                                ) {
                                    viewModel.ringtoneOptions.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setRingtoneSound(key)
                                                ringExpanded = false
                                                playPreview(key)
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DANGER ZONE
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DANGER ZONE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWipeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Reset account",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                            Text(
                                "Wipe all keys, messages, contacts, and rooms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
