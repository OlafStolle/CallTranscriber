// DialerScreen.kt
package com.calltranscriber.ui.dialer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.sip.CallState

@Composable
fun DialerScreen(viewModel: DialerViewModel = hiltViewModel()) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val callState by viewModel.callState.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Anrufen", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = phoneNumber, onValueChange = viewModel::onNumberChanged, label = { Text("Telefonnummer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        when (callState) {
            CallState.IDLE, CallState.ENDED -> Button(onClick = { viewModel.makeCall() }, enabled = phoneNumber.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Anrufen") }
            CallState.RINGING -> { Text("Verbindung wird aufgebaut..."); Spacer(Modifier.height(8.dp)); Button(onClick = { viewModel.hangUp() }, modifier = Modifier.fillMaxWidth()) { Text("Auflegen") } }
            CallState.CONNECTED -> { Text("Gespraech laeuft — wird aufgezeichnet", color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Button(onClick = { viewModel.hangUp() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Auflegen") } }
        }
    }
}
