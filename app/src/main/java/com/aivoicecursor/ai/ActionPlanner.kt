package com.aivoicecursor.ai

import com.aivoicecursor.core.ActionJsonParser
import com.aivoicecursor.core.ActionPlan
import com.aivoicecursor.core.ActionValidator
import com.aivoicecursor.core.VoiceCommandParser

/**
 * Command -> validated plan.
 * 1) local LLM (if loaded) with the compact screen summary, output parsed + validated;
 * 2) otherwise (or if the LLM output is rejected) the deterministic rule parser.
 */
class ActionPlanner(private val llm: LocalLLMEngine?) {

    suspend fun plan(command: String, screenSummary: String): Result<ActionPlan> {
        val cmd = command.trim()
        if (cmd.isEmpty()) return Result.failure(IllegalArgumentException("Empty command"))

        var note = ""
        if (llm != null && llm.isModelLoaded()) {
            val parsed = runCatching { llm.generate(PromptBuilder.build(cmd, screenSummary)) }
                .mapCatching { ActionJsonParser.parse(it).getOrThrow() }
                .mapCatching { ActionValidator.validate(it).getOrThrow() }
            if (parsed.isSuccess) return parsed
            note = " (AI output rejected: ${parsed.exceptionOrNull()?.message})"
        }

        VoiceCommandParser.parse(cmd)?.let { return ActionValidator.validate(it) }

        return Result.failure(
            IllegalArgumentException(
                if (llm != null && note.isEmpty())
                    "Command not understood. Install and load the local model for free-form commands."
                else "Command not understood$note"
            )
        )
    }
}
