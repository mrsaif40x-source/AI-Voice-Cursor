package com.aivoicecursor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Also covers parsing of raw AI responses (fences, prose around JSON, bad output). */
class ActionJsonParserTest {
    @Test fun parsesPlainJson() {
        val plan = ActionJsonParser.parse(
            """{"actions":[{"type":"CLICK","target":"Search"},{"type":"TYPE","text":"Python tutorial"}]}"""
        ).getOrThrow()
        assertEquals(
            listOf(
                AgentAction(ActionType.CLICK, target = "Search"),
                AgentAction(ActionType.TYPE, text = "Python tutorial")
            ),
            plan.actions
        )
    }

    @Test fun parsesFencedJsonWithProse() {
        val raw = "Sure!\n```json\n{\"actions\":[{\"type\":\"scroll_up\"}]}\n```\nDone."
        assertEquals(listOf(AgentAction(ActionType.SCROLL_UP)), ActionJsonParser.parse(raw).getOrThrow().actions)
    }

    @Test fun rejectsUnknownAction() =
        assertTrue(ActionJsonParser.parse("""{"actions":[{"type":"FORMAT_DISK"}]}""").isFailure)

    @Test fun rejectsInvalidJson() {
        assertTrue(ActionJsonParser.parse("not json at all").isFailure)
        assertTrue(ActionJsonParser.parse("{\"actions\": [").isFailure)
    }

    @Test fun rejectsMissingActions() = assertTrue(ActionJsonParser.parse("""{"foo":1}""").isFailure)

    @Test fun roundTrip() {
        val plan = ActionPlan(listOf(AgentAction(ActionType.WAIT, ms = 500), AgentAction(ActionType.SWIPE, direction = "UP")))
        assertEquals(plan, ActionJsonParser.parse(ActionJsonParser.toJson(plan)).getOrThrow())
    }
}
