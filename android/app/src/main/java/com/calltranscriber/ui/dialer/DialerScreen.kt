package com.calltranscriber.ui.dialer

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.recording.CallState

@Composable
fun DialerScreen(viewModel: DialerViewModel = hiltViewModel()) {
    val callState by viewModel.callState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val detectedNumber by viewModel.detectedNumber.collectAsState()
    val context = LocalContext.current
    val hasMicPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var manualNumber by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aufnahme", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Telefoniere normal ueber deine Telefon-App.\nDiese App nimmt das Gespraech auf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        // Phone state indicator
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Telefon-Status", style = MaterialTheme.typography.labelMedium)
                Text(
                    when (callState) {
                        CallState.IDLE -> "Kein Anruf"
                        CallState.RINGING -> "Eingehender Anruf..."
                        CallState.IN_CALL -> "Anruf aktiv"
                        CallState.ENDED -> "Anruf beendet"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (callState == CallState.IN_CALL) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                detectedNumber?.let {
                    Text("Nummer: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Manual number input (optional)
        OutlinedTextField(
            value = manualNumber,
            onValueChange = { manualNumber = it },
            label = { Text("Telefonnummer (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        if (!hasMicPermission) {
            Text("Mikrofon-Berechtigung noetig", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        // Recording controls
        if (!isRecording) {
            Button(
                onClick = { viewModel.startRecording(manualNumber.ifBlank { detectedNumber ?: "" }) },
                enabled = hasMicPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("Aufnahme starten") }
        } else {
            Text(
                "Aufnahme laeuft...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.stopRecording() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Aufnahme stoppen") }
        }
    }
}
