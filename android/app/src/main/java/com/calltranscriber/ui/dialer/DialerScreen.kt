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
import com.calltranscriber.sip.CallState

@Composable
fun DialerScreen(viewModel: DialerViewModel = hiltViewModel()) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val context = LocalContext.current
    val hasMicPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Anrufen", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = phoneNumber, onValueChange = viewModel::onNumberChanged, label = { Text("Telefonnummer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))

        if (!hasMicPermission) {
            Text("Mikrofon-Berechtigung noetig", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        when (callState) {
            CallState.IDLE, CallState.ENDED -> {
                Button(
                    onClick = { viewModel.makeCall() },
                    enabled = phoneNumber.isNotBlank() && hasMicPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (hasMicPermission) "Anrufen" else "Berechtigung fehlt") }
            }
            CallState.RINGING -> {
                Text("Verbindung wird aufgebaut...", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.hangUp() }, modifier = Modifier.fillMaxWidth()) { Text("Auflegen") }
            }
            CallState.CONNECTED -> {
                Text("Gespraech laeuft — wird aufgezeichnet", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.hangUp() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Auflegen") }
            }
        }
    }
}
