package com.aivoicecursor.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aivoicecursor.core.Bounds
import com.aivoicecursor.core.UiElement

class ScreenSnapshot(val elements: List<UiElement>, val nodes: List<AccessibilityNodeInfo>)

/** Reads the active window's accessibility tree into plain UiElement objects. */
class ScreenReader(private val service: AccessibilityService) {

    fun capture(): ScreenSnapshot {
        val root = service.rootInActiveWindow ?: return ScreenSnapshot(emptyList(), emptyList())
        val elements = ArrayList<UiElement>()
        val nodes = ArrayList<AccessibilityNodeInfo>()
        walk(root, elements, nodes)
        return ScreenSnapshot(elements, nodes)
    }

    private fun walk(n: AccessibilityNodeInfo, out: MutableList<UiElement>, nodes: MutableList<AccessibilityNodeInfo>) {
        if (out.size >= MAX_NODES) return
        if (n.isVisibleToUser) {
            val r = Rect()
            n.getBoundsInScreen(r)
            val password = n.isPassword
            out += UiElement(
                index = out.size,
                className = n.className?.toString()?.substringAfterLast('.') ?: "View",
                text = if (password) null else n.text?.toString()?.takeIf { it.isNotBlank() },
                hint = n.hintText?.toString()?.takeIf { it.isNotBlank() },
                description = n.contentDescription?.toString()?.takeIf { it.isNotBlank() },
                resourceId = n.viewIdResourceName,
                clickable = n.isClickable,
                editable = n.isEditable,
                scrollable = n.isScrollable,
                enabled = n.isEnabled,
                password = password,
                focused = n.isFocused,
                bounds = Bounds(r.left, r.top, r.right, r.bottom)
            )
            nodes += n
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, out, nodes) }
    }

    private companion object {
        const val MAX_NODES = 500
    }
}
