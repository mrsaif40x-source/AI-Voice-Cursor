package com.aivoicecursor.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aivoicecursor.AiVoiceCursorApp
import com.aivoicecursor.accessibility.VoiceCursorService
import com.aivoicecursor.core.AgentRuntime
import com.aivoicecursor.data.HistoryEntity
import com.aivoicecursor.voice.VoiceAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val container = (app as AiVoiceCursorApp).container

    val listening: StateFlow<Boolean> = AgentRuntime.listening
    val lastHeard: StateFlow<String> = AgentRuntime.lastHeard
    val status: StateFlow<String> = AgentRuntime.status
    val accessibilityOn: StateFlow<Boolean> = VoiceCursorService.connected
    val history: StateFlow<List<HistoryEntity>> =
        container.history.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _modelStatus = MutableStateFlow("…")
    val modelStatus: StateFlow<String> = _modelStatus
    private val _speechStatus = MutableStateFlow("…")
    val speechStatus: StateFlow<String> = _speechStatus

    val modelPath: String get() = container.llm.modelDir().absolutePath
    val speechPath: String get() = container.recognizer.modelDir().absolutePath

    fun refresh() {
        viewModelScope.launch { updateStatus() }
    }

    private suspend fun updateStatus() {
        _modelStatus.value = when {
            container.llm.isModelLoaded() -> "Loaded"
            container.llm.isModelInstalled() -> "Installed (not loaded)"
            else -> "Not installed"
        }
        _speechStatus.value = if (container.recognizer.isModelInstalled()) "Installed" else "Not installed"
    }

    fun loadModel() {
        viewModelScope.launch {
            _modelStatus.value = "Loading…"
            container.llm.loadModel().onFailure { AgentRuntime.status.value = "Model: ${it.message}" }
            updateStatus()
        }
    }

    fun unloadModel() {
        viewModelScope.launch(Dispatchers.Default) {
            container.llm.unloadModel()
            updateStatus()
        }
    }

    fun runTextCommand(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { container.pipeline.handle(text.trim()) }
    }

    fun startListening() {
        val i = Intent(app, VoiceAgentService::class.java).setAction(VoiceAgentService.ACTION_START)
        ContextCompat.startForegroundService(app, i)
    }

    fun stopListening() {
        app.startService(Intent(app, VoiceAgentService::class.java).setAction(VoiceAgentService.ACTION_STOP))
    }

    fun clearHistory() {
        viewModelScope.launch { container.history.clear() }
    }
}
