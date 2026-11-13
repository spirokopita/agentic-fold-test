package com.agentfold.app.ui.neural

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.*

/** Hardware-accelerated vector scene in a unit circular viewport. No prerecorded frames. */
class NeuralDialView(context: Context, val controller: NeuralController) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val bounds = RectF()
    private val visible = Rect()
    private val condensedTypeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private var balanceShader: LinearGradient? = null
    private var shaderBalance = Float.NaN
    private val weights = FloatArray(7).apply { this[0] = 1f }
    private val startWeights = weights.copyOf()
    private var snapshot = controller.snapshot
    private var transitionStart = 0L
    private var clock = 0f
    private var previousFrame = 0L
    private var attached = false
    private var active = false
    private var scheduled = false
    private var failed = false
    private var lowPower = false
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val listener: (NeuralSnapshot) -> Unit = { next ->
        post {
            if (attached) {
                if (snapshot.state != next.state) {
                    weights.copyInto(startWeights); transitionStart = SystemClock.uptimeMillis()
                }
                snapshot = next
                contentDescription = "${if (next.preview) "development simulation" else "activity visual"}: ${next.state.label}. " +
                    "Firewall values: ${next.localFirewall.label}, ${next.peerFirewall.label}. No security diagnosis."
                invalidate(); schedule()
            }
        }
    }
    private val frame = Runnable { scheduled = false; invalidate() }
    private val visibilityListener = ViewTreeObserver.OnScrollChangedListener { schedule() }
    private val powerChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { lowPower = power.isPowerSaveMode; invalidate(); schedule() }
    }
    private val motionChanged = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { invalidate(); schedule() }
    }
    init {
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumHeight = (280 * resources.displayMetrics.density).toInt()
    }
    fun setHostActive(value: Boolean) { active = value; previousFrame = 0; schedule() }
    private fun canAnimate() = attached && active && !failed && isShown && windowVisibility == VISIBLE &&
        getGlobalVisibleRect(visible) && ValueAnimator.areAnimatorsEnabled()
    private fun schedule() {
        if (!canAnimate()) { removeCallbacks(frame); scheduled = false; return }
        if (!scheduled) { scheduled = true; postOnAnimationDelayed(frame, if (lowPower) 33L else 0L) }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true; lowPower = power.isPowerSaveMode
        controller.subscribe(listener)
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(powerChanged, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(powerChanged, filter)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionChanged)
        viewTreeObserver.addOnScrollChangedListener(visibilityListener); schedule()
    }
    override fun onDetachedFromWindow() {
        attached = false; removeCallbacks(frame); scheduled = false; controller.unsubscribe(listener)
        context.unregisterReceiver(powerChanged); context.contentResolver.unregisterContentObserver(motionChanged)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(visibilityListener)
        previousFrame = 0; super.onDetachedFromWindow()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); if (attached) schedule() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (attached) schedule() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (canAnimate()) {
            if (previousFrame != 0L) clock += minOf((now - previousFrame) / 1000f, .05f)
            previousFrame = now
        } else previousFrame = 0
        val fraction = if (ValueAnimator.areAnimatorsEnabled()) ((now - transitionStart) / 1000f).coerceIn(0f, 1f) else 1f
        val eased = fraction * fraction * (3 - 2 * fraction)
        weights.indices.forEach { weights[it] = startWeights[it] * (1 - eased) + (if (snapshot.state.ordinal == it) eased else 0f) }
        val save = canvas.save()
        try {
            val side = min(width.toFloat(), height.toFloat())
            canvas.translate(width / 2f, height / 2f); canvas.scale(side / 1000f, side / 1000f)
            path.reset(); path.addCircle(0f, 0f, 498f, Path.Direction.CW); canvas.clipPath(path)
            if (failed) label(canvas, "visual unavailable · use controls", 0f, 0f, Color.WHITE, 24f)
            else scene(canvas)
        } catch (_: RuntimeException) {
            failed = true // Functional controls are outside this View and remain usable.
            contentDescription = "Visual renderer unavailable. Use activity fallback and functional controls."
            postInvalidate()
        } finally { canvas.restoreToCount(save) }
        schedule()
    }
    private fun scene(c: Canvas) {
        val pair = weights[3] + weights[4] + weights[5] + weights[6]
        val explore = weights[2]
        val localX = -225f * pair
        val localRadius = 326f * (1 - pair - explore) + 160f * pair + 28f * explore
        val localY = -35f * pair
        orb(c, localX, localY, localRadius, BLUE, weights[1], snapshot.localInfection * weights[5], 1f, weights[1] + pair)
        if (pair > .001f) {
            orb(c, 225f, -35f, 160f, RED, .25f, snapshot.peerInfection * weights[6], pair)
            tendrils(c, pair)
            candle(c, -300f, 265f, BLUE, snapshot.localFirewall, pair)
            candle(c, 300f, 265f, RED, snapshot.peerFirewall, pair)
        }
        val field = weights[0]
        if (field > .001f) {
            circle(c, 0f, 0f, 354f, BLUE, 1.3f, field)
            circle(c, 0f, 0f, 386f, BLUE, 1.3f, field)
            bounds.set(-370f, -370f, 370f, 370f)
            for (i in 0..31) {
                val speed = 7f + (i % 7) * .51f
                val angle = i * 11.25f + clock * speed * (.45f + snapshot.activity)
                ink.style = Paint.Style.STROKE; ink.strokeWidth = 13f; ink.color = Color.WHITE; ink.alpha = (field * 225).toInt()
                c.drawArc(bounds, angle, 2.5f + i % 5, false, ink)
            }
        }
        if (explore > .001f) applications(c, explore)
        if (weights[1] > .001f) for (i in 0..5) {
            val a = clock * (.13f + i * .01f) + i * PI.toFloat() / 3
            val x = cos(a) * (385f + sin(clock + i) * 14)
            val y = sin(a) * 385f
            circle(c, x, y, 8f + i * 2, Color.WHITE, 1.1f, weights[1])
        }
        label(c, if (snapshot.preview) "simulation / ${snapshot.state.label}" else "${snapshot.state.label} / activity visual", 0f, 445f, Color.WHITE, 19f)
    }
    private fun orb(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, twist: Float, infection: Float, alpha: Float, detail: Float = 1f) {
        val shell = if (infection > 0f) blend(color, 0xff52606a.toInt(), infection * .65f) else color
        circle(c, cx, cy, r, shell, 1.4f, alpha * if (r > 300f) detail else 1f)
        if (detail < .001f) return
        // Project independently twisting spherical meridians with depth shading.
        val lanes = if (lowPower) 12 else 24
        for (lane in 0 until lanes) {
            path.reset()
            for (step in 0..64) {
                val latitude = -PI.toFloat() / 2 + step / 64f * PI.toFloat()
                val phase = lane * 2f * PI.toFloat() / lanes + clock * .19f
                val longitude = phase + twist * sin(latitude * 3 + clock * .7f) * 1.45f
                val depth = sin(longitude) * cos(latitude)
                val x = cx + cos(longitude) * cos(latitude) * r * (.95f + twist * .05f * sin(clock + latitude * 4))
                val y = cy + sin(latitude) * r * (.98f - twist * .12f * cos(longitude + clock))
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (step % 8 == 0 && depth > 0f) dot(c, x, y, 1.7f, color, alpha * .75f)
            }
            val inner = lane.toFloat() / lanes < infection
            glow(c, path, if (inner) (if (color == BLUE) RED else BLUE) else shell, .8f, alpha * detail * (.28f + .55f * (lane % 4) / 3f))
        }
        // Branched internal neural trees, clipped inside the identity shell.
        val save = c.save()
        path.reset(); path.addCircle(cx, cy, r * .97f, Path.Direction.CW); c.clipPath(path)
        for (i in 0..17) {
            val a = i * 2.39996f + sin(clock * .35f + i) * .08f
            val x = cx + cos(a) * r * .89f; val y = cy + sin(a) * r * .89f
            val neuralColor = if (i / 18f < infection) (if (color == BLUE) RED else BLUE) else shell
            path.reset(); path.moveTo(cx, cy)
            path.cubicTo(cx + sin(i + clock * .3f) * r * .5f, cy - r * .3f, x - cos(a + .7f) * r * .4f, y - sin(a) * r * .3f, x, y)
            glow(c, path, neuralColor, 1f, alpha * detail * .65f)
            val bx = cx + (x - cx) * .58f; val by = cy + (y - cy) * .58f
            path.reset(); path.moveTo(bx, by); path.quadTo(x, by, x + sin(a) * r * .18f, y - cos(a) * r * .18f)
            glow(c, path, neuralColor, .7f, alpha * detail * .45f)
        }
        c.restoreToCount(save)
    }
    private fun tendrils(c: Canvas, alpha: Float) {
        val conflict = weights[4] / alpha.coerceAtLeast(.001f)
        if (balanceShader == null || shaderBalance != snapshot.balance) {
            shaderBalance = snapshot.balance
            balanceShader = LinearGradient(-160f, 0f, 160f, 0f,
                intArrayOf(BLUE, BLUE, 0xffb54bff.toInt(), RED, RED),
                floatArrayOf(0f, (shaderBalance - .12f).coerceIn(.01f, .85f), shaderBalance.coerceIn(.02f, .98f),
                    (shaderBalance + .12f).coerceIn(.15f, .99f), 1f), Shader.TileMode.CLAMP)
        }
        for (strand in 0..11) {
            path.reset()
            for (step in 0..80) {
                val u = step / 80f
                val x = -160f + u * 320f
                val envelope = sin(u * PI.toFloat())
                val y = -35f + sin(u * PI.toFloat() * (4 + conflict * 3) - clock * 1.8f + strand * .53f) * envelope * (20f + strand * 2.8f)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            ink.shader = balanceShader
            glow(c, path, Color.WHITE, 1.2f, alpha)
            ink.shader = null
            val u = ((clock * .18f + strand * .083f) % 1f)
            val y = -35f + sin(u * PI.toFloat() * (4 + conflict * 3) - clock * 1.8f + strand * .53f) * sin(u * PI.toFloat()) * (20f + strand * 2.8f)
            dot(c, -160f + u * 320f, y, 2f, Color.WHITE, alpha)
        }
    }
    private fun applications(c: Canvas, alpha: Float) {
        val apps = snapshot.applications
        if (apps.isEmpty()) { label(c, "no registered app endpoints", 0f, 130f, Color.WHITE, 22f); return }
        apps.forEachIndexed { i, app ->
            val a = i * PI.toFloat() * 2 / apps.size - PI.toFloat() / 2
            val x = cos(a) * 350; val y = sin(a) * 350
            if (app.available && app.connected) {
                path.reset(); path.moveTo(0f, 0f)
                path.cubicTo(x * .2f + sin(clock + i) * 60, y * .5f, x * .75f, y * .45f, x, y)
                glow(c, path, BLUE, 1.2f, alpha)
                path.moveTo(x * .6f, y * .6f); path.quadTo(x * .7f + 30, y * .7f - 35, x, y)
                glow(c, path, BLUE, .7f, alpha * .5f)
            }
            circle(c, x, y, 18f, if (app.available) BLUE else Color.GRAY, 1f, alpha)
            label(c, app.label.take(20), x * .9f, y * .9f + 40, Color.WHITE, 20f)
        }
    }
    private fun candle(c: Canvas, x: Float, y: Float, color: Int, metric: FirewallMetric, alpha: Float) {
        ink.shader = null; ink.style = Paint.Style.STROKE; ink.strokeWidth = 1.4f; ink.color = color; ink.alpha = (alpha * 255).toInt()
        bounds.set(x - 12, y - 12, x + 12, y + 45); c.drawRoundRect(bounds, 4f, 4f, ink)
        val sway = sin(clock * 4 + x) * 5
        path.reset(); path.moveTo(x, y - 61); path.cubicTo(x + 24 + sway, y - 35, x + 13, y - 17, x, y - 15)
        path.cubicTo(x - 18, y - 20, x - 10 + sway, y - 43, x, y - 61)
        glow(c, path, color, 3f, alpha)
        dot(c, x, y - 31, 3.5f, Color.WHITE, alpha)
        label(c, metric.percent?.let { "${it.toInt()}%" } ?: "—", x, y + 78, Color.WHITE, 28f)
        label(c, if (metric.simulated) "simulation" else metric.label.take(20), x, y + 106, color, 18f)
    }
    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, width: Float, alpha: Float) {
        path.reset(); path.addCircle(x, y, r, Path.Direction.CW); glow(c, path, color, width, alpha)
    }
    private fun glow(c: Canvas, p: Path, color: Int, width: Float, alpha: Float) {
        ink.style = Paint.Style.STROKE; ink.color = color
        if (!lowPower) { ink.strokeWidth = width + 8; ink.alpha = (alpha * 14).toInt().coerceIn(0, 255); c.drawPath(p, ink)
            ink.strokeWidth = width + 3; ink.alpha = (alpha * 40).toInt().coerceIn(0, 255); c.drawPath(p, ink) }
        ink.strokeWidth = width; ink.alpha = (alpha * 255).toInt().coerceIn(0, 255); c.drawPath(p, ink)
    }
    private fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        ink.shader = null; ink.style = Paint.Style.FILL; ink.color = color; ink.alpha = (alpha * 255).toInt().coerceIn(0, 255); c.drawCircle(x, y, r, ink)
    }
    private fun label(c: Canvas, text: String, x: Float, y: Float, color: Int, size: Float) {
        ink.shader = null; ink.style = Paint.Style.FILL; ink.color = color; ink.alpha = 255
        ink.typeface = condensedTypeface; ink.textSize = size; ink.textAlign = Paint.Align.CENTER
        c.drawText(text, x, y, ink)
    }
    companion object {
        val BLUE = 0xff00bbff.toInt(); val RED = 0xffff304e.toInt()
        private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
            (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
            (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
            (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt())
    }
}
