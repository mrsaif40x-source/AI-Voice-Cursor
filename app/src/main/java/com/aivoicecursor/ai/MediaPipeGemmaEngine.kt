package com.aivoicecursor.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LocalLLMEngine backed by the MediaPipe LLM Inference runtime.
 * The model file is NOT bundled: place a runtime-compatible Gemma file in
 * <externalFilesDir>/models/ (see README). Loading and inference run off the UI thread.
 */
class MediaPipeGemmaEngine(private val context: Context) : LocalLLMEngine {
    private val lock = Any()
    @Volatile private var llm: LlmInference? = null

    fun modelDir(): File = File(context.getExternalFilesDir(null), "models")

    fun modelFile(): File? =
        modelDir().listFiles()?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            ?.maxByOrNull { it.length() }

    fun isModelInstalled(): Boolean = modelFile() != null

    override suspend fun isModelLoaded(): Boolean = llm != null

    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            synchronized(lock) {
                if (llm == null) {
                    val file = modelFile() ?: error("Model not installed")
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(file.absolutePath)
                        .setMaxTokens(1024)
                        .build()
                    llm = LlmInference.createFromOptions(context, options)
                }
            }
            Unit
        }
    }

    override fun unloadModel() {
        synchronized(lock) {
            runCatching { llm?.close() }
            llm = null
        }
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        synchronized(lock) {
            val engine = llm ?: error("Model not loaded")
            engine.generateResponse(prompt)
        }
    }

    private companion object {
        val EXTENSIONS = setOf("task", "bin", "litertlm")
    }
}
