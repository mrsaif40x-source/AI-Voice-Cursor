package com.aivoicecursor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {
    private fun one(s: String) = VoiceCommandParser.parse(s)!!.actions.single()

    @Test fun bengaliClick() =
        assertEquals(AgentAction(ActionType.CLICK, target = "Search"), one("Search বক্সে ক্লিক করো"))

    @Test fun bengaliType() =
        assertEquals(AgentAction(ActionType.TYPE, text = "Python"), one("Python লিখো"))

    @Test fun bengaliScrollUp() = assertEquals(AgentAction(ActionType.SCROLL_UP), one("উপরে স্ক্রল করো"))

    @Test fun bengaliScrollDown() = assertEquals(AgentAction(ActionType.SCROLL_DOWN), one("নিচে স্ক্রল করো"))

    @Test fun typeThenSearch() {
        val plan = VoiceCommandParser.parse("Python tutorial লিখে Search করো")!!
        assertEquals(
            listOf(
                AgentAction(ActionType.TYPE, text = "Python tutorial"),
                AgentAction(ActionType.CLICK, target = "Search")
            ),
            plan.actions
        )
    }

    @Test fun englishCommands() {
        assertEquals(AgentAction(ActionType.CLICK, target = "search"), one("click on the search box"))
        assertEquals(AgentAction(ActionType.SCROLL_DOWN), one("scroll down"))
        assertEquals(AgentAction(ActionType.BACK), one("go back"))
        assertEquals(AgentAction(ActionType.HOME), one("home"))
        assertEquals(AgentAction(ActionType.TYPE, text = "hello world"), one("type hello world"))
        assertEquals(AgentAction(ActionType.OPEN_APP, target = "YouTube"), one("open YouTube"))
    }

    @Test fun openUrl() =
        assertEquals(AgentAction(ActionType.OPEN_URL, text = "https://example.com"), one("open https://example.com"))

    @Test fun longClick() =
        assertEquals(AgentAction(ActionType.LONG_CLICK, target = "Photos"), one("Photos লং ক্লিক করো"))

    @Test fun unknownReturnsNull() {
        assertNull(VoiceCommandParser.parse("আজকের আবহাওয়া কেমন"))
        assertNull(VoiceCommandParser.parse("   "))
    }
}
