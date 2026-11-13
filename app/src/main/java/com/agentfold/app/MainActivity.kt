package com.agentfold.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.graphics.Typeface
import com.agentfold.app.ui.AgentTheme
import com.agentfold.app.ui.ActivityNetworkView
import com.agentfold.app.ui.neural.*
import com.agentfold.app.ui.AdaptiveWorkspace
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.HorizontalScrollView
import com.agentfold.app.agent.AgentRuntime
import com.agentfold.app.agent.CompatibleProvider
import com.agentfold.app.agent.CredentialStore
import com.agentfold.app.agent.ProviderConfig
import com.agentfold.app.agent.ToolScope
import com.agentfold.app.agent.readBytesBounded

/** Dark, resizeable native dashboard that works in Android multi-window. */
class MainActivity : Activity() {
    private lateinit var networkView: ActivityNetworkView
    private lateinit var neuralView: NeuralDialView
    val visualController = NeuralController()
    private lateinit var liveObjective: TextView
    private lateinit var liveSummary: TextView
    private lateinit var networkLegend: TextView
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var fileLabel: TextView
    private lateinit var agentStatus: TextView
    private lateinit var agentAnswer: TextView
    private lateinit var workspace: AdaptiveWorkspace
    private val agent by lazy { AgentRuntime.get(this) }
    private val refresh: () -> Unit = { refreshStatus() }
    private val pickRequest = 101
    private val exportRequest = 102
    private val reportExportRequest = 104
    private var exportBytes: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(AgentTheme.background)
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        status = text("Checking permissions...", 12f, AgentTheme.muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val network = section("01 / LIVE ACTIVITY")
        networkView = ActivityNetworkView(this)
        neuralView = NeuralDialView(this, visualController)
        network.background = null
        val visuals = getSharedPreferences("workspace_visuals", MODE_PRIVATE)
        var fallback = savedInstanceState?.getBoolean("neural_fallback", false) ?: false
        val legacyTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val signalTab = button("signal", {})
        val networkTab = button("network", {})
        fun showFallback(value: Boolean) {
            fallback = value
            networkView.visibility = if (value) View.VISIBLE else View.GONE
            neuralView.visibility = if (value) View.GONE else View.VISIBLE
            legacyTabs.visibility = if (value) View.VISIBLE else View.GONE
        }
        fun selectLegacy(value: ActivityNetworkView.Presentation) {
            networkView.setPresentation(value)
            visuals.edit().putString("activity_view", value.name).apply()
            signalTab.isSelected = value == ActivityNetworkView.Presentation.SIGNAL
            networkTab.isSelected = value == ActivityNetworkView.Presentation.NETWORK
            signalTab.contentDescription = "Signal activity view"
            networkTab.contentDescription = "Network activity view"
        }
        signalTab.setOnClickListener { selectLegacy(ActivityNetworkView.Presentation.SIGNAL); showFallback(true) }
        networkTab.setOnClickListener { selectLegacy(ActivityNetworkView.Presentation.NETWORK); showFallback(true) }
        legacyTabs.addView(signalTab, LinearLayout.LayoutParams(0, -2, 1f))
        legacyTabs.addView(networkTab, LinearLayout.LayoutParams(0, -2, 1f))
        selectLegacy(runCatching {
            ActivityNetworkView.Presentation.valueOf(visuals.getString("activity_view", "SIGNAL") ?: "SIGNAL")
        }.getOrDefault(ActivityNetworkView.Presentation.SIGNAL))
        val visualSwitch = button("neural / fallback", { showFallback(!fallback) })
        visualSwitch.contentDescription = "Switch neural and legacy activity renderer"
        val previewButton = button("development preview", {
            val labels = arrayOf("return to live activity") + VisualState.entries.map { it.label }.toTypedArray()
            AlertDialog.Builder(this).setTitle("visual simulation — no network actions")
                .setItems(labels) { _, which ->
                    if (which == 0) { visualController.endPreview(); refreshStatus() }
                    else visualController.preview(VisualState.entries[which - 1])
                }.setNegativeButton("close", null).show()
        })
        network.addView(neuralView, LinearLayout.LayoutParams(-1, dp(380)))
        network.addView(legacyTabs)
        showFallback(fallback)
        network.addView(networkView, LinearLayout.LayoutParams(-1, dp(280)))
        val visualControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        visualControls.addView(visualSwitch, LinearLayout.LayoutParams(0, -2, 1f))
        visualControls.addView(previewButton, LinearLayout.LayoutParams(0, -2, 1f))
        network.addView(visualControls)
        liveObjective = text("No task running", 15f, AgentTheme.text)
        liveSummary = text("Ready · owner in control", 12f, AgentTheme.muted)
        network.addView(liveObjective); network.addView(liveSummary)
        networkLegend = text("", 11f, AgentTheme.muted).apply { typeface = Typeface.MONOSPACE }
        network.addView(networkLegend)
        network.addView(text("Motion is a visual expression. Live mode follows public execution stages; development previews are simulations. Counts below are actual model calls.", 11f, AgentTheme.muted))
        val mission = section("02 / MISSION")
        val controls = section("04 / DEVICE CONTROL")
        val files = section("03 / FILES")
        val timeline = section("05 / AUDIT TRAIL")
        mission.addView(text("Native Android voice app • add the tiny 1x1 Voice widget from the home-screen widget picker. The widget starts/stops user-initiated microphone chat; phone tools require TAKE CONTROL and approval for every action. Provider usage may incur charges.", 12f, AgentTheme.muted))
        mission.addView(button("Voice chat ON / OFF", {
            startActivity(Intent(this, VoiceToggleActivity::class.java))
        }))
        mission.addView(button("Available tool library", {
            AlertDialog.Builder(this).setTitle("Agent tools — gated by your approval")
                .setMessage("Files: read_selected_text, read_selected_pdf, write_report.\n\nPhone: phone_observe, phone_tap, phone_scroll, phone_type, phone_back, phone_home.\n\nEach phone tool requires Accessibility ON and a five-minute TAKE CONTROL window. Every tap, scroll, navigation or typing action requires separate approval. Visible UI labels can be sent to the configured provider. Sensitive/password content is withheld. STOP revokes access and stops voice chat.\n\nNo unrestricted shell, root, stealth recording or unapproved file access.")
                .setPositiveButton("Close", null).show()
        }))
        mission.addView(button("Configure AI provider", { configureProvider() }))
        val objective = EditText(this).apply {
            id = R.id.objective_input
            hint = "What should we work on?\nInspect a selected file, diagnose a problem, create a report…"
            AgentTheme.style(this)
            minLines = 3; maxLines = 8
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) }
            setText(getSharedPreferences("workspace", MODE_PRIVATE).getString("draft", ""))
        }
        mission.addView(objective)
        mission.addView(button("Run approved agent task", {
            val config = CredentialStore(this).read()
            val task = objective.text.toString().trim()
            when {
                config == null -> message("Configure a provider and your own API key first")
                task.isBlank() || task.length > 8000 -> message("Enter an objective of 1–8000 characters")
                agent.running -> message("Wait for the task or press STOP")
                else -> {
                    getSharedPreferences("workspace", MODE_PRIVATE).edit().putString("draft", task).apply()
                    val uri = SessionStore.fileUri(this)
                    val mimeType = uri?.let { runCatching { contentResolver.getType(Uri.parse(it)) }.getOrNull() }
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Approve task and provider access")
                        .setMessage("Send this objective to ${config.endpoint} using ${config.model}.\n\nSelected file: ${SessionStore.file(this)}. If allowed, UTF-8 contents (up to 48 KiB) or extracted PDF text may be sent to that provider. PDF limits: Android 15+, 4 MiB, 24 pages, 24 KiB extracted text; no OCR, images or encrypted PDFs. The agent may create one private report for review and export. No other files are read. If device control is separately enabled, phone_observe can transmit limited visible app labels to this provider; never use this around sensitive information.\n\nMaximum eight model calls; three-minute deadline checked between operations; 24,000-token usage threshold (estimated if usage is unavailable). This is not a guaranteed spending cap. Provider billing is separate. Leaving the dashboard stops this task.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(if (uri != null) "Run • allow selected file" else "Run") { _, _ ->
                            agent.start(task, ToolScope(uri, uri != null, mimeType), CompatibleProvider(config))
                        }
                    if (uri != null) dialog.setNeutralButton("Run without file") { _, _ ->
                        agent.start(task, ToolScope(null, false), CompatibleProvider(config))
                    }
                    dialog.show()
                }
            }
        }))
        agentStatus = text(agent.status, 13f, AgentTheme.accent); mission.addView(agentStatus)
        agentAnswer = text(agent.answer, 14f, AgentTheme.text); mission.addView(agentAnswer)
        mission.addView(button("Export verified agent report", {
            val report = agent.output
            if (report == null || !report.isFile) message("No verified report is available")
            else {
                try {
                    exportBytes = report.inputStream().use { it.readBytesBounded(65536) }
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, report.name)
                    }, reportExportRequest)
                } catch (_: Exception) { message("Report could not be read; export was not started") }
            }
        }))
        timeline.addView(button("Inspect task history", {
            AlertDialog.Builder(this).setTitle("Local task history").setMessage(agent.history().ifBlank { "No tasks yet" })
                .setPositiveButton("Close", null).show()
        }))
        timeline.addView(button("Clear task history", {
            if (agent.running) message("Stop the task first") else
                AlertDialog.Builder(this).setTitle("Clear local task history?")
                    .setMessage("Deletes the local journal and draft. Exported reports remain in your chosen locations.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ ->
                        agent.clearHistory()
                        getSharedPreferences("workspace", MODE_PRIVATE).edit().clear().apply()
                        objective.setText("")
                    }.show()
        }))
        controls.addView(button("1 · Enable Android accessibility", {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }))
        controls.addView(button("App and installation details", {
            AlertDialog.Builder(this).setTitle("Agent Fold v$version")
                .setMessage("Package: $packageName\n\nThis development Recovery app has separate private data from legacy Agent Fold. Enable only one accessibility controller at a time. Android requires the same signing certificate for in-place updates.\n\nModel/API billing is separate. Device access requires an expiring TAKE CONTROL session and action approvals; model-driven gestures are now available only when you start an expiring TAKE CONTROL session and approve every action. STOP remains available in every workspace.")
                .setPositiveButton("Close", null).show()
        }))
        controls.addView(button("Allow STOP notification fallback", {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 103)
            else message("Notifications already available; system settings can revoke them")
        }))
        controls.addView(button("Observe app structure once (local only)", {
            message(AgentAccessibilityService.instance?.observeOnce() ?: "Enable accessibility first")
        }))
        controls.addView(button("2 · TAKE CONTROL", {
            val svc = AgentAccessibilityService.instance
            if (svc == null) message("Enable Agent Fold in Android Accessibility settings first.")
            else AlertDialog.Builder(this)
                .setTitle("Allow five minutes of phone control?")
                .setMessage("For five minutes, an AI task can request a bounded view of visible app labels and buttons. Those labels may be sent to your configured AI provider. Password text and Android security windows are excluded, but other personal information may be visible. Every tap, scroll, text entry or navigation still requires your separate on-screen approval. You can press STOP at any time. Do not enable this while viewing highly sensitive content.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("TAKE CONTROL") { _, _ -> svc.startSession(); refreshStatus() }
                .show()
            refreshStatus()
        }))
        controls.addView(button("Select target: WHOLE / LEFT / RIGHT", {
            val svc = AgentAccessibilityService.instance
            if (svc == null) message("Accessibility service not connected")
            else message("Target: ${svc.cycleTarget()}")
        }))
        controls.addView(button("Test scroll on selected target", {
            val svc = AgentAccessibilityService.instance
            if (svc == null || !svc.controlActive) {
                message("First press TAKE CONTROL")
            } else {
                if (!svc.scrollTest(onDone = { _ -> runOnUiThread { refreshStatus() } }))
                    message("Gesture not started; inspect action log")
            }
        }))
        controls.addView(text("MANUAL TOUCH / NAVIGATION TESTS", 14f, AgentTheme.text))
        val coordinateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val xPercent = EditText(this).apply {
            id = R.id.x_coordinate
            setText("25"); hint = "X %"; inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true); AgentTheme.style(this)
        }
        val yPercent = EditText(this).apply {
            id = R.id.y_coordinate
            setText("50"); hint = "Y %"; inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true); AgentTheme.style(this)
        }
        coordinateRow.addView(xPercent, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        coordinateRow.addView(yPercent, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(coordinateRow)
        controls.addView(button("Tap coordinates (confirm first)", {
            val svc = AgentAccessibilityService.instance
            val x = xPercent.text.toString().toIntOrNull()
            val y = yPercent.text.toString().toIntOrNull()
            when {
                svc == null || !svc.controlActive -> message("Enable Take Control first")
                x == null || y == null || x !in 1..99 || y !in 1..99 -> message("Set X and Y to 1–99% of target app")
                else -> if (!svc.tapPercent(x, y, onDone = { _ -> runOnUiThread { refreshStatus() } }))
                    message("Tap rejected; inspect action log")
            }
        }))
        controls.addView(button("Go BACK (manual)", {
            val ok = AgentAccessibilityService.instance?.navigateBack() ?: false
            if (!ok) message("Back unavailable: enable Take Control")
        }))
        controls.addView(button("Go HOME (manual)", {
            val ok = AgentAccessibilityService.instance?.navigateHome() ?: false
            if (!ok) message("Home unavailable: enable Take Control")
        }))
        controls.addView(button("STOP · REVOKE CONTROL", {
            stopWork()
            refreshStatus()
        }, danger = true))
        controls.addView(button("Show floating numbered bar", {
            val svc = AgentAccessibilityService.instance
            if (svc == null) message("Enable accessibility first") else svc.restoreBar()
        }))
        files.addView(text("Only files you choose are available to the agent.", 12f, AgentTheme.muted))
        fileLabel = text(SessionStore.file(this), 12f, AgentTheme.accent)
        files.addView(fileLabel)
        files.addView(button("Choose a file", {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, pickRequest)
        }))
        files.addView(button("Save activity log to a file", {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "AgentFold-activity-log.txt")
            }, exportRequest)
        }))
        log = text("", 12f, AgentTheme.muted).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        val logScroll = ScrollView(this).apply { addView(log) }
        val logHeight = (220f * resources.displayMetrics.density).toInt()
        timeline.addView(logScroll, LinearLayout.LayoutParams(-1, logHeight))
        workspace = AdaptiveWorkspace(this, listOf(network, mission, files, controls, timeline))
        root.addView(workspace)
        val dashboardScroll = ScrollView(this).apply {
            id = R.id.dashboard_scroll
            isFillViewport = true
            addView(root)
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AgentTheme.background)
        }
        shell.addView(button("STOP · REVOKE CONTROL", {
            stopWork()
            refreshStatus()
        }, danger = true))
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), dp(4))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(text("AGENT FOLD", 18f, AgentTheme.text).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = .08f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        brand.addView(text("v$version / RECOVERY", 10f, AgentTheme.accent))
        header.addView(brand); header.addView(status)
        shell.addView(header)
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(18), 0, dp(18), 0)
        }
        val navigationScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(navigation)
        }
        val tabs = AdaptiveWorkspace.Panel.values().associateWith { panel ->
            button(panel.label, {}).apply {
                minWidth = dp(96); minimumWidth = dp(96)
                navigation.addView(this, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            }
        }
        fun selectWorkspace(panel: AdaptiveWorkspace.Panel, resetScroll: Boolean = true) {
            if (panel != workspace.selection) {
                currentFocus?.clearFocus()
                dashboardScroll.windowToken?.let { token ->
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(token, 0)
                }
            }
            workspace.select(panel)
            dashboardScroll.accessibilityPaneTitle = "${panel.label} workspace"
            if (resetScroll) dashboardScroll.scrollTo(0, 0)
            tabs.forEach { (candidate, tab) ->
                val selected = candidate == panel
                tab.isSelected = selected
                tab.contentDescription = "${candidate.label} workspace tab${if (selected) ", selected" else ""}"
                AgentTheme.styleTab(tab, selected)
            }
            navigationScroll.post { navigationScroll.smoothScrollTo(tabs.getValue(panel).left - dp(18), 0) }
        }
        tabs.forEach { (panel, tab) -> tab.setOnClickListener { selectWorkspace(panel) } }
        val restoredPanel = runCatching { AdaptiveWorkspace.Panel.valueOf(savedInstanceState?.getString("workspace_tab") ?: "ACTIVITY") }
            .getOrDefault(AdaptiveWorkspace.Panel.ACTIVITY)
        selectWorkspace(restoredPanel, resetScroll = false)
        shell.addView(navigationScroll)
        shell.addView(dashboardScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(shell)
        SessionStore.write(this, "Dashboard opened")
        refreshStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("workspace_tab", workspace.selection.name)
        outState.putBoolean("neural_fallback", networkView.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    private fun stopWork() {
        // STOP also exits visual simulations so no active-looking scene survives revocation.
        visualController.endPreview()
        val service = AgentAccessibilityService.instance
        if (service != null) service.stopSession() else agent.cancel()
    }
    private fun dp(value: Int) = AgentTheme.dp(this, value)
    private fun section(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val d = dp(16); setPadding(d, d, d, d)
        background = AgentTheme.panel(this@MainActivity)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        addView(text(title, 11f, AgentTheme.accent).apply { typeface = Typeface.MONOSPACE; letterSpacing = .08f })
    }
    private fun text(value: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(dp(2), dp(8), dp(2), dp(8))
    }
    private fun button(label: String, click: () -> Unit, danger: Boolean = false): Button =
        Button(this).apply {
            text = label
            setAllCaps(false)
            textSize = 13f
            AgentTheme.style(this, danger)
            setOnClickListener { click() }
            val p = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            layoutParams = p
        }
    private fun message(msg: String) {
        SessionStore.write(this, msg)
        refreshStatus()
    }
    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val svc = AgentAccessibilityService.instance
        val snapshot = agent.activity
        val deviceStatus = when {
            svc == null -> "Control off · accessibility disconnected"
            svc.controlActive -> "Manual control authorized (${svc.targetLabel()})"
            else -> "Accessibility on · control off"
        }
        val summary = "$deviceStatus\n${snapshot.stage.label} · ${snapshot.modelCall}/8 model calls"
        if (status.text.toString() != summary) status.text = summary
        val modelLabel = snapshot.modelLabel ?: CredentialStore(this).modelName()
        networkView.show(snapshot, modelLabel, svc?.controlActive == true)
        // Public runtime and voice phases are the only live neural inputs. Peer/security scenes stay preview-only.
        if (!visualController.snapshot.preview) {
            val signal = liveVisualSignal(snapshot.stage, VoiceChatService.visualPhase)
            visualController.setVisualState(signal.state)
            visualController.setThoughtActivity(signal.activity)
        }
        liveObjective.text = snapshot.objective
        liveSummary.text = "${snapshot.stage.label} · ${agent.status}"
        networkLegend.text = "01 MODEL / $modelLabel · ${snapshot.modelCall}/8 calls\n" +
            "02 TOOLS / ${snapshot.tool ?: "none"} · ${snapshot.toolOutcome.name.lowercase()}\n" +
            "03 FILES / ${if (snapshot.verifiedReport) "verified report" else "no report from this task"}\n" +
            "04 CONTROL / ${if (svc?.controlActive == true) "manual authorization" else "off"}"
        log.text = SessionStore.events(this)
        fileLabel.text = SessionStore.file(this)
        agentStatus.text = agent.status
        agentAnswer.text = agent.answer + (agent.output?.let { "\n\nVerified report: ${it.name}" } ?: "")
    }
    override fun onStart() { super.onStart(); networkView.setHostActive(true); neuralView.setHostActive(true); SessionStore.subscribe(refresh); refreshStatus() }
    override fun onStop() {
        networkView.setHostActive(false)
        neuralView.setHostActive(false)
        SessionStore.unsubscribe(refresh)
        if (!isChangingConfigurations && !VoiceChatService.isActive) agent.cancel("Dashboard left")
        super.onStop()
    }

    private fun configureProvider() {
        val store = CredentialStore(this)
        val saved = store.read()
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        fun field(hintValue: String, value: String, secret: Boolean = false): EditText = EditText(this).apply {
            hint = hintValue; setText(value); setSingleLine(true)
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            if (secret) { setAutofillHints(); importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO }
            AgentTheme.style(this)
            fields.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val endpoint = field("HTTPS base URL, ending in /v1", saved?.endpoint ?: "https://api.openai.com/v1")
        val model = field("Provider model ID", saved?.model ?: "")
        val key = field("API key • blank keeps existing key", "", true)
        val dialog = AlertDialog.Builder(this).setTitle("Your AI provider")
            .setMessage("Use a tool-capable OpenAI-compatible model. Your key is encrypted on this device. API charges are separate from ChatGPT. Only approved task data is sent.")
            .setView(fields).setNegativeButton("Cancel", null)
            .setNeutralButton("Forget key") { _, _ -> agent.cancel("Provider revoked"); store.clear(); message("Provider credentials deleted") }
            .setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    agent.cancel("Provider changed")
                    require(saved == null || endpoint.text.toString().trim().trimEnd('/') == saved.endpoint || key.text.isNotBlank()) {
                        "Enter a new key when changing provider endpoint"
                    }
                    store.save(ProviderConfig(endpoint.text.toString().trim(), model.text.toString().trim(),
                        key.text.toString().ifBlank { saved?.apiKey ?: "" }))
                    key.setText(""); dialog.dismiss(); message("Provider configuration saved securely")
                } catch (e: Exception) { key.error = e.message ?: "Invalid configuration" }
            }
        }
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    @Deprecated("Android's document picker still supports this callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == exportRequest || requestCode == reportExportRequest) && resultCode == RESULT_OK) {
            val outUri = data?.data ?: return
            try {
                val report = if (requestCode == reportExportRequest) requireNotNull(exportBytes)
                    else ("Agent Fold activity log\n\n" + SessionStore.events(this) + "\n").toByteArray(Charsets.UTF_8)
                contentResolver.openOutputStream(outUri, "w")?.use { stream ->
                    stream.write(report)
                } ?: throw IllegalStateException("No output stream")
                val readback = contentResolver.openInputStream(outUri)?.use { stream ->
                    stream.readBytesBounded(65536)
                } ?: throw IllegalStateException("Cannot verify output")
                require(readback.contentEquals(report)) { "Output readback differs" }
                var outputName = "Verified output"
                contentResolver.query(outUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) outputName = c.getString(0) ?: outputName
                }
                SessionStore.setOutput(this, outputName)
                message("Verified output saved: $outputName (${report.size} bytes)")
                AgentAccessibilityService.instance?.restoreBar()
            } catch (e: Exception) {
                message("Could not save log: ${e.javaClass.simpleName}")
            }
            exportBytes = null
        }
        if (requestCode == pickRequest && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val grant = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (grant == 0 || uri.scheme != "content") { message("Document provider did not grant supported read access"); return }
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Selected provider may grant only temporary access. */ }
            var name = "Selected document"
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: name
                }
            } catch (_: Exception) { message("Provider metadata unavailable; file was not selected"); return }
            SessionStore.setFile(this, name, uri.toString())
            message("Document selected with user permission: $name")
            AgentAccessibilityService.instance?.restoreBar()
        }
    }
}

