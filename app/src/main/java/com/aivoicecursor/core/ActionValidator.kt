package com.aivoicecursor.core

import java.net.URI

/** Schema validation. Nothing that fails here reaches the executor. */
object ActionValidator {
    const val MAX_ACTIONS = 10
    const val MAX_TEXT = 500
    const val MAX_TARGET = 200
    const val MAX_WAIT_MS = 5_000L
    private val directions = setOf("UP", "DOWN", "LEFT", "RIGHT")

    fun validate(plan: ActionPlan): Result<ActionPlan> = runCatching {
        require(plan.actions.isNotEmpty()) { "Plan has no actions" }
        require(plan.actions.size <= MAX_ACTIONS) { "Too many actions (max $MAX_ACTIONS)" }
        plan.actions.forEachIndexed { i, a ->
            try {
                check(a)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Action #${i + 1} (${a.type}): ${e.message}")
            }
        }
        plan
    }

    private fun check(a: AgentAction) {
        a.target?.let { require(it.length <= MAX_TARGET) { "target too long" } }
        a.text?.let { require(it.length <= MAX_TEXT) { "text too long" } }
        when (a.type) {
            ActionType.CLICK, ActionType.LONG_CLICK, ActionType.FOCUS,
            ActionType.SEARCH_UI, ActionType.OPEN_APP ->
                require(!a.target.isNullOrBlank()) { "target is required" }
            ActionType.TYPE -> require(!a.text.isNullOrEmpty()) { "text is required" }
            ActionType.OPEN_URL -> require(isSafeUrl(a.text)) { "only http/https URLs are allowed" }
            ActionType.SWIPE -> require(a.direction?.uppercase()?.let { it in directions } == true) {
                "direction must be UP, DOWN, LEFT or RIGHT"
            }
            ActionType.WAIT -> require((a.ms ?: -1L) in 0L..MAX_WAIT_MS) { "ms must be 0..$MAX_WAIT_MS" }
            else -> Unit
        }
    }

    fun isSafeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val u = URI(url.trim())
            (u.scheme == "http" || u.scheme == "https") && !u.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
