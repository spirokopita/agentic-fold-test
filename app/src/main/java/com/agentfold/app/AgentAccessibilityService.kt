package com.agentfold.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.agentfold.app.control.ActionTicket
import com.agentfold.app.control.ActionVerification
import com.agentfold.app.control.ControlSession
import com.agentfold.app.control.WindowFrame
import java.security.MessageDigest
import com.agentfold.app.ui.AgentTheme
import android.graphics.Typeface

/** All dispatch, approval, expiry and STOP events are serialized on Android's main looper. */
class AgentAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: AgentAccessibilityService? = null
            private set
        const val WHOLE = 0
        const val LEFT = 1
        const val RIGHT = 2
        private const val NOTIFICATION_ID = 41
    }
    private val main = Handler(Looper.getMainLooper())
    private val session = ControlSession { SystemClock.elapsedRealtime() }
    val controlActive get() = session.active
    var target = WHOLE
        private set
    private var overlay: LinearLayout? = null
    private var details: LinearLayout? = null
    private var panelText: TextView? = null
    private var statusText: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val notifications by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private var selectedPanel = 1
    private var lastAction = "No action executed"
    private var currentPackage = "Unknown"
    private var approval: AlertDialog? = null
    private var pendingFrame: WindowFrame? = null
    private var pendingDone: ((Boolean) -> Unit)? = null
    private var verification: Runnable? = null
    private var approvalExpiry: Runnable? = null
    private val expiry = Runnable { stopSession("Session expired after five minutes") }
    private var observed = "Observation off"
    private val refreshOverlay: () -> Unit = { renderPanel() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        SessionStore.subscribe(refreshOverlay)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        session.stop()
        notifications.createNotificationChannel(NotificationChannel("control", "Agent Fold control", NotificationManager.IMPORTANCE_LOW))
        SessionStore.write(this, "Accessibility connected; device control OFF")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!controlActive) return // No ambient screen collection.
        if (event?.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            // Best effort only: ordinary touchscreen events are not guaranteed to be exposed.
            stopSession("Human touch detected; control yielded")
            return
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = event.packageName?.toString() ?: "Unknown"
            updatePanel()
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val expected = pendingFrame
            if (expected != null && resolveFrame(expected.id) != expected) {
                stopSession("Window moved, resized or disappeared; authorize again")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        stopSession("Display configuration changed; authorize again")
        clampOverlay()
    }
    override fun onInterrupt() { stopSession("Accessibility interrupted") }
    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }
    override fun onDestroy() { shutdown(); super.onDestroy() }
    private fun shutdown() {
        SessionStore.unsubscribe(refreshOverlay)
        stopSession("Accessibility disconnected")
        removeOverlay()
        instance = null
        main.removeCallbacksAndMessages(null)
    }

    fun startSession() {
        stopSession("Previous authorization revoked")
        showOverlay()
        val notificationAvailable = postControlNotification()
        if (overlay == null && !notificationAvailable) {
            record("Control denied: no persistent STOP path available")
            return
        }
        session.start()
        main.postDelayed(expiry, ControlSession.SESSION_MS)
        record("Manual control granted for five minutes; every action needs fresh approval")
    }

    fun stopSession(reason: String = "STOP: device control revoked") {
        val hadWork = controlActive || session.busy
        session.stop() // Revoke before dismissing UI or running callbacks.
        // Network disconnection may take time. Invalidate device input first.
        if (reason.startsWith("STOP")) com.agentfold.app.agent.AgentRuntime.peek()?.cancel(reason)
        main.removeCallbacks(expiry)
        verification?.let(main::removeCallbacks); verification = null
        approvalExpiry?.let(main::removeCallbacks); approvalExpiry = null
        approval?.dismiss(); approval = null
        pendingFrame = null
        val done = pendingDone; pendingDone = null
        notifications.cancel(NOTIFICATION_ID)
        observed = "Observation off"
        if (hadWork) record(reason) else updatePanel()
        done?.invoke(false)
    }

    fun cycleTarget(): String {
        if (session.busy) stopSession("Target changed during pending action")
        target = (target + 1) % 3
        record("Target preference: ${targetLabel()} — coordinates use app bounds")
        return targetLabel()
    }
    fun targetLabel() = when (target) { LEFT -> "LEFT"; RIGHT -> "RIGHT"; else -> "ACTIVE APP" }

    private fun frames(): List<WindowFrame> {
        return windows.mapNotNull { window ->
            try {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@mapNotNull null
                if (Build.VERSION.SDK_INT >= 30 && window.displayId != 0) return@mapNotNull null
                val root = window.root ?: return@mapNotNull null
                try {
                    val pkg = root.packageName?.toString() ?: return@mapNotNull null
                    if (pkg == packageName) return@mapNotNull null
                    val bounds = Rect(); window.getBoundsInScreen(bounds)
                    WindowFrame(window.id, pkg, bounds.left, bounds.top, bounds.right, bounds.bottom)
                } finally { @Suppress("DEPRECATION") root.recycle() }
            } catch (_: Exception) { null }
        }.filter { it.width > 0 && it.height > 0 }
    }

    private fun chooseFrame(): WindowFrame? {
        val available = frames()
        if (target == WHOLE) {
            val activeId = windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.id
            return available.firstOrNull { it.id == activeId } ?: available.singleOrNull()
        }
        val area = displayBounds()
        val center = area.width() / 2
        return available.filter { if (target == LEFT) (it.left + it.right) / 2 < center else (it.left + it.right) / 2 > center }
            .singleOrNull()
    }
    private fun resolveFrame(id: Int) = frames().firstOrNull { it.id == id }
    private fun displayBounds(): Rect = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds
        else Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

    private data class Snapshot(val fingerprint: String, val nodes: Int, val sensitive: Boolean)
    /** Structural fingerprint only. Screen text, descriptions and credentials are never stored. */
    private fun snapshot(frame: WindowFrame): Snapshot? {
        val window = windows.firstOrNull { it.id == frame.id } ?: return null
        val root = window.root ?: return null
        val structure = StringBuilder()
        var count = 0
        var sensitive = false
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= 400 || depth > 24) { sensitive = true; return }
            count++
            if (node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)) {
                sensitive = true; structure.append("[protected]"); return
            }
            val rect = Rect(); node.getBoundsInScreen(rect)
            structure.append(node.className).append(rect.toShortString())
                .append(node.isChecked).append(node.isSelected).append(node.isEnabled)
                .append(node.isScrollable).append(node.childCount)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { visit(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            }
        }
        return try {
            visit(root, 0)
            val digest = MessageDigest.getInstance("SHA-256").digest(structure.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            Snapshot(digest, count, sensitive)
        } catch (_: Exception) { null }
        finally { @Suppress("DEPRECATION") root.recycle() }
    }

    fun observeOnce(): String {
        // Explicit local observation is possible without authorizing control.
        val frame = chooseFrame() ?: return "No external application window available"
        val result = snapshot(frame) ?: return "Application does not expose an accessible tree"
        observed = "${frame.packageName}: ${result.nodes} structural nodes; protected=${result.sensitive}; no screen text saved"
        record("Explicit local observation: $observed")
        return observed
    }

    /** A bounded view of the current app for the model; never runs outside TAKE CONTROL. */
    fun describeForAgent(): String {
        if (!controlActive) return "Screen reading is OFF; activate TAKE CONTROL"
        val frame = chooseFrame() ?: return "No unambiguous app window"
        if (frame.packageName in setOf("com.android.settings", "com.android.systemui", "com.google.android.packageinstaller", "com.android.packageinstaller"))
            return "Protected system window; operate it manually"
        val root = windows.firstOrNull { it.id == frame.id }?.root ?: return "No accessibility tree"
        val out = StringBuilder("App: ${frame.packageName}\n")
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= 75 || depth > 10 || out.length > 4000) return
            count++
            if (node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)) return
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            val label = if (node.isEditable) "[editable; content withheld]" else
                (node.text?.toString()?.take(90) ?: node.contentDescription?.toString()?.take(90) ?: "")
            if (label.isNotBlank() || node.isClickable || node.isEditable)
                out.append(node.className?.toString()?.substringAfterLast('.') ?: "View")
                    .append(": ").append(label.replace('\n', ' '))
                    .append(" clickable=").append(node.isClickable)
                    .append(" enabled=").append(node.isEnabled)
                    .append(" bounds=").append(bounds.toShortString()).append('\n')
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { visit(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            }
        }
        return try { visit(root, 0); out.toString().take(4200) }
        finally { @Suppress("DEPRECATION") root.recycle() }
    }

    /** Set text in a *non-password* editable focused field only, after one-time user approval. */
    fun typeText(value: String, onDone: (Boolean) -> Unit): Boolean {
        if (!controlActive || session.busy || value.length !in 1..500) return false
        val frame = chooseFrame() ?: return false
        if (frame.packageName in setOf("com.android.settings", "com.android.systemui", "com.android.packageinstaller", "com.google.android.packageinstaller")) return false
        val before = snapshot(frame) ?: return false
        if (before.sensitive) return false
        val root = windows.firstOrNull { it.id == frame.id }?.root ?: return false
        val focused = try { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
            finally { @Suppress("DEPRECATION") root.recycle() } ?: return false
        try {
            if (!focused.isEditable || !focused.isEnabled || focused.isPassword ||
                (Build.VERSION.SDK_INT >= 34 && focused.isAccessibilityDataSensitive)) return false
            // Do not retain an AccessibilityNodeInfo across async approval: reacquire on dispatch.
        } finally { @Suppress("DEPRECATION") focused.recycle() }
        val ticket = session.prepare(frame) ?: return false
        pendingFrame = frame
        pendingDone = onDone
        return showApproval(ticket, "Allow typing into ${frame.packageName}?",
            "Type ${value.length} characters into the focused app field:\n\n${value.take(250)}${if (value.length > 250) "..." else ""}\n\nReview the target and the text. Password fields are refused, and the model can see ordinary app labels while control is active.") {
            val current = resolveFrame(frame.id)
            val fresh = current?.let(::snapshot)
            if (current == null || fresh == null || fresh.sensitive || fresh.fingerprint != before.fingerprint || !session.consume(ticket, current)) {
                complete(ticket, "Typing rejected: target changed", false); return@showApproval
            }
            val r = windows.firstOrNull { it.id == frame.id }?.root
            val node = try { r?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } finally { @Suppress("DEPRECATION") r?.recycle() }
            if (node == null) { complete(ticket, "Focused field unavailable", false); return@showApproval }
            val ok = try {
                if (!node.isEditable || node.isPassword || !node.isEnabled || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)) false
                else node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                })
            } finally { @Suppress("DEPRECATION") node.recycle() }
            // Never log or cache typed text; API acceptance does not guarantee semantic success.
            complete(ticket, if (ok) "Text dispatched; verify in target app" else "Android rejected text", ok)
        }
    }

    fun scrollTest(onDone: (Boolean) -> Unit): Boolean = requestGesture("Scroll", 50, 78, true, onDone)
    fun tapPercent(xPercent: Int, yPercent: Int, onDone: (Boolean) -> Unit): Boolean =
        requestGesture("Tap", xPercent, yPercent, false, onDone)

    private fun requestGesture(label: String, x: Int, y: Int, scroll: Boolean, onDone: (Boolean) -> Unit): Boolean {
        if (!controlActive || session.busy) { record("Rejected: start control session or wait for current action"); return false }
        val frame = chooseFrame() ?: run { record("Rejected: no unambiguous external app in selected region"); return false }
        if (frame.packageName in setOf("com.android.settings", "com.android.systemui", "com.google.android.packageinstaller", "com.android.packageinstaller")) {
            record("Rejected: security/system interfaces require manual operation"); return false
        }
        if (frame.point(x, y) == null) { record("Rejected: coordinates outside app"); return false }
        val before = snapshot(frame) ?: run { record("Rejected: cannot observe target"); return false }
        if (before.sensitive) { record("Rejected: protected/password nodes present"); return false }
        val ticket = session.prepare(frame) ?: return false
        pendingFrame = frame
        pendingDone = onDone
        return showApproval(ticket, "$label in ${frame.packageName}",
            "One $label at $x%, $y% of this app's window. Bounds: ${frame.left},${frame.top}–${frame.right},${frame.bottom}.\n\nDo not use this for purchases, sending, deletion or other sensitive actions. Keep hands off the target until verification. Approval expires in 10 seconds.") {
            val current = resolveFrame(frame.id)
            val fresh = current?.let(::snapshot)
            if (current == null || fresh == null || fresh.sensitive || fresh.fingerprint != before.fingerprint ||
                !session.consume(ticket, current)) {
                complete(ticket, "Rejected: target or authorization changed", false)
                return@showApproval
            }
            val point = frame.point(x, y)!!
            if (overlayContains(point.first, point.second) ||
                (scroll && overlayIntersects(point.first, frame.point(50, 33)!!.second, point.second))) {
                complete(ticket, "Rejected: move or minimize floating bar away from gesture path", false)
                return@showApproval
            }
            val path = Path().apply {
                moveTo(point.first, point.second)
                if (scroll) { val end = frame.point(50, 33)!!; lineTo(end.first, end.second) }
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, if (scroll) 550L else 75L)).build()
            record("DISPATCH: $label → ${frame.packageName}; ticket=${ticket.sequence}")
            try {
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (!session.isCurrent(ticket)) return
                        record("Input delivered; checking UI effect")
                        val check = Runnable {
                            if (!session.isCurrent(ticket)) return@Runnable
                            val afterFrame = resolveFrame(frame.id)
                            val after = if (afterFrame == frame) snapshot(frame) else null
                            val outcome = ActionVerification.compare(before.fingerprint, after?.fingerprint, afterFrame == frame)
                            complete(ticket, "VERIFY $label: $outcome (task success requires semantic checks)", outcome.name == "CHANGED")
                        }
                        verification = check
                        main.postDelayed(check, 700)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        complete(ticket, "Input cancelled by Android or another interaction", false)
                    }
                }, main)
                if (!accepted) complete(ticket, "Android rejected gesture dispatch", false)
                else {
                    // Includes gesture duration and verification; no uncontrolled retry.
                    val timeout = Runnable {
                        if (session.isCurrent(ticket)) complete(ticket, "Action timed out; no retry dispatched", false)
                    }
                    main.postDelayed(timeout, 4000)
                }
            } catch (e: Exception) { complete(ticket, "Dispatch error: ${e.javaClass.simpleName}", false) }
        }
    }

    fun navigateHome(): Boolean = navigate(GLOBAL_ACTION_HOME, "Home")
    fun navigateBack(): Boolean = navigate(GLOBAL_ACTION_BACK, "Back")
    private fun navigate(action: Int, label: String): Boolean {
        if (!controlActive || session.busy) { record("Navigation rejected: no session or action pending"); return false }
        val frame = chooseFrame() ?: run { record("Navigation rejected: no external app target"); return false }
        val ticket = session.prepare(frame) ?: return false
        val before = snapshot(frame) ?: run { session.finish(ticket); return false }
        pendingFrame = frame
        return showApproval(ticket, "Allow global $label navigation?",
            "$label applies to the Android display, including the focused split-screen pane. This is a manual global action, not a pane-specific command.") {
            val current = resolveFrame(frame.id)
            if (current == null || !session.consume(ticket, current)) {
                complete(ticket, "Navigation rejected: target changed", false); return@showApproval
            }
            if (!performGlobalAction(action)) {
                complete(ticket, "Android rejected $label", false); return@showApproval
            }
            val check = Runnable {
                if (!session.isCurrent(ticket)) return@Runnable
                val focused = windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val after = resolveFrame(frame.id)?.let(::snapshot)
                val changed = focused?.id != frame.id || (after != null && after.fingerprint != before.fingerprint)
                complete(ticket, "VERIFY $label: ${if (changed) "UI changed" else "effect unverified"}", changed)
            }
            verification = check; main.postDelayed(check, 700)
        }
    }

    private fun showApproval(ticket: ActionTicket, title: String, description: String, action: () -> Unit): Boolean {
        return try {
            val dialog = AlertDialog.Builder(this)
                .setTitle(title).setMessage(description)
                .setNegativeButton("Cancel") { _, _ -> complete(ticket, "Action cancelled by owner", false) }
                .setPositiveButton("Allow once") { _, _ ->
                    approvalExpiry?.let(main::removeCallbacks); approvalExpiry = null
                    approval = null
                    action()
                }.create()
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            dialog.setOnCancelListener { complete(ticket, "Action cancelled by owner", false) }
            approval = dialog
            dialog.show()
            approvalExpiry = Runnable {
                approval?.dismiss(); approval = null
                complete(ticket, "Approval expired; no action dispatched", false)
            }.also { main.postDelayed(it, ControlSession.APPROVAL_MS) }
            record("APPROVAL: $title")
            true
        } catch (e: Exception) { complete(ticket, "Cannot display approval: ${e.javaClass.simpleName}", false); false }
    }

    private fun complete(ticket: ActionTicket, result: String, verified: Boolean) {
        if (!session.finish(ticket)) return // A stale callback cannot restart or alter a later session.
        verification?.let(main::removeCallbacks); verification = null
        approvalExpiry?.let(main::removeCallbacks); approvalExpiry = null
        pendingFrame = null
        val done = pendingDone; pendingDone = null
        record(result); done?.invoke(verified)
    }
    private fun record(event: String) { lastAction = event; SessionStore.write(this, event); updatePanel() }

    private fun postControlNotification(): Boolean {
        if (!notifications.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val stop = PendingIntent.getBroadcast(this, 0, Intent(this, StopReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(NOTIFICATION_ID, Notification.Builder(this, "control")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Agent Fold • manual control")
                .setContentText("Five-minute session. Tap STOP to revoke.")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "STOP", stop).build()).build())
            true
        } catch (_: SecurityException) { false }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; setAllCaps(false); minWidth = dp(48); minimumWidth = dp(48)
        minHeight = dp(48); minimumHeight = dp(48); setPadding(0, 0, 0, 0)
        AgentTheme.style(this, label == "STOP")
        contentDescription = if (label.startsWith("0")) "Expand information panel $label" else label
        setOnClickListener { action() }
    }
    private fun showOverlay() {
        if (overlay != null) return
        val width = displayBounds().width()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
            background = AgentTheme.panel(this@AgentAccessibilityService, 0xf2111a13.toInt(), AgentTheme.stroke, 16)
        }
        val handle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val grip = TextView(this).apply {
            text = "◎"; textSize = 24f; setTextColor(AgentTheme.accent); gravity = Gravity.CENTER
            contentDescription = "Drag control bar"
        }
        handle.addView(grip, LinearLayout.LayoutParams(dp(48), dp(48)))
        handle.addView(button("FOLD") { details?.visibility = if (details?.visibility == View.GONE) View.VISIBLE else View.GONE; clampOverlay() },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        handle.addView(button("OPEN") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, LinearLayout.LayoutParams(dp(64), dp(48)))
        handle.addView(button("STOP") { stopSession() }, LinearLayout.LayoutParams(dp(72), dp(48)))
        root.addView(handle)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(this)
        for (id in 1..4) tabs.addView(button("0$id") { selectedPanel = id; updatePanel() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(tabs)
        val status = TextView(this).apply { setTextColor(AgentTheme.accent); typeface = Typeface.MONOSPACE; textSize = 12f; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val detail = TextView(this).apply { setTextColor(AgentTheme.text); textSize = 12f; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        body.addView(status); body.addView(detail)
        val quick = LinearLayout(this)
        quick.addView(button("SCROLL") { scrollTest {} }, LinearLayout.LayoutParams(0, dp(48), 1f))
        quick.addView(button("BACK") { navigateBack() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        quick.addView(button("HOME") { navigateHome() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(quick); root.addView(body)
        val stored = getSharedPreferences("overlay_position", MODE_PRIVATE)
        val p = WindowManager.LayoutParams(
            dp(328).coerceAtMost(width), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = stored.getInt("x", 0); y = stored.getInt("y", dp(24))
        }
        var downX = 0f; var downY = 0f; var originalX = 0; var originalY = 0
        grip.setOnTouchListener { _: View, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originalX = p.x; originalY = p.y }
                MotionEvent.ACTION_MOVE -> {
                    p.x = originalX + (event.rawX - downX).toInt(); p.y = originalY + (event.rawY - downY).toInt()
                    clampOverlay()
                }
                MotionEvent.ACTION_UP -> {
                    stored.edit().putInt("x", p.x).putInt("y", p.y).apply(); grip.performClick()
                }
            }; true
        }
        try {
            wm.addView(root, p)
            overlay = root; details = body; params = p; statusText = status; panelText = detail
            root.post { clampOverlay() }
            SessionStore.write(this, "Floating STOP bar available")
        } catch (e: Exception) { SessionStore.write(this, "Overlay unavailable: ${e.javaClass.simpleName}") }
    }
    private fun clampOverlay() {
        val view = overlay ?: return
        val p = params ?: return
        val area = displayBounds()
        p.width = dp(328).coerceAtMost(area.width())
        p.x = p.x.coerceIn(0, (area.width() - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (area.height() - view.height).coerceAtLeast(0))
        try { wm.updateViewLayout(view, p) } catch (_: Exception) { }
    }
    private fun overlayRect(): Rect? = overlay?.let {
        val location = IntArray(2); it.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + it.width, location[1] + it.height)
    }
    private fun overlayContains(x: Float, y: Float) = overlayRect()?.contains(x.toInt(), y.toInt()) == true
    private fun overlayIntersects(x: Float, top: Float, bottom: Float): Boolean {
        val rect = overlayRect() ?: return false
        return x >= rect.left && x < rect.right && bottom >= rect.top && top <= rect.bottom
    }
    private fun removeOverlay() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        overlay = null; details = null; panelText = null; statusText = null; params = null
    }
    fun restoreBar() { showOverlay(); updatePanel() }
    private fun updatePanel() {
        renderPanel()
        SessionStore.notifyChanged()
    }
    private fun renderPanel() {
        statusText?.text = if (controlActive) "● CONTROL • ${session.remainingSeconds}s • ${targetLabel()}" else "○ CONTROL OFF"
        panelText?.text = when (selectedPanel) {
            1 -> "01 TASK\n${com.agentfold.app.agent.AgentRuntime.peek()?.status ?: "No agent task"}\nManual gestures need approval."
            2 -> "02 ACTION\n$lastAction\nApp: $currentPackage"
            3 -> "03 FILES\nInput: ${SessionStore.file(this)}\nOutput: ${SessionStore.output(this)}"
            else -> "04 STATUS\nDevice control: ${if (controlActive) "authorized" else "off"}\n$observed\nModel: ${com.agentfold.app.agent.CredentialStore(this).modelName()}"
        }
    }
}
