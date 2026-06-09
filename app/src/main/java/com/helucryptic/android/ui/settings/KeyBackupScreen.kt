package com.helucryptic.android.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.helucryptic.android.ui.navigation.Screen
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyBackupScreen(
    nav: NavController,
    viewModel: KeyBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Export flow
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var exportPassphrase           by remember { mutableStateOf("") }
    var pendingExportUri           by remember { mutableStateOf<Uri?>(null) }

    // Import flow
    var showImportPassphraseDialog by remember { mutableStateOf(false) }
    var importPassphrase           by remember { mutableStateOf("") }
    var pendingImportUri           by remember { mutableStateOf<Uri?>(null) }
    var showOverwriteDialog        by remember { mutableStateOf(false) }

    // File pickers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) { pendingExportUri = uri; showExportPassphraseDialog = true } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) { pendingImportUri = uri; showOverwriteDialog = true } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key Backup & Restore", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Security Warning
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "Protect Your Key Backup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your backup is passphrase-encrypted with AES-256-GCM. " +
                            "Anyone with the file AND the passphrase can impersonate you. " +
                            "Use a strong, unique passphrase and store the backup safely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Export Section
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FileDownload, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Export Key Backup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        "Export your E2EE identity, encrypted with a passphrase you choose. " +
                        "Required to restore your account on another device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick  = { exportLauncher.launch("helucryptic-keys.hbk") },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Encrypted Backup")
                    }
                }
            }

            // Import Section
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FileUpload, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Import Key Backup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        "Restore your identity from an encrypted backup. " +
                        "This replaces all current keys on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick  = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.small,
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor   = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Backup")
                    }
                }
            }
        }
    }

    // ── Overwrite confirmation (before asking passphrase) ─────────────────────
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false; pendingImportUri = null },
            title = { Text("Replace Current Identity?") },
            text  = {
                Text(
                    "This will permanently overwrite your current E2EE keys. " +
                    "Any chats encrypted with the current keys will become unreadable. " +
                    "Are you sure?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { showOverwriteDialog = false; showImportPassphraseDialog = true },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Replace") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOverwriteDialog = false; pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Export passphrase dialog ──────────────────────────────────────────────
    if (showExportPassphraseDialog) {
        var confirmPassphrase by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showExportPassphraseDialog = false
                exportPassphrase = ""
                confirmPassphrase = ""
                pendingExportUri = null
            },
            title = { Text("Set Backup Passphrase") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose a strong passphrase to encrypt your backup. You will need it to restore.")
                    OutlinedTextField(
                        value         = exportPassphrase,
                        onValueChange = { exportPassphrase = it; error = null },
                        label         = { Text("Passphrase") },
                        singleLine    = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier             = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value         = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it; error = null },
                        label         = { Text("Confirm Passphrase") },
                        singleLine    = true,
                        isError       = error != null,
                        supportingText = error?.let { { Text(it) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier             = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        exportPassphrase.length < 8 ->
                            error = "Passphrase must be at least 8 characters"
                        exportPassphrase != confirmPassphrase ->
                            error = "Passphrases do not match"
                        else -> {
                            val uri = pendingExportUri
                            if (uri != null) {
                                val encrypted = viewModel.exportEncrypted(exportPassphrase)
                                if (encrypted != null) {
                                    try {
                                        context.contentResolver.openOutputStream(uri)?.use { out ->
                                            OutputStreamWriter(out).use { it.write(encrypted) }
                                        }
                                        Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "No identity to export", Toast.LENGTH_LONG).show()
                                }
                            }
                            showExportPassphraseDialog = false
                            exportPassphrase = ""
                            confirmPassphrase = ""
                            pendingExportUri = null
                        }
                    }
                }) { Text("Export") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showExportPassphraseDialog = false
                    exportPassphrase = ""
                    confirmPassphrase = ""
                    pendingExportUri = null
                }) { Text("Cancel") }
            }
        )
    }

    // ── Import passphrase dialog ──────────────────────────────────────────────
    if (showImportPassphraseDialog) {
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showImportPassphraseDialog = false
                importPassphrase = ""
                pendingImportUri = null
            },
            title = { Text("Enter Backup Passphrase") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter the passphrase you used when creating this backup.")
                    OutlinedTextField(
                        value         = importPassphrase,
                        onValueChange = { importPassphrase = it; error = null },
                        label         = { Text("Passphrase") },
                        singleLine    = true,
                        isError       = error != null,
                        supportingText = error?.let { { Text(it) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier             = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingImportUri
                    if (uri != null) {
                        try {
                            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                                BufferedReader(InputStreamReader(input)).readText()
                            } ?: run { error = "Could not read file"; return@Button }

                            if (viewModel.importEncrypted(json, importPassphrase)) {
                                Toast.makeText(context, "Identity imported successfully", Toast.LENGTH_LONG).show()
                                showImportPassphraseDialog = false
                                importPassphrase = ""
                                pendingImportUri = null
                                nav.navigate(Screen.ChatList.route) { popUpTo(0) { inclusive = true } }
                            } else {
                                error = "Wrong passphrase or corrupt backup"
                            }
                        } catch (e: Exception) {
                            error = "Import failed: ${e.message}"
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showImportPassphraseDialog = false
                    importPassphrase = ""
                    pendingImportUri = null
                }) { Text("Cancel") }
            }
        )
    }
}
