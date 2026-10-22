package com.aivoicecursor.ai

import com.aivoicecursor.core.ActionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPlannerTest {
    private class FakeLlm(var loaded: Boolean, var output: String) : LocalLLMEngine {
        var prompts = 0
        override suspend fun generate(prompt: String): String { prompts++; return output }
        override suspend fun isModelLoaded() = loaded
        override suspend fun loadModel(): Result<Unit> = Result.success(Unit)
        override fun unloadModel() { loaded = false }
    }

    @Test fun usesLlmPlanWhenValid() = runTest {
        val llm = FakeLlm(true, """{"actions":[{"type":"CLICK","target":"Search"}]}""")
        val plan = ActionPlanner(llm).plan("anything", "1. Button").getOrThrow()
        assertEquals(ActionType.CLICK, plan.actions.single().type)
        assertEquals(1, llm.prompts)
    }

    @Test fun fallsBackToRulesWhenLlmOutputInvalid() = runTest {
        val llm = FakeLlm(true, """{"actions":[{"type":"DELETE_EVERYTHING"}]}""")
        val plan = ActionPlanner(llm).plan("উপরে স্ক্রল করো", "").getOrThrow()
        assertEquals(ActionType.SCROLL_UP, plan.actions.single().type)
    }

    @Test fun usesRulesWhenModelNotLoaded() = runTest {
        val llm = FakeLlm(false, "")
        val plan = ActionPlanner(llm).plan("Python লিখো", "").getOrThrow()
        assertEquals("Python", plan.actions.single().text)
        assertEquals(0, llm.prompts)
    }

    @Test fun failsWhenNothingUnderstood() = runTest {
        assertTrue(ActionPlanner(null).plan("blah blah", "").isFailure)
        assertTrue(ActionPlanner(FakeLlm(true, "garbage")).plan("blah blah", "").isFailure)
        assertTrue(ActionPlanner(null).plan("  ", "").isFailure)
    }

    @Test fun promptContainsScreenAndCommand() {
        val p = PromptBuilder.build("Search করো", "1. Button\n   text=\"Search\"")
        assertTrue(p.contains("Search করো") && p.contains("text=\"Search\"") && p.contains("CLICK"))
    }
}
