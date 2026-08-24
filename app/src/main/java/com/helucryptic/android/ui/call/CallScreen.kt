package com.helucryptic.android.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.helucryptic.android.ui.theme.DarkBackground
import com.helucryptic.android.ui.theme.DarkAccent
import com.helucryptic.android.ui.theme.DarkSuccess

@Composable
fun CallScreen(
    nav: NavController,
    peerId: String,
    vm: CallViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { vm.startTimer() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DarkBackground, Color.Black))
            )
    ) {
        // PiP overlay - local camera placeholder
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(width = 70.dp, height = 100.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Videocam,
                    contentDescription = "Local camera",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Main content
        Column(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar with terracotta ring
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape    = CircleShape,
                    color    = DarkAccent
                ) {}
                Surface(
                    modifier = Modifier.size(68.dp).clip(CircleShape),
                    color    = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            peerId.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(peerId, style = MaterialTheme.typography.titleLarge,
                 color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock, contentDescription = null,
                    tint     = DarkSuccess,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Connected · E2EE",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkSuccess
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                formatTimer(vm.elapsedSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // 4-button row - 36dp icons, 8dp gaps, total ≤ 170dp
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CallButton(
                    icon    = if (vm.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label   = if (vm.muted) "Unmute" else "Mute",
                    onClick = { vm.toggleMute() }
                )
                CallButton(
                    icon    = if (vm.videoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label   = "Video",
                    onClick = { vm.toggleVideo() }
                )
                CallButton(
                    icon    = if (vm.speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    label   = "Speaker",
                    onClick = { vm.toggleSpeaker() }
                )
                CallButton(
                    icon           = Icons.Rounded.CallEnd,
                    label          = "End",
                    containerColor = MaterialTheme.colorScheme.error,
                    onClick        = { nav.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick  = onClick,
            modifier = Modifier.size(52.dp),
            colors   = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
