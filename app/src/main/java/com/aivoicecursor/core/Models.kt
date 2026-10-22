package com.aivoicecursor.core

import kotlinx.coroutines.flow.MutableStateFlow

enum class ActionType {
    CLICK, LONG_CLICK, TYPE, SCROLL_UP, SCROLL_DOWN,
    SWIPE, BACK, HOME, OPEN_APP, OPEN_URL, FOCUS,
    WAIT, READ_SCREEN, SCREENSHOT, SEARCH_UI, CONFIRM
}

/**
 * Field usage:
 * CLICK/LONG_CLICK/FOCUS/SEARCH_UI/OPEN_APP -> target
 * TYPE -> text (optional target = field label)
 * OPEN_URL -> text (http/https only)
 * SWIPE -> direction (UP|DOWN|LEFT|RIGHT)
 * WAIT -> ms
 * CONFIRM -> text (question shown to the user)
 */
data class AgentAction(
    val type: ActionType,
    val target: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val ms: Long? = null
)

data class ActionPlan(val actions: List<AgentAction>)

data class ExecutionResult(val success: Boolean, val message: String)

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Long get() = maxOf(0, right - left).toLong() * maxOf(0, bottom - top).toLong()
}

data class UiElement(
    val index: Int,
    val className: String,
    val text: String? = null,
    val hint: String? = null,
    val description: String? = null,
    val resourceId: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val password: Boolean = false,
    val focused: Boolean = false,
    val bounds: Bounds = Bounds(0, 0, 0, 0)
)

/** Process-wide observable state shown on the main screen. */
object AgentRuntime {
    val listening = MutableStateFlow(false)
    val lastHeard = MutableStateFlow("")
    val status = MutableStateFlow("Idle")
}
