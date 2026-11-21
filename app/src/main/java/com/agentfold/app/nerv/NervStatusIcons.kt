package com.agentfold.app.nerv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class NervStatus { IDLE, LISTENING, THINKING, ACTING, WAITING, ERROR, OFFLINE;

    val animated: Boolean get() = this == LISTENING || this == THINKING || this == ACTING || this == WAITING
}

class NervStatusIconView(context: Context) : View(context) {

    var status: NervStatus = NervStatus.IDLE
        set(value) {
            field = value
            contentDescription = "Status: ${value.name}"
            invalidate()

            if (value.animated) ensureTicking() else handler.removeCallbacks(tick)
        }

    private var t = 0.0
    private var ticking = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            if (!status.animated) return
            t += 0.05
            invalidate()
            handler.postDelayed(this, 50)
            ticking = true
        }
    }

    private fun ensureTicking() {
        if (ticking) return
        ticking = true
        handler.post(tick)
    }

    init {
        contentDescription = "Status: IDLE"
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private val amber = Color.parseColor("#ff9a1a")
    private val orange = Color.parseColor("#ff5a10")
    private val cyan = Color.parseColor("#6fffe0")
    private val green = Color.parseColor("#3dff7a")
    private val red = Color.parseColor("#ff2a1a")
    private val grey = Color.parseColor("#5a5a5a")

    private val paintCache = mutableMapOf<Triple<Int, Boolean, Float>, Paint>()
    private val hexPathCache = Path()

    private fun paint(color: Int, stroke: Boolean = true, width: Float = 2f): Paint =
        paintCache.getOrPut(Triple(color, stroke, width)) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
                strokeWidth = width * resources.displayMetrics.density
            }
        }

    private fun hexPath(cx: Float, cy: Float, r: Float): Path {
        val path = hexPathCache
        path.reset()
        for (i in 0 until 6) {
            val a = Math.PI / 3 * i + Math.PI / 6
            val px = (cx + r * cos(a)).toFloat()
            val py = (cy + r * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.42f
        val density = resources.displayMetrics.density
        when (status) {
            NervStatus.IDLE -> {
                canvas.drawPath(hexPath(cx, cy, r), paint(amber))
                canvas.drawCircle(cx, cy, 5f * density, paint(green, stroke = false))
            }
            NervStatus.LISTENING -> {

                val pulse = (0.5 + 0.5 * sin(t * 4)).toFloat()
                canvas.drawPath(hexPath(cx, cy, r), paint(amber))
                canvas.drawCircle(cx, cy, r * (0.55f + 0.25f * pulse), paint(cyan))
                for (i in -1..1) {
                    val h = (6 + 8 * (0.5 + 0.5 * sin(t * 6 + i))).toFloat() * density
                    val x = cx + i * 7f * density
                    canvas.drawRect(x - 2f * density, cy - h, x + 2f * density, cy + h, paint(amber, stroke = false))
                }
            }
            NervStatus.THINKING -> {

                canvas.drawPath(hexPath(cx, cy, r), paint(amber))
                for (i in 0 until 3) {
                    val start = (t * 120 + i * 120).toFloat()
                    canvas.drawArc(
                        cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f,
                        start, 70f, false, paint(if (i == 0) cyan else amber, width = 3f)
                    )
                }
                canvas.drawCircle(cx, cy, 3f * density, paint(cyan, stroke = false))
            }
            NervStatus.ACTING -> {

                canvas.drawPath(hexPath(cx, cy, r), paint(orange))
                val arrow = Path().apply {
                    moveTo(cx - r * 0.35f, cy - r * 0.45f)
                    lineTo(cx + r * 0.45f, cy)
                    lineTo(cx - r * 0.35f, cy + r * 0.45f)
                    close()
                }
                canvas.drawPath(arrow, paint(amber, stroke = false))
                val sweep = ((t * 40).toInt() % 100) / 100f
                val barY = cy + r * 0.75f
                canvas.drawRect(cx - r + sweep * 2 * r, barY - 2f * density, cx - r + sweep * 2 * r + 8f * density, barY + 2f * density, paint(green, stroke = false))
            }
            NervStatus.WAITING -> {

                canvas.drawPath(hexPath(cx, cy, r), paint(amber))
                for (i in 0 until 3) {
                    val bounce = (sin(t * 5 + i * 2.1)).toFloat()
                    val x = cx + (i - 1) * 9f * density
                    val y = cy - bounce * 4f * density
                    canvas.drawCircle(x, y, 3.5f * density, paint(cyan, stroke = false))
                }
            }
            NervStatus.ERROR -> {

                canvas.drawPath(hexPath(cx, cy, r), paint(red))
                val d = r * 0.45f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint(red, width = 3f))
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint(red, width = 3f))
            }
            NervStatus.OFFLINE -> {

                canvas.drawPath(hexPath(cx, cy, r), paint(grey))
                canvas.drawLine(cx - r * 0.8f, cy + r * 0.8f, cx + r * 0.8f, cy - r * 0.8f, paint(grey, width = 3f))
            }
        }
    }
}
