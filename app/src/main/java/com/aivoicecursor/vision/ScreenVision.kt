package com.aivoicecursor.vision

data class TargetCoordinates(val x: Int, val y: Int, val confidence: Float = 1f)

interface ScreenVisionEngine {
    suspend fun findTarget(screenshot: ByteArray, description: String): Result<TargetCoordinates>
}

/**
 * Honest default: no vision model is bundled, so it always reports failure.
 * Implement ScreenVisionEngine with a real detector and pass it from AppContainer to enable the fallback.
 */
class NoVisionEngine : ScreenVisionEngine {
    override suspend fun findTarget(screenshot: ByteArray, description: String): Result<TargetCoordinates> =
        Result.failure(UnsupportedOperationException("no vision model installed"))
}
