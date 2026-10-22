package com.aivoicecursor.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aivoicecursor.core.Bounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Target highlight and on-screen confirmation prompt, drawn as accessibility overlays (no extra permission). */
class OverlayController(private val service: AccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private class HighlightView(context: Context, private val b: Bounds) : View(context) {
        private val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.RED
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
        }
    }

    suspend fun highlight(bounds: Bounds, durationMs: Long = 700) = withContext(Dispatchers.Main) {
        val view = HighlightView(service, bounds)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (runCatching { wm.addView(view, lp) }.isSuccess) {
            try { delay(durationMs) } finally { runCatching { wm.removeView(view) } }
        }
    }

    /** Returns true only if the user taps Confirm. Auto-denies after the timeout. */
    suspend fun confirm(message: String, timeoutMs: Long = 20_000): Boolean = withContext(Dispatchers.Main) {
        var panel: View? = null
        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val v = buildPanel(message) { answer -> if (cont.isActive) cont.resume(answer) }
                    panel = v
                    val lp = WindowManager.LayoutParams(
                        (service.resources.displayMetrics.widthPixels * 0.85).toInt(),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply { gravity = Gravity.CENTER }
                    if (runCatching { wm.addView(v, lp) }.isFailure && cont.isActive) cont.resume(false)
                }
            } ?: false
        } finally {
            panel?.let { runCatching { wm.removeView(it) } }
        }
    }

    private fun buildPanel(message: String, onAnswer: (Boolean) -> Unit): View {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(service).apply {
            text = message
            textSize = 18f
            setTextColor(Color.BLACK)
        })
        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        row.addView(Button(service).apply { text = "Cancel / বাতিল"; setOnClickListener { onAnswer(false) } })
        row.addView(Button(service).apply { text = "Confirm / নিশ্চিত"; setOnClickListener { onAnswer(true) } })
        root.addView(row)
        return root
    }
}
