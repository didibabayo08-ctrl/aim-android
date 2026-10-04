package com.tea.aim

import kotlin.math.hypot

class TargetSelector {

    var fov: Float = 400f
    var preferHead: Boolean = true

    fun pick(dets: List<Detection>, screenW: Int, screenH: Int): Detection? {
        val cx = screenW * 0.5f
        val cy = screenH * 0.5f
        var best: Detection? = null
        var bestScore = Float.MAX_VALUE

        for (d in dets) {
            val dx = d.cx - cx
            val dy = d.cy - cy
            val dist = hypot(dx, dy)
            if (dist > fov) continue

            val confW = 1f - d.confidence
            val sizeW = 1f - (d.area / (screenW * screenH).toFloat())
            val score = dist + confW * 100f + sizeW * 60f

            if (score < bestScore) { bestScore = score; best = d }
        }
        return best
    }

    fun aimPoint(d: Detection, screenW: Int, screenH: Int): Pair<Float, Float> {
        val headRatio = if (preferHead) 0.18f else 0.5f
        val y = d.top + d.height * headRatio
        return d.cx to y
    }
}
