package com.agentfold.app.control

/**
 * Converts an element's absolute screen bounds into the window-relative percentage
 * coordinates that phone_tap accepts. Pure Kotlin so it is unit-testable off-device.
 * The model is never given authority from this: the owner still approves each tap and
 * ControlSession still re-validates the window before dispatch.
 */
object TapTarget {
    /** Centre of the element as (x%, y%) within [frame], clamped to 1..99; null if unusable. */
    fun centerPercent(frame: WindowFrame, left: Int, top: Int, right: Int, bottom: Int): Pair<Int, Int>? {
        if (frame.width <= 0 || frame.height <= 0 || right <= left || bottom <= top) return null
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        if (cx < frame.left || cx >= frame.right || cy < frame.top || cy >= frame.bottom) return null
        val x = ((cx - frame.left) * 100L / frame.width).toInt().coerceIn(1, 99)
        val y = ((cy - frame.top) * 100L / frame.height).toInt().coerceIn(1, 99)
        return x to y
    }
}
