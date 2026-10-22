package com.aivoicecursor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(vm: MainViewModel, onToggleListening: () -> Unit, onOpenAccessibility: () -> Unit) {
    val listening by vm.listening.collectAsState()
    val heard by vm.lastHeard.collectAsState()
    val status by vm.status.collectAsState()
    val model by vm.modelStatus.collectAsState()
    val speech by vm.speechStatus.collectAsState()
    val accessibility by vm.accessibilityOn.collectAsState()
    val history by vm.history.collectAsState()
    var typed by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Text("AI Voice Cursor", style = MaterialTheme.typography.headlineMedium) }
                item { Text("AI status: Offline (the app has no internet permission)") }
                item { Text("Model status: $model") }
                item { Text("Speech model: $speech") }
                item { Text("Accessibility: ${if (accessibility) "Enabled" else "Disabled"}") }
                item { Text("Status: $status") }
                item { Text("Current command: ${heard.ifBlank { "—" }}") }
                item {
                    Button(onClick = onToggleListening, modifier = Modifier.fillMaxWidth()) {
                        Text(if (listening) "Stop Listening" else "Start Listening")
                    }
                }
                item {
                    OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Accessibility settings")
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::loadModel) { Text("Load model") }
                        OutlinedButton(onClick = vm::unloadModel) { Text("Unload model") }
                    }
                }
                item {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type a command (no microphone needed)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Button(onClick = { vm.runTextCommand(typed); typed = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Run command")
                    }
                }
                item {
                    Text("Model folder: ${vm.modelPath}", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Text("Speech folder: ${vm.speechPath}", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Command history", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = vm::clearHistory) { Text("Clear") }
                    }
                }
                items(history, key = { it.id }) { e ->
                    Text(
                        (if (e.success) "✓ " else "✗ ") + e.command + "\n" + e.message,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
