package com.tea.aim

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val label: String
) {
    val width: Float  get() = right - left
    val height: Float get() = bottom - top
    val cx: Float     get() = (left + right) * 0.5f
    val cy: Float     get() = (top + bottom) * 0.5f
    val area: Float   get() = width * height
}
