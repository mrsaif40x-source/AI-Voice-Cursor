package com.aivoicecursor.ai

interface LocalLLMEngine {
    suspend fun generate(prompt: String): String
    suspend fun isModelLoaded(): Boolean
    suspend fun loadModel(): Result<Unit>
    fun unloadModel()
}
