package com.helucryptic.android.ui.contacts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.helucryptic.android.ui.theme.DarkSuccess
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    nav: NavController,
    username: String,
    vm: ContactsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val contact  = contacts.firstOrNull { it.username == username }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            val scanned = result.contents
            val expectedVerifyPrefix = "HELUCRYPTIC-VERIFY:${contact?.username}:"
            if (contact != null && scanned.startsWith(expectedVerifyPrefix)) {
                val scannedFingerprint = scanned.removePrefix(expectedVerifyPrefix)
                if (scannedFingerprint == contact.fingerprint) {
                    vm.markVerified(contact.username)
                    Toast.makeText(context, "Verification Successful: Fingerprints match!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Verification Failed: Fingerprint mismatch!", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Invalid verification QR code format", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(username) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (contact == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Key-change warning
            if (contact.keyChanged) {
                Surface(
                    color  = MaterialTheme.colorScheme.errorContainer,
                    shape  = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null,
                             tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Key changed since last contact. Re-verify fingerprint.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text("Fingerprint", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Compare with your contact out-of-band to verify their identity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            val chunks = contact.fingerprint.split(" ")
            LazyVerticalGrid(
                columns               = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.heightIn(max = 240.dp)
            ) {
                items(chunks) { chunk ->
                    Surface(
                        shape    = MaterialTheme.shapes.small,
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.aspectRatio(1.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(chunk, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Verification Section (QR display + Scan button)
            Text("Verify Contact", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Verify this contact by scanning their QR code or letting them scan yours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            val verifyString = "HELUCRYPTIC-VERIFY:${contact.username}:${contact.fingerprint}"
            val verifyQrBitmap = remember(verifyString) {
                try {
                    BarcodeEncoder().encodeBitmap(verifyString, BarcodeFormat.QR_CODE, 400, 400)
                } catch (e: Exception) {
                    null
                }
            }

            if (verifyQrBitmap != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = verifyQrBitmap.asImageBitmap(),
                        contentDescription = "Verification QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (contact.verified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Rounded.Verified, contentDescription = "Verified",
                         tint = DarkSuccess)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Verified",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkSuccess
                    )
                }
            } else {
                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan contact's verification QR code")
                            setBeepEnabled(false)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan to Verify Contact")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { vm.markVerified(username) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) { Text("Mark as Verified Manually") }
            }
        }
    }
}
