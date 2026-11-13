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
    var onRendererFailure: (() -> Unit)? = null
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val bounds = RectF()
    private val visible = Rect()
    private val condensedTypeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private var balanceShader: LinearGradient? = null
    private var shaderBalance = Float.NaN
    private val sphereLatitude = FloatArray(65) { -PI.toFloat() / 2 + it / 64f * PI.toFloat() }
    private val sphereSin = FloatArray(65) { sin(sphereLatitude[it]) }
    private val sphereCos = FloatArray(65) { cos(sphereLatitude[it]) }
    private val sphereSin3 = FloatArray(65) { sin(sphereLatitude[it] * 3f) }
    private val sphereCos3 = FloatArray(65) { cos(sphereLatitude[it] * 3f) }
    private val sphereSin4 = FloatArray(65) { sin(sphereLatitude[it] * 4f) }
    private val sphereCos4 = FloatArray(65) { cos(sphereLatitude[it] * 4f) }
    private val tendrilU = FloatArray(81) { it / 80f }
    private val tendrilEnvelope = FloatArray(81) { sin(tendrilU[it] * PI.toFloat()) }
    private val packetPhase = FloatArray(32) { i -> i * 11.25f + sin(i * 2.173f) * 5.5f + (i % 3) * 1.7f }
    private val packetSpeed = FloatArray(32) { i ->
        val magnitude = 3.4f + (i * 37 % 17) * .47f
        if (i % 6 == 0) -magnitude else magnitude
    }
    private val packetSweep = FloatArray(32) { i -> 1.7f + (i * 29 % 13) * .42f }
    private val packetRadius = FloatArray(32) { i -> 363f + (i * 7 % 4) * 4.2f }
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
    private var failureReported = false
    private var lowPower = false
    private var renderedActivity = 0f
    private var renderedBalance = .5f
    private var renderedLocalInfection = 0f
    private var renderedPeerInfection = 0f
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
            val dt = if (previousFrame == 0L) 0f else minOf((now - previousFrame) / 1000f, .05f)
            clock += dt
            renderedActivity = if (snapshot.activity == 0f) 0f else follow(renderedActivity, snapshot.activity, dt, 7f)
            renderedBalance = follow(renderedBalance, snapshot.balance, dt, 4f)
            renderedLocalInfection = follow(renderedLocalInfection, snapshot.localInfection, dt, 3.5f)
            renderedPeerInfection = follow(renderedPeerInfection, snapshot.peerInfection, dt, 3.5f)
            previousFrame = now
        } else previousFrame = 0
        val fraction = if (ValueAnimator.areAnimatorsEnabled()) ((now - transitionStart) / 1000f).coerceIn(0f, 1f) else 1f
        val eased = fraction * fraction * (3 - 2 * fraction)
        for (i in weights.indices) {
            weights[i] = startWeights[i] * (1 - eased) + (if (snapshot.state.ordinal == i) eased else 0f)
        }
        val save = canvas.save()
        try {
            val side = min(width.toFloat(), height.toFloat())
            canvas.translate(width / 2f, height / 2f); canvas.scale(side / 1000f, side / 1000f)
            path.reset(); path.addCircle(0f, 0f, 498f, Path.Direction.CW); canvas.clipPath(path)
            if (failed) label(canvas, "visual unavailable · use controls", 0f, 0f, Color.WHITE, 24f)
            else scene(canvas)
        } catch (_: RuntimeException) {
            failed = true
            contentDescription = "Visual renderer unavailable. Activity fallback is being restored."
            if (!failureReported) {
                failureReported = true
                post { onRendererFailure?.invoke() }
            }
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
        orb(c, localX, localY, localRadius, BLUE, weights[1], renderedLocalInfection * weights[5], 1f, weights[1] + pair)
        if (pair > .001f) {
            orb(c, 225f, -35f, 160f, RED, .25f, renderedPeerInfection * weights[6], pair)
            tendrils(c, pair)
            candle(c, -300f, 265f, BLUE, snapshot.localFirewall, pair)
            candle(c, 300f, 265f, RED, snapshot.peerFirewall, pair)
        }
        val field = weights[0]
        if (field > .001f) {
            circle(c, 0f, 0f, 354f, BLUE, 1.3f, field)
            circle(c, 0f, 0f, 386f, BLUE, 1.3f, field)
            for (i in packetPhase.indices) {
                val radius = packetRadius[i]
                bounds.set(-radius, -radius, radius, radius)
                val angle = packetPhase[i] + clock * packetSpeed[i] * (.42f + renderedActivity)
                ink.style = Paint.Style.STROKE; ink.strokeWidth = 11.5f; ink.color = Color.WHITE
                ink.alpha = (field * (190 + (i * 31 % 66))).toInt().coerceIn(0, 255)
                c.drawArc(bounds, angle, packetSweep[i], false, ink)
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
        // Latitude terms are immutable; addition identities avoid four extra trig calls per point.
        val lanes = if (lowPower) 12 else 24
        val clockSin = sin(clock)
        val clockCos = cos(clock)
        val twistClock = clock * .7f
        val twistClockSin = sin(twistClock)
        val twistClockCos = cos(twistClock)
        for (lane in 0 until lanes) {
            path.reset()
            val phase = lane * 2f * PI.toFloat() / lanes + clock * .19f
            for (step in 0..64) {
                val latitudeSin = sphereSin[step]
                val latitudeCos = sphereCos[step]
                val twistWave = sphereSin3[step] * twistClockCos + sphereCos3[step] * twistClockSin
                val longitude = phase + twist * twistWave * 1.45f
                val longitudeSin = sin(longitude)
                val longitudeCos = cos(longitude)
                val depth = longitudeSin * latitudeCos
                val radiusWave = sphereSin4[step] * clockCos + sphereCos4[step] * clockSin
                val x = cx + longitudeCos * latitudeCos * r * (.95f + twist * .05f * radiusWave)
                val shiftedLongitudeCos = longitudeCos * clockCos - longitudeSin * clockSin
                val y = cy + latitudeSin * r * (.98f - twist * .12f * shiftedLongitudeCos)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (step % 8 == 0 && depth > 0f) dot(c, x, y, 1.7f, color, alpha * .75f)
            }
            val invasion = spread(infection, lane.toFloat() / (lanes - 1).coerceAtLeast(1))
            val neuralColor = blend(shell, if (color == BLUE) RED else BLUE, invasion)
            glow(c, path, neuralColor, .8f, alpha * detail * (.28f + .55f * (lane % 4) / 3f))
        }
        // Branched internal neural trees, clipped inside the identity shell.
        val save = c.save()
        path.reset(); path.addCircle(cx, cy, r * .97f, Path.Direction.CW); c.clipPath(path)
        for (i in 0..17) {
            val a = i * 2.39996f + sin(clock * .35f + i) * .08f
            val x = cx + cos(a) * r * .89f; val y = cy + sin(a) * r * .89f
            val neuralColor = blend(shell, if (color == BLUE) RED else BLUE, spread(infection, i / 17f))
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
        val balanceKey = (renderedBalance * 100f).roundToInt() / 100f
        if (balanceShader == null || shaderBalance != balanceKey) {
            shaderBalance = balanceKey
            balanceShader = LinearGradient(-160f, 0f, 160f, 0f,
                intArrayOf(BLUE, BLUE, 0xffb54bff.toInt(), RED, RED),
                floatArrayOf(0f, (shaderBalance - .12f).coerceIn(.01f, .85f), shaderBalance.coerceIn(.02f, .98f),
                    (shaderBalance + .12f).coerceIn(.15f, .99f), 1f), Shader.TileMode.CLAMP)
        }
        val frequency = PI.toFloat() * (4f + conflict * 3f)
        val phaseStep = frequency / 80f
        val phaseStepSin = sin(phaseStep)
        val phaseStepCos = cos(phaseStep)
        val basePhase = -clock * 1.8f
        for (strand in 0..11) {
            path.reset()
            var phaseSin = sin(basePhase + strand * .53f)
            var phaseCos = cos(basePhase + strand * .53f)
            val amplitude = 20f + strand * 2.8f
            for (step in 0..80) {
                val u = tendrilU[step]
                val x = -160f + u * 320f
                val y = -35f + phaseSin * tendrilEnvelope[step] * amplitude
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                val nextSin = phaseSin * phaseStepCos + phaseCos * phaseStepSin
                phaseCos = phaseCos * phaseStepCos - phaseSin * phaseStepSin
                phaseSin = nextSin
            }
            ink.shader = balanceShader
            glow(c, path, Color.WHITE, 1.2f, alpha)
            ink.shader = null
            val u = ((clock * .18f + strand * .083f) % 1f)
            val y = -35f + sin(u * frequency + basePhase + strand * .53f) * sin(u * PI.toFloat()) * amplitude
            dot(c, -160f + u * 320f, y, 2f, Color.WHITE, alpha)
        }
        // Moving local pressure makes conflict a contest, not merely a static colour ramp.
        val pressureAlpha = alpha * (.32f + conflict * .68f)
        for (i in 0 until 18) {
            val direction = if (i % 2 == 0) 1f else -1f
            val u = ((clock * (.12f + (i % 4) * .018f) * direction + i / 18f) % 1f + 1f) % 1f
            val wave = sin(u * frequency + basePhase + i * .53f) * sin(u * PI.toFloat())
            val y = -35f + wave * (22f + (i % 6) * 4f)
            val contest = ((u - renderedBalance) * 3.2f + sin(clock * 1.4f + i) * .22f * conflict).coerceIn(-1f, 1f)
            val packetColor = blend(BLUE, RED, ((contest + 1f) * .5f).coerceIn(0f, 1f))
            dot(c, -160f + u * 320f, y, 2.4f + conflict * 1.6f, packetColor, pressureAlpha)
        }
        dot(c, -160f, -35f, 4f + conflict * 2f, BLUE, pressureAlpha)
        dot(c, 160f, -35f, 4f + conflict * 2f, RED, pressureAlpha)
    }
    private fun applications(c: Canvas, alpha: Float) {
        val apps = snapshot.applications
        if (apps.isEmpty()) { label(c, "no registered app endpoints", 0f, 130f, Color.WHITE, 22f); return }
        apps.forEachIndexed { i, app ->
            val a = i * PI.toFloat() * 2 / apps.size - PI.toFloat() / 2
            val x = cos(a) * 350f; val y = sin(a) * 350f
            if (app.connected) {
                val growth = smooth(((clock * .23f + i * .37f) % 1f * 1.55f).coerceIn(0f, 1f))
                path.reset(); path.moveTo(0f, 0f)
                val steps = (growth * 24).roundToInt().coerceAtLeast(1)
                for (step in 1..steps) {
                    val u = step / 24f
                    val one = 1f - u
                    val c1x = x * .2f + sin(clock + i) * 55f
                    val c1y = y * .48f
                    val c2x = x * .73f
                    val c2y = y * .44f + cos(clock * .7f + i) * 24f
                    val px = one * one * one * 0f + 3f * one * one * u * c1x + 3f * one * u * u * c2x + u * u * u * x
                    val py = 3f * one * one * u * c1y + 3f * one * u * u * c2y + u * u * u * y
                    path.lineTo(px, py)
                }
                glow(c, path, BLUE, 1.2f, alpha)
                if (growth > .58f) {
                    val bx = x * .62f; val by = y * .62f
                    path.reset(); path.moveTo(bx, by)
                    path.quadTo(x * .75f + sin(i.toFloat()) * 34f, y * .72f - 30f, x, y)
                    glow(c, path, BLUE, .7f, alpha * smooth((growth - .58f) / .42f) * .6f)
                }
                dot(c, x * growth, y * growth, 2.8f, Color.WHITE, alpha)
            }
            val nodeColor = when {
                app.connected -> BLUE
                app.launchable -> 0xff4f8da8.toInt()
                app.available -> 0xff31505e.toInt()
                else -> Color.DKGRAY
            }
            circle(c, x, y, if (app.connected) 20f else 15f, nodeColor, if (app.connected) 1.3f else .8f, alpha)
            val status = when {
                app.connected -> "integrated"
                app.launchable -> "launchable"
                app.available -> "installed"
                else -> "unavailable"
            }
            label(c, app.label.take(18), x * .87f, y * .87f + 30f, Color.WHITE, 17f)
            label(c, status, x * .87f, y * .87f + 50f, nodeColor, 14f)
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
        private fun follow(current: Float, target: Float, dt: Float, response: Float): Float =
            current + (target - current) * (1f - exp(-response * dt))
        private fun smooth(value: Float): Float {
            val v = value.coerceIn(0f, 1f)
            return v * v * (3f - 2f * v)
        }
        private fun spread(amount: Float, position: Float): Float = smooth((amount * 1.18f - position) * 4f + .5f)
        private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
            (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
            (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
            (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt())
    }
}
