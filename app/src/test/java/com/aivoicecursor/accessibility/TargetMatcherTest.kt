package com.aivoicecursor.accessibility

import com.aivoicecursor.core.Prefer
import com.aivoicecursor.core.ScreenSummarizer
import com.aivoicecursor.core.TargetMatcher
import com.aivoicecursor.core.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetMatcherTest {
    private val field = UiElement(0, "EditText", hint = "Search", editable = true, clickable = true)
    private val button = UiElement(1, "Button", text = "Search", clickable = true)
    private val other = UiElement(2, "Button", text = "Settings", clickable = true)
    private val all = listOf(field, button, other)

    @Test fun prefersEditableOrButtonAsRequested() {
        assertEquals(0, TargetMatcher.best(all, "Search", Prefer.EDITABLE)?.index)
        assertEquals(1, TargetMatcher.best(all, "Search", Prefer.NON_EDITABLE)?.index)
    }

    @Test fun ignoresNoiseWordsAndCase() =
        assertEquals(2, TargetMatcher.best(all, "settings button")?.index)

    @Test fun matchesResourceIdAndDescription() {
        val e = listOf(
            UiElement(0, "ImageView", description = "Open menu", clickable = true),
            UiElement(1, "EditText", resourceId = "com.app:id/search_input", editable = true)
        )
        assertEquals(0, TargetMatcher.best(e, "menu")?.index)
        assertEquals(1, TargetMatcher.best(e, "search")?.index)
    }

    @Test fun skipsDisabledAndUnknown() {
        assertNull(TargetMatcher.best(listOf(button.copy(enabled = false)), "Search"))
        assertNull(TargetMatcher.best(all, "Checkout"))
    }

    @Test fun bengaliLabels() {
        val e = listOf(UiElement(0, "Button", text = "সার্চ করুন", clickable = true))
        assertEquals(0, TargetMatcher.best(e, "সার্চ")?.index)
    }

    @Test fun summaryContainsOnlyUsefulData() {
        val s = ScreenSummarizer.summarize(all + UiElement(3, "TextView", text = "just text"))
        assertTrue(s.contains("hint=\"Search\""))
        assertTrue(s.contains("editable=true"))
        assertTrue(!s.contains("just text"))
    }
}
