package com.aivoicecursor.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.aivoicecursor.core.ActionPlan
import com.aivoicecursor.core.ActionType
import com.aivoicecursor.core.AgentAction
import com.aivoicecursor.core.Bounds
import com.aivoicecursor.core.ExecutionResult
import com.aivoicecursor.core.Prefer
import com.aivoicecursor.core.Safety
import com.aivoicecursor.core.SafetyChecker
import com.aivoicecursor.core.ScreenSummarizer
import com.aivoicecursor.core.TargetMatcher
import com.aivoicecursor.core.UiElement
import com.aivoicecursor.vision.ScreenVisionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/**
 * Executes a VALIDATED plan. Order of preference: node action -> (gesture at node bounds) -> vision fallback.
 * Every action goes through SafetyChecker; sensitive ones need an on-screen confirmation.
 */
class ActionExecutor(
    private val service: AccessibilityService,
    private val vision: ScreenVisionEngine,
    private val ui: OverlayController
) {
    private val reader = ScreenReader(service)
    private var typedSinceLastClick = false

    private data class Step(val ok: Boolean, val message: String)

    suspend fun execute(plan: ActionPlan): ExecutionResult {
        val notes = ArrayList<String>()
        for ((i, action) in plan.actions.withIndex()) {
            val step = runStep(action)
            notes += "${i + 1}. ${action.type}: ${step.message}"
            if (!step.ok) return ExecutionResult(false, notes.joinToString("\n"))
            delay(300)
        }
        return ExecutionResult(true, notes.joinToString("\n"))
    }

    private suspend fun runStep(a: AgentAction): Step {
        when (SafetyChecker.assess(a)) {
            Safety.BLOCK -> return Step(false, "blocked by safety policy")
            Safety.CONFIRM -> if (!ui.confirm("Allow: ${a.type} ${a.target ?: a.text ?: ""}?")) {
                return Step(false, "cancelled by user")
            }
            Safety.ALLOW -> Unit
        }
        return when (a.type) {
            ActionType.CLICK -> click(a.target.orEmpty(), long = false)
            ActionType.LONG_CLICK -> click(a.target.orEmpty(), long = true)
            ActionType.TYPE -> type(a)
            ActionType.SCROLL_UP -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            ActionType.SCROLL_DOWN -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            ActionType.SWIPE -> swipe(a.direction.orEmpty())
            ActionType.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, "back")
            ActionType.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, "home")
            ActionType.OPEN_APP -> openApp(a.target.orEmpty())
            ActionType.OPEN_URL -> openUrl(a.text.orEmpty())
            ActionType.FOCUS -> focus(a.target.orEmpty())
            ActionType.WAIT -> { delay(a.ms ?: 0L); Step(true, "waited ${a.ms} ms") }
            ActionType.READ_SCREEN -> Step(true, "\n" + ScreenSummarizer.summarize(reader.capture().elements))
            ActionType.SCREENSHOT -> screenshot()
            ActionType.SEARCH_UI -> searchUi(a.target.orEmpty())
            ActionType.CONFIRM ->
                if (ui.confirm(a.text ?: "Continue?")) Step(true, "confirmed") else Step(false, "cancelled by user")
        }
    }

    // ---------- click / type / scroll ----------

    private suspend fun click(target: String, long: Boolean): Step {
        val snap = reader.capture()
        val prefer = if (typedSinceLastClick) Prefer.NON_EDITABLE else Prefer.EDITABLE
        val match = TargetMatcher.best(snap.elements, target, prefer)
            ?: return visionFallback(target, long)

        val label = TargetMatcher.labelOf(match)
        if (SafetyChecker.isSensitiveLabel(label) && !SafetyChecker.isSensitiveLabel(target) &&
            !ui.confirm("Allow tapping \"$label\"?")
        ) return Step(false, "cancelled by user")

        ui.highlight(match.bounds)
        val node = snap.nodes[match.index]
        if (match.editable && !long) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val done = performOnClickable(node, long) ||
            gesture(tapPath(match.bounds.centerX, match.bounds.centerY), if (long) 800 else 50)
        typedSinceLastClick = false
        return if (done) Step(true, "${if (long) "long-clicked" else "clicked"} \"$label\"")
        else Step(false, "could not click \"$label\"")
    }

    private fun performOnClickable(start: AccessibilityNodeInfo, long: Boolean): Boolean {
        var cur: AccessibilityNodeInfo? = start
        var depth = 0
        while (cur != null && depth < 6) {
            val ok = if (long) cur.isLongClickable else cur.isClickable
            if (ok) {
                val id = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
                if (cur.performAction(id)) return true
            }
            cur = cur.parent
            depth++
        }
        return false
    }

    private suspend fun type(a: AgentAction): Step {
        val snap = reader.capture()
        val field: UiElement = (if (!a.target.isNullOrBlank())
            TargetMatcher.best(snap.elements, a.target) { it.enabled && it.editable }
        else null)
            ?: snap.elements.firstOrNull { it.editable && it.focused }
            ?: snap.elements.firstOrNull { it.editable && it.enabled }
            ?: return Step(false, "no editable field found")
        if (field.password) return Step(false, "refusing to type into a password field")

        ui.highlight(field.bounds)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, a.text)
        }
        val ok = snap.nodes[field.index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) typedSinceLastClick = true
        return if (ok) Step(true, "typed into \"${TargetMatcher.labelOf(field)}\"") else Step(false, "set text failed")
    }

    private fun scroll(actionId: Int): Step {
        val snap = reader.capture()
        val el = snap.elements.filter { it.scrollable && it.enabled }.maxByOrNull { it.bounds.area }
            ?: return Step(false, "nothing scrollable on screen")
        return if (snap.nodes[el.index].performAction(actionId)) Step(true, "scrolled") else Step(false, "scroll failed")
    }

    private suspend fun swipe(direction: String): Step {
        val m = service.resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        val cx = w / 2
        val cy = h / 2
        val (x2, y2) = when (direction.uppercase()) {
            "UP" -> cx to cy - h * 0.35f
            "DOWN" -> cx to cy + h * 0.35f
            "LEFT" -> cx - w * 0.35f to cy
            else -> cx + w * 0.35f to cy
        }
        val path = Path().apply { moveTo(cx, cy); lineTo(x2, y2) }
        return if (gesture(path, 300)) Step(true, "swiped ${direction.lowercase()}") else Step(false, "swipe failed")
    }

    private fun focus(target: String): Step {
        val snap = reader.capture()
        val el = TargetMatcher.best(snap.elements, target) ?: return Step(false, "\"$target\" not found")
        val node = snap.nodes[el.index]
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        return if (ok) Step(true, "focused \"${TargetMatcher.labelOf(el)}\"") else Step(false, "focus failed")
    }

    private suspend fun searchUi(target: String): Step {
        val el = TargetMatcher.best(reader.capture().elements, target)
            ?: return Step(false, "\"$target\" not found on screen")
        ui.highlight(el.bounds)
        return Step(true, "found \"${TargetMatcher.labelOf(el)}\" at ${el.bounds}")
    }

    private fun global(id: Int, name: String): Step =
        if (service.performGlobalAction(id)) Step(true, name) else Step(false, "$name failed")

    // ---------- apps / urls ----------

    private fun openApp(name: String): Step {
        val pm = service.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val best = pm.queryIntentActivities(launcher, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .maxByOrNull { TargetMatcher.score(it.first, name) }
        if (best == null || TargetMatcher.score(best.first, name) < TargetMatcher.MIN_SCORE) {
            return Step(false, "app \"$name\" not found")
        }
        val intent = pm.getLaunchIntentForPackage(best.second)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return Step(false, "cannot launch \"${best.first}\"")
        service.startActivity(intent)
        return Step(true, "opened \"${best.first}\"")
    }

    private fun openUrl(url: String): Step = try {
        service.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Step(true, "opened URL")
    } catch (e: ActivityNotFoundException) {
        Step(false, "no app can open this URL")
    }

    // ---------- screenshot + vision fallback ----------

    private suspend fun screenshot(): Step {
        val png = captureScreenshot() ?: return Step(false, "screenshot unavailable (needs Android 11+)")
        val file = File(service.cacheDir, "last_screenshot.png")
        file.writeBytes(png)
        return Step(true, "saved ${png.size} bytes to app cache")
    }

    private suspend fun captureScreenshot(): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine<ByteArray?> { cont ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        val bytes = bmp?.let {
                            ByteArrayOutputStream().also { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }
                                .toByteArray()
                        }
                        if (cont.isActive) cont.resume(bytes)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }
    }

    private suspend fun visionFallback(target: String, long: Boolean): Step {
        val png = captureScreenshot() ?: return Step(false, "\"$target\" not found on screen")
        val found = vision.findTarget(png, target).getOrElse {
            return Step(false, "\"$target\" not found on screen (${it.message})")
        }
        if (!ui.confirm("Tap detected position for \"$target\"?")) return Step(false, "cancelled by user")
        ui.highlight(Bounds(found.x - 40, found.y - 40, found.x + 40, found.y + 40))
        return if (gesture(tapPath(found.x, found.y), if (long) 800 else 50)) Step(true, "tapped via vision")
        else Step(false, "tap failed")
    }

    // ---------- gestures ----------

    private fun tapPath(x: Int, y: Int) = Path().apply { moveTo(x.toFloat(), y.toFloat()) }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val dispatched = service.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
        if (!dispatched && cont.isActive) cont.resume(false)
    }
}
