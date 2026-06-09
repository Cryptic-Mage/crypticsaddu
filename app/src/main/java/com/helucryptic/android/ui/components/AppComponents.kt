package com.helucryptic.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import kotlin.math.absoluteValue

// ── Avatar ────────────────────────────────────────────────────────────────────

private val avatarPalette = listOf(
    Color(0xFFC0714A), // terracotta
    Color(0xFF4488FF), // blue
    Color(0xFF44BB88), // teal
    Color(0xFFAA55CC), // purple
    Color(0xFFFF7744), // orange
    Color(0xFF44AACC), // cyan
    Color(0xFFCC4488), // pink
    Color(0xFF88AA44), // olive
)

/** Deterministic color for a given username. */
fun avatarColorFor(username: String): Color =
    avatarPalette[username.hashCode().absoluteValue % avatarPalette.size]

/** Round avatar showing the first initial with a username-derived color. */
@Composable
fun AvatarCircle(
    username: String,
    size: Dp = 48.dp,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColorFor(username)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = username.take(1).uppercase(),
            style = textStyle,
            color = Color.White
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

/**
 * Centered empty-state placeholder: large icon, title, body.
 * Wrap with padding as needed.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector     = icon,
            contentDescription = null,
            modifier        = Modifier.size(72.dp),
            tint            = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text      = title,
            style     = MaterialTheme.typography.titleMedium,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = body,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── AnimatedContent helper ────────────────────────────────────────────────────

/**
 * Crossfades between [EmptyState] and the list content based on [isEmpty].
 * Use as a drop-in replacement for the manual if/else pattern.
 */
@Composable
fun ListOrEmpty(
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = isEmpty,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
        },
        label = "listOrEmpty",
        modifier = modifier
    ) { empty ->
        if (empty) {
            EmptyState(icon = emptyIcon, title = emptyTitle, body = emptyBody)
        } else {
            content()
        }
    }
}

// ── Send button ───────────────────────────────────────────────────────────────

/**
 * FAB-style send button that bounces into view when [hasText] becomes true.
 */
@Composable
fun AnimatedSendButton(hasText: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue    = if (hasText) 1f else 0.72f,
        animationSpec  = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "sendScale"
    )
    FloatingActionButton(
        onClick        = onClick,
        modifier       = Modifier.size(48.dp).scale(scale),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector        = Icons.AutoMirrored.Rounded.Send,
            contentDescription = "Send"
        )
    }
}
