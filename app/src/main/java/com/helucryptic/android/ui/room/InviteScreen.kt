package com.helucryptic.android.ui.room

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.helucryptic.android.ui.navigation.Screen
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    nav: NavController,
    roomCode: String?,
    viewModel: InviteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isScanOnly = roomCode == null || roomCode == "scan"
    
    val inviteState by viewModel.inviteState.collectAsState()

    LaunchedEffect(roomCode) {
        if (!isScanOnly) {
            viewModel.loadInvite(roomCode)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            viewModel.handleScannedInvite(
                inviteStr = result.contents,
                onSuccess = { targetRoom ->
                    Toast.makeText(context, "Successfully joined room $targetRoom", Toast.LENGTH_LONG).show()
                    nav.navigate(Screen.Room.go(targetRoom)) {
                        popUpTo(Screen.RoomList.route) { inclusive = false }
                    }
                },
                onFailure = {
                    Toast.makeText(context, "Invalid invite QR code format", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isScanOnly) "Join Room" else "Room Invite", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isScanOnly) {
                // Scan only layout
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(120.dp)
                        .padding(top = 40.dp)
                )

                Text(
                    text = "Scan Invite QR Code",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Scan a QR code from a friend's device to instantly import the room keys and join their secure, encrypted peer-to-peer room.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Align QR code inside the frame")
                            setBeepEnabled(false)
                            setBarcodeImageEnabled(false)
                            setOrientationLocked(true)
                            setCameraId(0)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Scanner")
                }
            } else {
                // Generate QR Code layout
                when (val state = inviteState) {
                    is InviteState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is InviteState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text("Failed to load room invite details.", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    is InviteState.Success -> {
                        Text(
                            text = "Room: $roomCode",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Have your friend scan this QR code to join your encrypted room. The invite contains room keys and signaling server address.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        val qrBitmap = remember(state.inviteString) {
                            try {
                                BarcodeEncoder().encodeBitmap(state.inviteString, BarcodeFormat.QR_CODE, 512, 512)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (qrBitmap != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .size(280.dp)
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Invite QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, state.inviteString)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Room Invite")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share text link")
                            }

                            OutlinedButton(
                                onClick = {
                                    val options = ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        setPrompt("Align QR code inside the frame")
                                        setBeepEnabled(false)
                                        setOrientationLocked(true)
                                        setCameraId(0)
                                    }
                                    scanLauncher.launch(options)
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan Another")
                            }
                        }
                    }
                }
            }
        }
    }
}
