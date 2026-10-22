package com.aivoicecursor.voice

import kotlinx.coroutines.flow.Flow

interface OfflineSpeechRecognizer {
    suspend fun start(): Result<Unit>
    fun stop()
    fun results(): Flow<String>
}
