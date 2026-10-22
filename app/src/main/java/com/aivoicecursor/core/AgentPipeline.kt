package com.aivoicecursor.core

import com.aivoicecursor.accessibility.ActionExecutor
import com.aivoicecursor.accessibility.OverlayController
import com.aivoicecursor.accessibility.ScreenReader
import com.aivoicecursor.accessibility.VoiceCursorService
import com.aivoicecursor.ai.ActionPlanner
import com.aivoicecursor.data.HistoryRepository
import com.aivoicecursor.vision.ScreenVisionEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** command -> screen summary -> plan (LLM/rules) -> validation -> safety-gated execution -> history. */
class AgentPipeline(
    private val planner: ActionPlanner,
    private val history: HistoryRepository,
    private val vision: ScreenVisionEngine
) {
    private val mutex = Mutex()

    suspend fun handle(command: String): ExecutionResult = mutex.withLock {
        AgentRuntime.lastHeard.value = command
        AgentRuntime.status.value = "Thinking…"
        var plan: ActionPlan? = null
        val result = try {
            val service = VoiceCursorService.instance
            if (service == null) {
                ExecutionResult(false, "Accessibility service is not enabled")
            } else {
                val summary = ScreenSummarizer.summarize(ScreenReader(service).capture().elements)
                val planned = planner.plan(command, summary)
                plan = planned.getOrNull()
                if (plan == null) {
                    ExecutionResult(false, planned.exceptionOrNull()?.message ?: "Planning failed")
                } else {
                    AgentRuntime.status.value = "Executing…"
                    ActionExecutor(service, vision, OverlayController(service)).execute(plan)
                }
            }
        } catch (e: Exception) {
            ExecutionResult(false, "Error: ${e.message}")
        }
        runCatching { history.record(command, plan, result) }
        AgentRuntime.status.value = (if (result.success) "Done: " else "Failed: ") + result.message.take(160)
        result
    }
}
