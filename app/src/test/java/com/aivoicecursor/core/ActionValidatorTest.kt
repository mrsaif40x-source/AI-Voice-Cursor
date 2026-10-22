package com.aivoicecursor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {
    private fun plan(vararg a: AgentAction) = ActionPlan(a.toList())

    @Test fun validPlanPasses() {
        val p = plan(AgentAction(ActionType.CLICK, target = "Search"), AgentAction(ActionType.TYPE, text = "x"))
        assertTrue(ActionValidator.validate(p).isSuccess)
    }

    @Test fun rejectsEmptyAndMissingFields() {
        assertTrue(ActionValidator.validate(ActionPlan(emptyList())).isFailure)
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.CLICK))).isFailure)
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.TYPE))).isFailure)
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.SWIPE, direction = "SIDEWAYS"))).isFailure)
    }

    @Test fun rejectsUnsafeUrls() {
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.OPEN_URL, text = "javascript:alert(1)"))).isFailure)
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.OPEN_URL, text = "file:///sdcard/x"))).isFailure)
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.OPEN_URL, text = "https://example.com"))).isSuccess)
    }

    @Test fun limitsWaitAndSize() {
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.WAIT, ms = 99_999))).isFailure)
        assertTrue(ActionValidator.validate(plan(AgentAction(ActionType.WAIT, ms = 1_000))).isSuccess)
        val many = ActionPlan(List(ActionValidator.MAX_ACTIONS + 1) { AgentAction(ActionType.BACK) })
        assertTrue(ActionValidator.validate(many).isFailure)
    }

    @Test fun safetyLevels() {
        assertEquals(Safety.ALLOW, SafetyChecker.assess(AgentAction(ActionType.CLICK, target = "Search")))
        assertEquals(Safety.CONFIRM, SafetyChecker.assess(AgentAction(ActionType.CLICK, target = "Pay now")))
        assertEquals(Safety.CONFIRM, SafetyChecker.assess(AgentAction(ActionType.CLICK, target = "পেমেন্ট করুন")))
        assertEquals(Safety.CONFIRM, SafetyChecker.assess(AgentAction(ActionType.SCREENSHOT)))
        assertEquals(Safety.BLOCK, SafetyChecker.assess(AgentAction(ActionType.TYPE, target = "Password", text = "x")))
        assertEquals(Safety.ALLOW, SafetyChecker.assess(AgentAction(ActionType.TYPE, target = "Pinterest", text = "x")))
    }
}
