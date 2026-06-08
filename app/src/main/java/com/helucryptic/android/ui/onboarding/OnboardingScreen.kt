package com.helucryptic.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun OnboardingScreen(nav: NavController, vm: OnboardingViewModel = hiltViewModel()) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(current = vm.step)
            Spacer(Modifier.height(32.dp))

            when (vm.step) {
                1 -> StepUsername(vm)
                2 -> StepFingerprint(vm)
                3 -> StepServer(vm, nav)
            }
        }
    }
}

@Composable
private fun StepUsername(vm: OnboardingViewModel) {
    Text("Pick a username", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Used to identify you to peers. Visible to people you connect with.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value         = vm.username,
        onValueChange = { vm.username = it },
        label         = { Text("USERNAME") },
        isError       = vm.usernameError != null,
        supportingText = vm.usernameError?.let { { Text(it) } },
        singleLine    = true,
        shape         = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier      = Modifier.fillMaxWidth().testTag("username_field")
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick  = { if (vm.validateAndAdvance()) vm.generateKeys() },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Continue") }
}

@Composable
private fun StepFingerprint(vm: OnboardingViewModel) {
    Text("Your identity key", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Share this fingerprint out-of-band so contacts can verify you.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    val chunks = vm.fingerprint.split(" ")
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(max = 200.dp)
    ) {
        items(chunks) { chunk ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text     = chunk,
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick  = { vm.step = 3 },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Looks good →") }
}

@Composable
private fun StepServer(vm: OnboardingViewModel, nav: NavController) {
    Text("Signaling & connection settings", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Configure signaling connection, optional TURN relay, and port forwarding.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value         = vm.serverUrl,
            onValueChange = { vm.serverUrl = it },
            label         = { Text("Server URL (wss://)") },
            singleLine    = true,
            shape         = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier      = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value         = vm.serverPassword,
            onValueChange = { vm.serverPassword = it },
            label         = { Text("Server Password") },
            singleLine    = true,
            shape         = CircleShape,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier      = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value         = vm.turnUrl,
            onValueChange = { vm.turnUrl = it },
            label         = { Text("TURN URL (turn:host:port)") },
            singleLine    = true,
            shape         = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier      = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value         = vm.turnUsername,
            onValueChange = { vm.turnUsername = it },
            label         = { Text("TURN Username") },
            singleLine    = true,
            shape         = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier      = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value         = vm.turnPassword,
            onValueChange = { vm.turnPassword = it },
            label         = { Text("TURN Password") },
            singleLine    = true,
            shape         = CircleShape,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier      = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = vm.portForwardEnabled,
                onCheckedChange = { vm.portForwardEnabled = it }
            )
            Spacer(Modifier.width(8.dp))
            Text("Enable port forwarding (VPN/router)", style = MaterialTheme.typography.bodyMedium)
        }

        if (vm.portForwardEnabled) {
            OutlinedTextField(
                value         = vm.forwardedPort,
                onValueChange = { vm.forwardedPort = it },
                label         = { Text("Forwarded Port") },
                singleLine    = true,
                shape         = CircleShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick  = { vm.finish(nav) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Start chatting →") }
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick  = { vm.serverUrl = ""; vm.serverPassword = ""; vm.turnUrl = ""; vm.turnUsername = ""; vm.turnPassword = ""; vm.portForwardEnabled = false; vm.forwardedPort = ""; vm.finish(nav) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Skip, use default") }
}

@Composable
private fun StepDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { i ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
            )
        }
    }
}
