package com.aivoicecursor.ai

import com.aivoicecursor.core.ActionJsonParser
import com.aivoicecursor.core.ActionPlan
import com.aivoicecursor.core.ActionValidator
import com.aivoicecursor.core.VoiceCommandParser

class ActionPlanner(private val llmEngine: LocalLLMEngine?) {

    suspend fun plan(command: String, screenSummary: String): Result<ActionPlan> {
        val cmd = command.trim()
        if (cmd.isEmpty()) return Result.failure(IllegalArgumentException("Empty command"))

        val engine = llmEngine
        var note = ""
        if (engine != null && engine.isModelLoaded()) {
            val prompt = PromptBuilder.build(cmd, screenSummary)
            val parsed: Result<ActionPlan> = try {
                ActionJsonParser.parse(engine.generate(prompt)).mapCatching { ActionValidator.validate(it).getOrThrow() }
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (parsed.isSuccess) return parsed
            note = " (AI output rejected: ${parsed.exceptionOrNull()?.message})"
        }

        VoiceCommandParser.parse(cmd)?.let { return ActionValidator.validate(it) }

        return Result.failure(
            IllegalArgumentException(
                if (engine != null && note.isEmpty())
                    "Command not understood. Install and load the local model for free-form commands."
                else "Command not understood$note"
            )
        )
    }
}
