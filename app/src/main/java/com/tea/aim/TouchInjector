package com.tea.aim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.hypot

class TouchInjector : AccessibilityService() {

    companion object {
        var instance: TouchInjector? = null
        var ready: Boolean = false
    }

    private val ui = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ready = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* noop */ }

    override fun onInterrupt() { /* noop */ }

    override fun onDestroy() {
        ready = false
        instance = null
        super.onDestroy()
    }

    fun drag(dx: Float, dy: Float, durationMs: Long = 12L) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(dx, dy)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 40L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun dragSmooth(dx: Float, dy: Float, steps: Int = 6, stepMs: Long = 8L) {
        if (steps <= 1) return drag(dx, dy, stepMs)
        var i = 0
        val sx = dx / steps
        val sy = dy / steps
        val r = object : Runnable {
            override fun run() {
                if (i >= steps) return
                drag(sx, sy, stepMs)
                i++
                ui.postDelayed(this, stepMs)
            }
        }
        ui.post(r)
    }

    fun dragWithMagnitude(dx: Float, dy: Float) {
        val mag = hypot(dx, dy)
        val steps = when {
            mag < 5f  -> 1
            mag < 25f -> 2
            mag < 60f -> 4
            else      -> 6
        }
        dragSmooth(dx, dy, steps)
    }
}
