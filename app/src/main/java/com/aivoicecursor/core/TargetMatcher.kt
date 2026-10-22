package com.aivoicecursor.core

enum class Prefer { NONE, EDITABLE, NON_EDITABLE }

/** Finds the UI element that best matches a spoken/AI-provided target label. Pure logic, unit-tested. */
object TargetMatcher {
    const val MIN_SCORE = 50

    private val noise = setOf(
        "box", "button", "field", "bar", "the", "on",
        "বক্স", "বক্সে", "বাটন", "বাটনে", "বোতাম", "বোতামে", "ফিল্ড"
    )
    private val cleaner = Regex("[^\\p{L}\\p{M}\\p{N}\\s]")
    private val spaces = Regex("\\s+")

    fun tokens(s: String): List<String> =
        cleaner.replace(s.lowercase(), " ").split(spaces).filter { it.isNotEmpty() && it !in noise }

    fun score(label: String, query: String): Int {
        val q = tokens(query)
        val l = tokens(label)
        if (q.isEmpty() || l.isEmpty()) return 0
        if (q == l) return 100
        val matched = q.count { qt -> l.any { lt -> lt == qt || (qt.length >= 3 && lt.contains(qt)) } }
        if (matched == q.size) return 75 - minOf(l.size - q.size, 10).coerceAtLeast(0)
        return matched * 49 / q.size
    }

    fun labelOf(e: UiElement): String =
        e.text?.takeIf { it.isNotBlank() }
            ?: e.hint?.takeIf { it.isNotBlank() }
            ?: e.description?.takeIf { it.isNotBlank() }
            ?: e.resourceId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: e.className

    private fun scoreElement(e: UiElement, query: String, prefer: Prefer): Int {
        val labels = listOfNotNull(
            e.text?.let { it to 0 },
            e.hint?.let { it to 0 },
            e.description?.let { it to 0 },
            e.resourceId?.substringAfterLast('/')?.let { it to 10 }
        )
        val base = labels.maxOfOrNull { (label, penalty) -> score(label, query) - penalty } ?: 0
        if (base < MIN_SCORE) return 0
        var s = base
        if (e.clickable || e.editable) s += 5
        if (prefer == Prefer.EDITABLE && e.editable) s += 3
        if (prefer == Prefer.NON_EDITABLE && !e.editable) s += 3
        return s
    }

    fun best(
        elements: List<UiElement>,
        query: String,
        prefer: Prefer = Prefer.NONE,
        accept: (UiElement) -> Boolean = { it.enabled }
    ): UiElement? {
        var best: UiElement? = null
        var bestScore = MIN_SCORE - 1
        for (e in elements) {
            if (!accept(e)) continue
            val s = scoreElement(e, query, prefer)
            if (s > bestScore) { best = e; bestScore = s }
        }
        return best
    }
}
