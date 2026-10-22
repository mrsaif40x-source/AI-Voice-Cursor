package com.aivoicecursor.voice

import android.content.Context
import com.aivoicecursor.core.AgentRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/** Offline recognizer using a Vosk model stored in <externalFilesDir>/vosk-model/. */
class VoskSpeechRecognizer(private val context: Context) : OfflineSpeechRecognizer {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var model: Model? = null
    private var service: SpeechService? = null

    fun modelDir(): File = File(context.getExternalFilesDir(null), "vosk-model")

    fun isModelInstalled(): Boolean = modelDir().list()?.isNotEmpty() == true

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {}
        override fun onResult(hypothesis: String?) = emit(hypothesis)
        override fun onFinalResult(hypothesis: String?) = emit(hypothesis)
        override fun onError(exception: Exception?) {
            AgentRuntime.status.value = "Speech error: ${exception?.message}"
        }
        override fun onTimeout() {}
    }

    private fun emit(json: String?) {
        val text = runCatching { JSONObject(json ?: "").optString("text") }.getOrDefault("").trim()
        if (text.isNotEmpty()) flow.tryEmit(text)
    }

    override suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (service != null) return@runCatching
            check(isModelInstalled()) { "Speech model not installed (${modelDir().absolutePath})" }
            val m = model ?: Model(modelDir().absolutePath).also { model = it }
            val recognizer = Recognizer(m, SAMPLE_RATE)
            service = SpeechService(recognizer, SAMPLE_RATE).also { it.startListening(listener) }
        }
    }

    override fun stop() {
        service?.stop()
        service?.shutdown()
        service = null
    }

    override fun results(): Flow<String> = flow.asSharedFlow()

    private companion object {
        const val SAMPLE_RATE = 16000.0f
    }
}
