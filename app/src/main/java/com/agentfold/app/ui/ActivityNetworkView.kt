package com.agentfold.app.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import com.agentfold.app.agent.ActivityStage
import com.agentfold.app.agent.TaskActivity
import com.agentfold.app.agent.ToolOutcome
import kotlin.math.sin

/** Native execution visualization. Packets mean activity, never progress or delegated agents. */
class ActivityNetworkView(context: Context) : View(context) {
    enum class Presentation { SIGNAL, NETWORK }
    var presentation = Presentation.SIGNAL
        private set
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val routes = Array(4) { Path() }
    private val streams = Array(56) { Path() }
    private val streamColors = intArrayOf(0xffecc37b.toInt(), 0xffb2f079.toInt(), 0xffb9a9e8.toInt(), 0xff84d4c5.toInt())
    private val visibleRect = Rect()
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var activity = TaskActivity()
    private var control = false
    private var model = "unconfigured"
    private val nodeLabels = arrayOf("MODEL", "TOOLS", "FILES", "CONTROL")
    private var hostActive = false
    private var packet = 0f
    private var animator: ValueAnimator? = null
    private var lastFrame = 0L
    private var attached = false
    val animationsRunning get() = animator?.isRunning == true
    private val scrollChanged = ViewTreeObserver.OnScrollChangedListener { refreshAnimation() }
    private val layoutChanged = ViewTreeObserver.OnGlobalLayoutListener { refreshAnimation() }
    private val powerChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshAnimation() }
    }
    private val motionChanged = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshAnimation() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        background = AgentTheme.panel(context)
        minimumHeight = dp(270).toInt()
        updateDescription()
    }

    fun show(snapshot: TaskActivity, configuredModel: String, controlAuthorized: Boolean) {
        if (activity == snapshot && model == configuredModel && control == controlAuthorized) return
        activity = snapshot; model = configuredModel; control = controlAuthorized
        updateDescription(); refreshAnimation(); invalidate()
    }
    fun setHostActive(active: Boolean) { hostActive = active; refreshAnimation() }
    fun setPresentation(value: Presentation) {
        if (presentation == value) return
        presentation = value; updateDescription(); invalidate()
    }

    private fun updateDescription() {
        contentDescription = "Live agent activity. ${presentation.name.lowercase()} view. Task ${activity.stage.label}: ${activity.objective}. " +
            "Model $model, call ${activity.modelCall} of at most eight. " +
            "Tool ${activity.tool ?: "none"}, result ${activity.toolOutcome.name.lowercase()}. " +
            "File transmission ${if (activity.fileApproved) "approved for this task" else "not approved"}. " +
            "Report ${if (activity.verifiedReport) "verified" else "none from this task"}. " +
            "Manual device control ${if (control) "authorized" else "off"}; model-driven control unavailable. " +
            "Particles indicate an active phase, not counts, throughput or task percentage."
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(powerChanged, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(powerChanged, filter)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionChanged)
        viewTreeObserver.addOnScrollChangedListener(scrollChanged)
        viewTreeObserver.addOnGlobalLayoutListener(layoutChanged)
        refreshAnimation()
    }
    override fun onDetachedFromWindow() {
        attached = false; stopAnimation()
        context.unregisterReceiver(powerChanged)
        context.contentResolver.unregisterContentObserver(motionChanged)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollChanged)
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(layoutChanged)
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); refreshAnimation() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); refreshAnimation() }
    private fun refreshAnimation() {
        // Visibility callbacks can run before subclass initialization.
        if (!attached) return
        val animate = hostActive && activity.stage.busy && activeNode() >= 0 && windowVisibility == VISIBLE && isShown &&
            getGlobalVisibleRect(visibleRect) && ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode
        if (!animate) { stopAnimation(); return }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                packet = it.animatedValue as Float
                val now = SystemClock.elapsedRealtime()
                if (now - lastFrame >= 33) { lastFrame = now; invalidate() }
            }
            start()
        }
    }
    private fun stopAnimation() {
        if (animator == null && packet == 0f) return
        animator?.cancel(); animator = null; packet = 0f; invalidate()
    }
    private fun dp(value: Int) = value * resources.displayMetrics.density
    private fun sp(value: Int) = value * resources.displayMetrics.scaledDensity

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w * .5f; val cy = h * .47f
        for (i in 0..3) {
            val x = w * if (i % 2 == 0) .19f else .81f
            val y = h * if (i < 2) .28f else .74f
            routes[i].apply { reset(); moveTo(cx, cy); lineTo((cx + x) * .5f, cy); lineTo((cx + x) * .5f, y); lineTo(x, y) }
        }
        for (index in streams.indices) {
            val lane = index / 14; val strand = index % 14
            streams[index].apply {
                reset()
                for (sample in 0..48) {
                    val t = sample / 48f
                    val x = streamX(t, lane, strand / 13f)
                    val y = streamY(t)
                    if (sample == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        }
        post { refreshAnimation() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (presentation == Presentation.SIGNAL) { drawSignal(canvas); return }
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w * .5f; val cy = h * .47f
        val small = minOf(dp(25), w * .075f)
        val central = minOf(dp(39), w * .12f)
        ink.style = Paint.Style.STROKE; ink.strokeWidth = dp(1); ink.color = 0xff1c2a1e.toInt()
        var gx = dp(18)
        while (gx < w) { canvas.drawLine(gx, dp(16), gx, h - dp(16), ink); gx += dp(36) }
        var gy = dp(18)
        while (gy < h) { canvas.drawLine(dp(16), gy, w - dp(16), gy, ink); gy += dp(36) }
        val activeNode = when (activity.stage) {
            ActivityStage.MODEL -> 0
            ActivityStage.TOOL -> 1
            ActivityStage.VERIFYING -> 2
            else -> -1
        }
        for (i in 0..3) {
            // Node labels leave the shared paint in FILL mode.
            ink.style = Paint.Style.STROKE
            ink.color = if (i == activeNode) AgentTheme.accent else AgentTheme.stroke
            ink.strokeWidth = dp(if (i == activeNode) 2 else 1)
            ink.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(routes[i], ink)
            val x = w * if (i % 2 == 0) .19f else .81f
            val y = h * if (i < 2) .28f else .74f
            if (i == activeNode && animationsRunning) {
                // Travel along the same orthogonal route, independent of task percentage.
                val mid = (cx + x) * .5f
                val t = packet * 3
                val px = when { t < 1 -> cx + (mid - cx) * t; t < 2 -> mid; else -> mid + (x - mid) * (t - 2) }
                val py = when { t < 1 -> cy; t < 2 -> cy + (y - cy) * (t - 1); else -> y }
                ink.style = Paint.Style.FILL; ink.color = AgentTheme.signal
                canvas.drawCircle(px, py, dp(3), ink); ink.style = Paint.Style.STROKE
            }
            val nodeColor = when {
                i == 1 && activity.toolOutcome == ToolOutcome.REJECTED -> AgentTheme.error
                i == activeNode || i == 2 && activity.verifiedReport || i == 3 && control -> AgentTheme.accent
                else -> AgentTheme.muted
            }
            rings(canvas, x, y, small, nodeColor)
            val label = nodeLabels[i]
            val detail = when (i) {
                0 -> if (activity.modelCall > 0) "${activity.modelCall}/8 CALLS" else if (model == "unconfigured") "SETUP" else "READY"
                1 -> when (activity.toolOutcome) { ToolOutcome.REJECTED -> "REJECTED"; ToolOutcome.ACCEPTED -> "RESULT"; else -> if (i == activeNode) "RUNNING" else "IDLE" }
                2 -> if (activity.verifiedReport) "VERIFIED" else if (activity.fileApproved) "APPROVED" else "NO GRANT"
                else -> if (control) "MANUAL ON" else "OFF"
            }
            if (resources.configuration.fontScale <= 1.3f && width / resources.displayMetrics.density >= 280) {
                label(canvas, label, x, y + small + dp(19), AgentTheme.text, 11)
                label(canvas, detail, x, y + small + dp(35), nodeColor, 10)
            } else label(canvas, "0${i + 1}", x, y + sp(4), nodeColor, 11)
        }
        rings(canvas, cx, cy, central, if (activity.stage == ActivityStage.FAILED) AgentTheme.error else AgentTheme.accent)
        label(canvas, activity.stage.label, cx, cy + sp(4), AgentTheme.text, 12)
        label(canvas, "01 / TASK", cx, cy + central + dp(20), AgentTheme.accent, 11)
        label(canvas, if (activity.stage.busy) "EXECUTION ACTIVE" else "OWNER IN CONTROL", cx, dp(27), AgentTheme.muted, 10)
    }
    private fun activeNode() = when (activity.stage) {
        ActivityStage.MODEL -> 0
        ActivityStage.TOOL -> 1
        ActivityStage.VERIFYING -> 2
        else -> -1
    }
    private fun streamX(t: Float, lane: Int, strand: Float): Float {
        val inverse = 1f - t
        return width * (.34f + .36f * (1f - inverse * inverse * inverse) +
            (.033f * lane + .022f * strand) * t)
    }
    private fun streamY(t: Float) = height * (.80f - .64f * t * t * t)

    /** Reference-inspired ribbons are a phase visualization, not invented performance data. */
    private fun drawSignal(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val active = activeNode()
        val compact = resources.configuration.fontScale > 1.3f || w / resources.displayMetrics.density < 280
        ink.style = Paint.Style.STROKE; ink.strokeWidth = dp(1); ink.color = AgentTheme.stroke
        canvas.drawLine(w * .29f, h * .18f, w * .29f, h * .85f, ink)
        canvas.drawLine(w * .34f, h * .84f, w * .93f, h * .84f, ink)
        for (index in streams.indices) {
            val lane = index / 14
            ink.style = Paint.Style.STROKE; ink.strokeWidth = dp(1)
            ink.color = streamColors[lane]; ink.alpha = if (lane == active) 105 else 30
            canvas.drawPath(streams[index], ink)
        }
        ink.alpha = 255
        // A bounded native particle field: no video decode, random allocation or idle frame loop.
        if (active >= 0 && animationsRunning) {
            ink.style = Paint.Style.FILL; ink.color = streamColors[active]
            for (index in 0 until 72) {
                val t = (packet + index / 72f) % 1f
                val spread = (index % 9) / 8f
                val flutter = sin((packet * 2f + index) * 6.283185f) * dp(2) * t
                canvas.drawCircle(streamX(t, active, spread) + flutter, streamY(t),
                    dp(1) * (1f + t), ink)
            }
        }
        // Text stays outside the streams. The wrapping native legend supplies large-text detail.
        for (lane in 0..3) {
            val y = h * (.26f + lane * .15f)
            val color = when {
                lane == 1 && activity.toolOutcome == ToolOutcome.REJECTED -> AgentTheme.error
                lane == active || lane == 2 && activity.verifiedReport || lane == 3 && control -> streamColors[lane]
                else -> AgentTheme.muted
            }
            ink.style = Paint.Style.FILL; ink.color = color
            canvas.drawCircle(w * .065f, y - dp(4), dp(3), ink)
            label(canvas, if (compact) "0${lane + 1}" else nodeLabels[lane], w * .17f, y, color, 10)
            if (!compact) label(canvas, when (lane) {
                0 -> "${activity.modelCall}/8"
                1 -> when (activity.toolOutcome) { ToolOutcome.REJECTED -> "DENIED"; ToolOutcome.ACCEPTED -> "RESULT"; else -> if (active == 1) "ACTIVE" else "IDLE" }
                2 -> if (activity.verifiedReport) "VERIFIED" else if (activity.fileApproved) "GRANTED" else "NO GRANT"
                else -> if (control) "MANUAL" else "OFF"
            }, w * .17f, y + dp(16), AgentTheme.muted, 9)
        }
        label(canvas, "SIGNAL / ${activity.stage.label}", w * .5f, dp(27),
            if (activity.stage == ActivityStage.FAILED) AgentTheme.error else AgentTheme.accent, 11)
        label(canvas, if (compact) activity.stage.label else if (activity.stage.busy) "LIVE PHASE · NO THROUGHPUT METRIC" else "STANDBY · OWNER IN CONTROL",
            w * .5f, h * .94f, AgentTheme.muted, 9)
    }
    private fun rings(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        ink.style = Paint.Style.FILL; ink.color = AgentTheme.background
        canvas.drawCircle(x, y, radius, ink)
        ink.style = Paint.Style.STROKE; ink.color = color; ink.strokeWidth = dp(1)
        canvas.drawCircle(x, y, radius, ink)
        ink.color = AgentTheme.stroke; canvas.drawCircle(x, y, radius * .78f, ink)
        ink.color = color; canvas.drawCircle(x, y, radius * .56f, ink)
    }
    private fun label(canvas: Canvas, value: String, x: Float, y: Float, color: Int, size: Int) {
        ink.style = Paint.Style.FILL; ink.color = color; ink.textSize = sp(size)
        ink.typeface = Typeface.MONOSPACE; ink.textAlign = Paint.Align.CENTER
        canvas.drawText(value, x, y, ink)
    }
}
