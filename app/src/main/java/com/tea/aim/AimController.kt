package com.tea.aim

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class AimController(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aim", Context.MODE_PRIVATE)

    var enabled: Boolean = true
    var autoFire: Boolean = false
    var smoothness: Float = 0.35f
    var triggerDelay: Long = 80L
    var fov: Float = 400f
    var sensitivity: Float = 1.35f

    private var lastFireAt: Long = 0L

    fun load() {
        enabled      = prefs.getBoolean("en", true)
        autoFire     = prefs.getBoolean("af", false)
        smoothness   = prefs.getFloat("sm", 0.35f)
        triggerDelay = prefs.getLong("dl", 80L)
        fov          = prefs.getFloat("fov", 400f)
        sensitivity  = prefs.getFloat("sen", 1.35f)
    }

    fun save() {
        prefs.edit()
            .putBoolean("en", enabled)
            .putBoolean("af", autoFire)
            .putFloat("sm", smoothness)
            .putLong("dl", triggerDelay)
            .putFloat("fov", fov)
            .putFloat("sen", sensitivity)
            .apply()
    }

    fun computeDelta(
        screenW: Int, screenH: Int,
        targetX: Float, targetY: Float
    ): Pair<Float, Float> {
        val cx = screenW * 0.5f
        val cy = screenH * 0.5f
        var dx = (targetX - cx)
        var dy = (targetY - cy)

        dx *= sensitivity
        dy *= sensitivity

        val s = smoothness.coerceIn(0.01f, 0.99f)
        dx *= (1f - s)
        dy *= (1f - s)

        val maxStep = min(screenW, screenH) * 0.35f
        val mag = hypot(dx, dy)
        if (mag > maxStep) {
            val k = maxStep / mag
            dx *= k; dy *= k
        }
        return dx to dy
    }

    fun shouldFire(now: Long, targetLocked: Boolean): Boolean {
        if (!autoFire || !targetLocked) return false
        if (now - lastFireAt < triggerDelay) return false
        lastFireAt = now
        return true
    }

    fun isLocked(target: Detection?, screenW: Int, screenH: Int): Boolean {
        target ?: return false
        val dx = abs(target.cx - screenW * 0.5f)
        val dy = abs(target.cy - screenH * 0.5f)
        return dx < 25f && dy < 25f
    }
}
