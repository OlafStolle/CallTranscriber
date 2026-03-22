package com.calltranscriber.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.data.local.CallEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallListScreen(onCallClick: (String) -> Unit, onDialerClick: () -> Unit, viewModel: CallListViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Gespraeche") }) }, floatingActionButton = { FloatingActionButton(onClick = onDialerClick) { Icon(Icons.Default.Phone, "Anrufen") } }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(value = searchQuery, onValueChange = viewModel::onSearchQueryChanged, label = { Text("Suchen...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true)
            if (calls.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Gespraeche") } }
            else { LazyColumn { items(calls, key = { it.id }) { call -> CallCard(call) { onCallClick(call.id) } } } }
        }
    }
}

@Composable
fun CallCard(call: CallEntity, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(call.remoteNumber, style = MaterialTheme.typography.titleMedium); Text(if (call.direction == "inbound") "Eingehend" else "Ausgehend", style = MaterialTheme.typography.labelMedium) }
            Text(fmt.format(Date(call.startedAt)), style = MaterialTheme.typography.bodySmall)
            call.durationSeconds?.let { Text("${it / 60}:${"%02d".format(it % 60)} Min", style = MaterialTheme.typography.bodySmall) }
            call.transcriptText?.let { Text(it.take(120) + if (it.length > 120) "..." else "", style = MaterialTheme.typography.bodySmall, maxLines = 2) } ?: Text(call.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
