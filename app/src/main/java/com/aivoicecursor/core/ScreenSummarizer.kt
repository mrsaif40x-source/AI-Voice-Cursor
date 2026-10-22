package com.aivoicecursor.core

/** Compact screen description sent to the local LLM: only actionable/labelled elements. */
object ScreenSummarizer {

    fun summarize(elements: List<UiElement>, max: Int = 40): String {
        val relevant = elements.filter { e ->
            e.enabled && (e.clickable || e.editable || e.scrollable) &&
                (e.editable || e.scrollable || hasLabel(e))
        }.take(max)
        if (relevant.isEmpty()) return "(no actionable elements)"
        return relevant.mapIndexed { i, e ->
            buildString {
                append("${i + 1}. ${e.className}")
                e.hint?.takeIf { it.isNotBlank() }?.let { append("\n   hint=\"${cut(it)}\"") }
                if (!e.password) e.text?.takeIf { it.isNotBlank() }?.let { append("\n   text=\"${cut(it)}\"") }
                e.description?.takeIf { it.isNotBlank() }?.let { append("\n   desc=\"${cut(it)}\"") }
                e.resourceId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { append("\n   id=\"$it\"") }
                if (e.clickable) append("\n   clickable=true")
                if (e.editable) append("\n   editable=true")
                if (e.scrollable) append("\n   scrollable=true")
            }
        }.joinToString("\n")
    }

    private fun hasLabel(e: UiElement) =
        !e.text.isNullOrBlank() || !e.description.isNullOrBlank() || !e.hint.isNullOrBlank()

    private fun cut(s: String) = if (s.length > 60) s.take(60) + "…" else s
}
