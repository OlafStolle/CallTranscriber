package com.calltranscriber.ui.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CallDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val callRepository: CallRepository) : ViewModel() {
    private val callId: String = savedStateHandle["callId"] ?: ""
    private val _call = MutableStateFlow<CallEntity?>(null)
    val call = _call.asStateFlow()
    init { viewModelScope.launch { _call.value = callRepository.getCallById(callId) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(onBack: () -> Unit, viewModel: CallDetailViewModel = hiltViewModel()) {
    val call by viewModel.call.collectAsState()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Scaffold(topBar = { TopAppBar(title = { Text(call?.remoteNumber ?: "Gespraech") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck") } }) }) { padding ->
        call?.let { c ->
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Nummer: ${c.remoteNumber}", style = MaterialTheme.typography.titleMedium)
                Text("Richtung: ${if (c.direction == "inbound") "Eingehend" else "Ausgehend"}")
                Text("Datum: ${fmt.format(Date(c.startedAt))}")
                c.durationSeconds?.let { Text("Dauer: ${it / 60}:${"%02d".format(it % 60)} Min") }
                Text("Status: ${c.status}")
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))
                Text("Transkript", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp))
                c.transcriptText?.let { Text(it) } ?: Text("Transkript wird erstellt...", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
