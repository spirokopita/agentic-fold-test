package com.agentfold.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.biometrics.BiometricPrompt
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.agentfold.app.agent.ProviderConfig
import com.agentfold.app.nerv.AccessibilityHelper
import com.agentfold.app.nerv.AgentBridge
import com.agentfold.app.nerv.AppConnector
import com.agentfold.app.nerv.BatterySaver
import com.agentfold.app.nerv.CalendarLink
import com.agentfold.app.nerv.ContactLookup
import com.agentfold.app.nerv.ChatSearch
import com.agentfold.app.nerv.ChatFolderStore
import com.agentfold.app.nerv.ChatStore
import com.agentfold.app.nerv.DailyBriefing
import com.agentfold.app.nerv.DebateHistory
import com.agentfold.app.nerv.DeviceSync
import com.agentfold.app.nerv.DisplaySettings
import com.agentfold.app.nerv.FolderManager
import com.agentfold.app.nerv.FoldSupport
import com.agentfold.app.nerv.HealthDashboard
import com.agentfold.app.nerv.LocationTracker
import com.agentfold.app.nerv.MagiVoice
import com.agentfold.app.nerv.NervBackup
import com.agentfold.app.nerv.NervLockWidget
import com.agentfold.app.nerv.NervTileService
import com.agentfold.app.nerv.LockSecurity
import com.agentfold.app.nerv.MagiEngine
import com.agentfold.app.nerv.MagiMemory
import com.agentfold.app.nerv.NervStatus
import com.agentfold.app.nerv.NodeKeyStore
import com.agentfold.app.nerv.NoteStore
import com.agentfold.app.nerv.NotificationHelper
import com.agentfold.app.nerv.NervStatusIconView
import com.agentfold.app.nerv.NervVoiceWidget
import com.agentfold.app.nerv.PackManager
import com.agentfold.app.nerv.PackStore
import com.agentfold.app.nerv.PhoneTaskStore
import com.agentfold.app.nerv.PortManager
import com.agentfold.app.nerv.ReportExporter
import com.agentfold.app.nerv.RoutineStore
import com.agentfold.app.nerv.SharedContent
import com.agentfold.app.nerv.SmartReplies
import com.agentfold.app.nerv.SyncMonitor
import com.agentfold.app.nerv.SyncSignals
import com.agentfold.app.nerv.ToxMonitor
import com.agentfold.app.nerv.ToxSignals
import com.agentfold.app.nerv.ToolRunner
import com.agentfold.app.nerv.TutorialGuide
import com.agentfold.app.nerv.TaughtSkillStore
import com.agentfold.app.nerv.TaskReminders
import com.agentfold.app.nerv.TimerStore
import com.agentfold.app.nerv.VoiceConnector
import com.agentfold.app.nerv.WakeWord
import com.agentfold.app.ui.AgentTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class NervActivity : Activity() {
    private data class Screen(val key: String, val glyph: String, val label: String)
    private val screens = listOf(
        Screen("home", "本部", "HOME"), Screen("magi", "決議", "MAGI"),
        Screen("sync", "同調", "SYNC"), Screen("armor", "装甲", "TOOLS"),
        Screen("comms", "通信", "CHAT"), Screen("tox", "毒性", "TOX"),
        Screen("timer", "起動", "TIMER"), Screen("map", "地球", "MAP"),
        Screen("setup", "設定", "SETUP"),
        Screen("clasp", "\u7d50", "CLASP")
    )
    private val palettes = listOf(
        "amber" to intArrayOf(0xffff9a1a.toInt(), 0xffff5a10.toInt(), 0xff5a3200.toInt()),
        "cyan" to intArrayOf(0xff6fffe0.toInt(), 0xff2ad8c0.toInt(), 0xff0b4a42.toInt()),
        "alert" to intArrayOf(0xffff3a2a.toInt(), 0xffff8a1a.toInt(), 0xff4a0c06.toInt()),
        "eva01" to intArrayOf(0xffc58bff.toInt(), 0xff8dff4a.toInt(), 0xff2e1450.toInt())
    )
    private val motions = listOf("SELFDESTRUCTION", "LAUNCH EVA-01", "OPEN SECTOR LINK", "SEAL GEOFRONT")
    private val eegChannels = listOf("TEMPORAL", "AMYGDALA", "HIPPOCAMPUS", "PARIETAL", "MOTOR", "SENSORY")

    private lateinit var packManager: PackManager

    private lateinit var packStore: PackStore
    private lateinit var toolRunner: ToolRunner
    private lateinit var chatStore: ChatStore
    private lateinit var agentBridge: AgentBridge
    private lateinit var magiEngine: MagiEngine

    private lateinit var portManager: PortManager
    private lateinit var folderManager: FolderManager
    private lateinit var voiceConnector: VoiceConnector
    private lateinit var appConnector: AppConnector
    private lateinit var nodeKeyStore: NodeKeyStore

    private lateinit var lockSecurity: LockSecurity

    private lateinit var reportExporter: ReportExporter

    private lateinit var nervBackup: NervBackup

    private lateinit var deviceSync: DeviceSync

    private lateinit var batterySaver: BatterySaver

    private lateinit var wakeWord: WakeWord

    private lateinit var foldSupport: FoldSupport
    private var lastFoldBucket: String? = null

    private lateinit var tutorialGuide: TutorialGuide
    private var tutorialShownThisRun = false
    private var tutorialTestRunning = false

    private lateinit var magiMemory: MagiMemory

    private lateinit var debateHistory: DebateHistory

    private lateinit var dailyBriefing: DailyBriefing

    private lateinit var routineStore: RoutineStore

    private lateinit var calendarLink: CalendarLink
    private val calendarRequestCode = 1703

    private lateinit var contactLookup: ContactLookup
    private val contactsRequestCode = 1704

    private lateinit var taughtSkillStore: TaughtSkillStore

    private lateinit var phoneTaskStore: PhoneTaskStore

    private lateinit var syncMonitor: SyncMonitor

    private lateinit var locationTracker: LocationTracker

    private lateinit var noteStore: NoteStore
    private var pendingTeach: String? = null
    private var chatBusy = false
    private var voiceListening = false

    private var pendingWidgetListen = false

    private var pendingOpenScreen: String? = null

    private var pendingStartTimer = false

    private var pendingSharedText: String? = null
    private var pendingSharedImage: java.io.File? = null

    private var chatSearchQuery: String? = null
    private var chatHighlightIndex: Int = -1

    private lateinit var chatFolderStore: ChatFolderStore
    private var chatFolderFilter: String = "ALL"

    private var notesFolderFilter: String = "ALL"
    private var notesSearchQuery: String? = null

    private lateinit var magiVoice: MagiVoice

    private lateinit var smartReplies: SmartReplies
    private var folderDir: java.io.File? = null
    private var folderListView: LinearLayout? = null
    private var agentStatusView: TextView? = null
    private var statusIconView: NervStatusIconView? = null

    private var lockNodeValueViews = mutableListOf<TextView>()
    private var lockNodeBoxViews = mutableListOf<LinearLayout>()
    private var lockMessageView: TextView? = null
    private var lockVoteRunning = false

    private var pinVerified = false
    private var pinEntry = ""
    private var pinDotsView: TextView? = null
    private var pinAuthButton: Button? = null

    private var lockClockView: TextView? = null
    private var lockTimerView: TextView? = null
    private var lockMagiView: TextView? = null
    private var lockTickCount = 0
    private var lastSavedLockTimer = ""
    private var lastSavedLockMagi = ""

    private lateinit var glowFrame: FrameLayout
    private lateinit var phoneFrame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var screenBody: LinearLayout
    private lateinit var navBar: LinearLayout
    private var navScrollView: HorizontalScrollView? = null
    private lateinit var headerStatus: TextView
    private var current = "home"
    private var palette = "amber"

    private var customAccent = 0
    private var locked = true
    private var clockView: TextView? = null
    private var msView: TextView? = null
    private var statusTime: TextView? = null

    private var statusTitleView: TextView? = null
    private var statusBarView: LinearLayout? = null
    private var scanOn = false
    private var flickerOn = true

    private var soundOn = true
    private var textSizeSp = DisplaySettings.DEFAULT_TEXT_SIZE_SP

    private var textBoost = false
    private var highContrast = false
    private var reduceMotion = false
    private var modeIndex = 2

    private var syncRatio = 62
    private var syncLive = true
    private var syncManualRatio = 62
    private var syncLiveValue = 62
    private var syncSignals: SyncSignals? = null
    private var syncValueView: TextView? = null
    private var syncSliderView: SeekBar? = null
    private var syncLiveBoxView: TextView? = null

    private val armorDamaged = booleanArrayOf(false, true, false, false, true, false)

    private val chatSeed = listOf(
        Triple("MISATO", "06:00", "EVA-02 launch sequence confirmed. Report to Cage 7."),
        Triple("MAGI-01", "07:13", "Deliberation pending. Casper requests additional data."),
        Triple("CMD", "08:26", "Phase 4 psychograph shows contamination at the boundary line."),
        Triple("UNKNOWN", "09:39", "Access attempt from external node. Source masked.")
    )

    private val toxScreenValues = mutableListOf(38, 52, 29)
    private val toxStartTime = System.currentTimeMillis()
    private val toxScreenBars = mutableListOf<StripedBarView>()
    private val toxScreenValueViews = mutableListOf<TextView>()
    private var toxPurityView: TextView? = null
    private var toxElapsedView: TextView? = null

    private lateinit var toxMonitor: ToxMonitor

    private lateinit var healthDashboard: HealthDashboard
    private var healthLinesView: TextView? = null
    private var healthWarningsView: TextView? = null

    private lateinit var displaySettings: DisplaySettings

    private lateinit var notificationHelper: NotificationHelper

    private lateinit var taskReminders: TaskReminders
    private var toxLive = true
    private var toxSignals: ToxSignals? = null
    private val toxOffsets = mutableListOf(0, 0, 0)
    private var toxReadoutView: TextView? = null

    private lateinit var timerStore: TimerStore
    private var timerSelectedId: String? = null
    private var timerValueView: TextView? = null
    private var timerBarView: StripedBarView? = null
    private var timerButtonView: Button? = null
    private var timerEmptyView: TextView? = null
    private var timerRowViews = mutableMapOf<String, Triple<TextView, StripedBarView, Button>>()

    private var mapGlobe: GlobeView? = null
    private var mapCoordView: TextView? = null
    private var mapAccuracyView: TextView? = null
    private var mapStatusView: TextView? = null
    private var mapRotView: TextView? = null

    private var mapFix: LocationTracker.Fix? = null
    private var mapLocationStarted = false
    private val locationRequestCode = 1701

    private val notificationRequestCode = 1702
    private var appInForeground = false

    private val toxValues = mutableListOf(40, 40, 40)
    private val toxBars = mutableListOf<StripedBarView>()

    private val handler = Handler(Looper.getMainLooper())

    private val timeFmtHHmm = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val timeFmtHHmmss = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var lastClockSecond = ""

    private var chatShowAllMessages = false
    private var scanlinesView: ScanlinesView? = null

    private var tapTone: ToneGenerator? = null
    private var pixelCanvasView: PixelCanvasView? = null

    private val lockTickRunnable = object : Runnable {
        override fun run() {

            if (!locked) return
            lockTickCount++
            lockClockView?.text = timeFmtHHmm.format(Date())
            lockTimerView?.text = nextTimerLine()
            lockMagiView?.text = magiStatusLine()

            if (lockTickCount % 30 == 1) refreshLockWidget()
            handler.postDelayed(this, 1000)
        }
    }

    private val flickerRunnable = object : Runnable {
        override fun run() {

            val flickerScale = if (::batterySaver.isInitialized) batterySaver.flickerScale() else 1f

            if (flickerOn && !reduceMotion) {
                phoneFrame.alpha = 1f
                handler.postDelayed({ if (flickerOn && !reduceMotion) phoneFrame.alpha = 0.82f }, (2976 * flickerScale).toLong())
                handler.postDelayed({ if (flickerOn && !reduceMotion) phoneFrame.alpha = 1f }, (3040 * flickerScale).toLong())
                handler.postDelayed({ if (flickerOn && !reduceMotion) phoneFrame.alpha = 0.9f }, (3104 * flickerScale).toLong())
                handler.postDelayed({ if (flickerOn && !reduceMotion) phoneFrame.alpha = 1f }, (3200 * flickerScale).toLong())
            } else {
                phoneFrame.alpha = 1f
            }
            handler.postDelayed(this, (3200 * flickerScale).toLong())
        }
    }

    private val toxSamplerRunnable = object : Runnable {
        override fun run() {
            if (::toxMonitor.isInitialized) {
                if (toxLive) {
                    val signals = toxMonitor.sample()
                    toxSignals = signals
                    val live = toxMonitor.smooth(toxMonitor.valuesFrom(signals))
                    for (i in live.indices) {

                        toxOffsets[i] = when {
                            toxOffsets[i] > 0 -> (toxOffsets[i] - 2).coerceAtLeast(0)
                            toxOffsets[i] < 0 -> (toxOffsets[i] + 2).coerceAtMost(0)
                            else -> 0
                        }
                        val value = (live[i] + toxOffsets[i]).coerceIn(2, 100)
                        toxScreenValues[i] = value
                        toxValues[i] = value
                        toxBars.getOrNull(i)?.setValue(value)
                    }
                    if (current == "tox") updateToxScreen()
                } else {

                    for (i in toxValues.indices) {
                        toxValues[i] = (35 + Math.random() * 55).toInt()
                        toxBars.getOrNull(i)?.setValue(toxValues[i])
                    }
                }
            }

            if (::batterySaver.isInitialized && batterySaver.check(this@NervActivity)) applyBatterySaver()
            handler.postDelayed(this, if (::batterySaver.isInitialized) batterySaver.sampleIntervalMs() else 2000)
        }
    }
    private val toxScreenRunnable = object : Runnable {
        override fun run() {
            if (current == "tox") updateToxScreen()
            handler.postDelayed(this, 700)
        }
    }
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (::timerStore.isInitialized && timerStore.anyRunning()) {

                val finished = timerStore.tick(System.currentTimeMillis())
                for (entry in finished) ringTimerAlarm(entry)
                if (current == "timer") updateTimerViews()
            }
            handler.postDelayed(this, 100)
        }
    }

    private val taskWatcherRunnable = object : Runnable {
        override fun run() {
            val running = if (::phoneTaskStore.isInitialized) phoneTaskStore.running() else null
            if (running != null) {
                if (agentBridge.isRunning()) {
                    agentStatusView?.text = "● TASK RUNNING — ${agentBridge.status()}"
                } else {
                    finalizeRunningTask()
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    private val routineRunnable = object : Runnable {
        override fun run() {
            runDueRoutines()
            handler.postDelayed(this, 60_000)
        }
    }

    private val reminderRunnable = object : Runnable {
        override fun run() {
            checkTaskReminders()
            handler.postDelayed(this, 60_000)
        }
    }

    private fun checkTaskReminders() {
        if (!::taskReminders.isInitialized || !taskReminders.enabled) return
        val now = System.currentTimeMillis()

        val thresholdMillis = taskReminders.waitingThresholdMinutes * 60_000L
        if (::phoneTaskStore.isInitialized) {
            phoneTaskStore.all()
                .filter { it.status == PhoneTaskStore.TaskStatus.PROPOSED }
                .filter { it.createdAtMillis > 0L && now - it.createdAtMillis >= thresholdMillis }
                .filter { !taskReminders.hasRemindedTask(it.id) }
                .forEach { task ->
                    taskReminders.markTaskReminded(task.id)
                    if (!appInForeground || current != "comms") {
                        notificationHelper.notifyTaskWaiting(task.objective)
                    }
                }
        }

        if (::timerStore.isInitialized) {
            timerStore.all()
                .filter { it.running && !it.finished }
                .forEach { entry ->
                    val left = entry.remainingMillis(now)
                    if (left > 0L && left <= TaskReminders.TIMER_WARNING_MILLIS) {
                        val key = "${entry.id}:${entry.endAtMillis}"
                        if (!taskReminders.hasRemindedTimer(key)) {
                            taskReminders.markTimerReminded(key)
                            if (!appInForeground || current != "timer") {
                                notificationHelper.notifyTimerAlmostDone(entry.name)
                            }
                        }
                    }
                }
        }
    }
    private val mapRunnable = object : Runnable {
        override fun run() {

            if (current == "map") updateMapViews()
            handler.postDelayed(this, 300)
        }
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            if (::syncMonitor.isInitialized && syncLive) {
                val signals = syncMonitor.sample()
                syncSignals = signals
                syncLiveValue = syncMonitor.smooth(syncMonitor.ratioFrom(signals))
                syncRatio = syncLiveValue
                if (current == "sync") updateSyncViews()
            }

            handler.postDelayed(this, if (::batterySaver.isInitialized) batterySaver.sampleIntervalMs() else 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packManager = PackManager(this)

        packStore = PackStore(this, packManager)
        noteStore = NoteStore(this)

        calendarLink = CalendarLink(this)

        contactLookup = ContactLookup(this)
        toolRunner = ToolRunner(this, packManager, noteStore, calendarLink, contactLookup)
        chatStore = ChatStore(this)

        chatFolderStore = ChatFolderStore(this, chatStore)
        agentBridge = AgentBridge(this)
        taughtSkillStore = TaughtSkillStore(this, packManager.availableTools().toSet())
        phoneTaskStore = PhoneTaskStore(this)
        magiEngine = MagiEngine(this, toolRunner, agentBridge, taughtSkillStore)
        portManager = PortManager(this)
        folderManager = FolderManager(this)
        voiceConnector = VoiceConnector(this)

        magiVoice = MagiVoice(this)

        smartReplies = SmartReplies(this)
        appConnector = AppConnector(this, agentBridge, magiEngine, portManager, voiceConnector)
        nodeKeyStore = NodeKeyStore(this)

        lockSecurity = LockSecurity(this)
        syncMonitor = SyncMonitor(this)
        toxMonitor = ToxMonitor(this)

        healthDashboard = HealthDashboard(this, toxMonitor)

        batterySaver = BatterySaver(this)
        locationTracker = LocationTracker(this)
        timerStore = TimerStore(this)

        displaySettings = DisplaySettings(this)

        notificationHelper = NotificationHelper(this)

        taskReminders = TaskReminders(this)

        reportExporter = ReportExporter(this, chatStore, phoneTaskStore)

        foldSupport = FoldSupport(this)

        tutorialGuide = TutorialGuide(this)

        magiMemory = MagiMemory(this)

        debateHistory = DebateHistory(this)

        dailyBriefing = DailyBriefing(this)

        routineStore = RoutineStore(this)

        wakeWord = WakeWord(this)

        nervBackup = NervBackup(this, chatStore, noteStore, packManager, taughtSkillStore, displaySettings, magiEngine, routineStore, timerStore, chatFolderStore, batterySaver, wakeWord)

        deviceSync = DeviceSync(this, nervBackup)

        mapFix = locationTracker.savedFix() ?: locationTracker.lastKnown()
        if (chatStore.entries().isEmpty()) {
            chatSeed.forEachIndexed { i, (sender, time, text) ->
                chatStore.add(ChatStore.Entry(ChatStore.Role.SYSTEM, text, time, sender = sender, deleted = i == 3))
            }
        }
        current = savedInstanceState?.getString("screen") ?: "home"

        palette = savedInstanceState?.getString("palette") ?: displaySettings.palette

        customAccent = displaySettings.customAccent
        scanOn = displaySettings.scanlines
        flickerOn = displaySettings.flicker
        soundOn = displaySettings.sound
        textSizeSp = displaySettings.textSizeSp
        textBoost = displaySettings.textBoost
        highContrast = displaySettings.highContrast
        reduceMotion = displaySettings.reduceMotion
        applyReduceMotion()
        locked = savedInstanceState?.getBoolean("locked", true) ?: true
        buildShell()

        batterySaver.check(this)
        applyBatterySaver()

        lastFoldBucket = if (isDualPane()) "wide" else "narrow"
        if (locked) showLock() else showScreen(current)

        handleWidgetIntent(intent)

        handleShareIntent(intent)
        NervVoiceWidget.updateAll(this)
        NervLockWidget.updateAll(this)
        if (!locked) maybeStartPendingWidgetListen()
        if (!locked) maybeApplyPendingOpenScreen()
        handler.post(flickerRunnable)
        handler.postDelayed(toxSamplerRunnable, 2000)
        handler.postDelayed(toxScreenRunnable, 700)
        handler.postDelayed(timerRunnable, 100)
        handler.postDelayed(mapRunnable, 300)
        handler.postDelayed(taskWatcherRunnable, 2000)
        handler.postDelayed(syncRunnable, 2000)

        handler.postDelayed(routineRunnable, 60_000)

        handler.postDelayed(reminderRunnable, 60_000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(flickerRunnable)
        handler.removeCallbacks(toxSamplerRunnable)
        handler.removeCallbacks(toxScreenRunnable)
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(mapRunnable)
        handler.removeCallbacks(taskWatcherRunnable)
        handler.removeCallbacks(syncRunnable)
        handler.removeCallbacks(lockTickRunnable)

        handler.removeCallbacks(routineRunnable)

        handler.removeCallbacks(reminderRunnable)
        if (::voiceConnector.isInitialized) voiceConnector.release()

        if (::magiVoice.isInitialized) magiVoice.release()

        try { tapTone?.release() } catch (e: Exception) { }
        tapTone = null

        if (::locationTracker.isInitialized) locationTracker.stop()
        super.onDestroy()
    }

    override fun onPause() {

        appInForeground = false
        pixelCanvasView?.running = false

        if (::timerStore.isInitialized && ::magiEngine.isInitialized) refreshLockWidget()

        if (::locationTracker.isInitialized) locationTracker.stop()
        mapLocationStarted = false

        if (::foldSupport.isInitialized) foldSupport.stopHingeListening()
        super.onPause()
    }

    override fun onResume() {

        appInForeground = true
        pixelCanvasView?.running = true
        super.onResume()

        if (::foldSupport.isInitialized) foldSupport.startHingeListening()
        if (current == "map") startMapLocation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (locked) return
        val bucket = if (isDualPane()) "wide" else "narrow"
        if (lastFoldBucket != null && lastFoldBucket != bucket) showScreen(current)
        lastFoldBucket = bucket
    }

    private fun isDualPane(): Boolean =
        ::foldSupport.isInitialized && foldSupport.dualPaneEnabled && foldSupport.isUnfolded(this)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == locationRequestCode && current == "map") {
            if (::locationTracker.isInitialized && locationTracker.hasPermission()) startMapLocation()
            updateMapViews()
        }

        if (requestCode == notificationRequestCode && current == "setup") {
            showScreen("setup")
        }

        if (requestCode == calendarRequestCode && (current == "setup" || current == "home")) {
            showScreen(current)
        }

        if (requestCode == contactsRequestCode && current == "setup") {
            showScreen("setup")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", current)
        outState.putString("palette", palette)
        outState.putBoolean("locked", locked)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
        handleShareIntent(intent)
        if (!locked) maybeStartPendingWidgetListen()
        if (!locked) maybeApplyPendingOpenScreen()
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NervVoiceWidget.EXTRA_LISTEN, false) == true) {
            pendingWidgetListen = true
            intent.removeExtra(NervVoiceWidget.EXTRA_LISTEN)
        }

        val screen = intent?.getStringExtra(NervTileService.EXTRA_OPEN_SCREEN)
        if (screen == "magi") {
            pendingOpenScreen = screen
            intent.removeExtra(NervTileService.EXTRA_OPEN_SCREEN)
        }

        val shortcut = intent?.getStringExtra("nerv_shortcut")
        if (shortcut == "magi" || shortcut == "timer") {
            pendingOpenScreen = shortcut
            pendingStartTimer = shortcut == "timer"
            intent.removeExtra("nerv_shortcut")
        } else if (shortcut == "listen") {
            pendingWidgetListen = true
            intent.removeExtra("nerv_shortcut")
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {

            if (!locked) {
                AlertDialog.Builder(this)
                    .setTitle("ONE THING AT A TIME")
                    .setMessage("NERV takes one shared photo or one piece of text at a time — share one, then share the next.")
                    .setPositiveButton("OK", null).show()
            }
            return
        }
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type ?: ""
        if (type.startsWith("image/")) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            val saved = SharedContent.saveSharedImage(this, uri)
            if (saved == null) {
                AlertDialog.Builder(this)
                    .setTitle("COULD NOT SAVE THAT PHOTO")
                    .setMessage("NERV could not read that photo, or it is over the 10 MB limit. Try a smaller one.")
                    .setPositiveButton("OK", null).show()
                return
            }
            pendingSharedImage = saved
            pendingOpenScreen = "comms"
            intent.removeExtra(Intent.EXTRA_STREAM)
        } else {

            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (shared.isBlank()) return
            intent.removeExtra(Intent.EXTRA_TEXT)

            if (DeviceSync.looksLikeSyncText(shared)) {
                val saved = if (::deviceSync.isInitialized) deviceSync.saveIncomingSyncText(shared) else null
                if (saved != null) {
                    if (!locked) {
                        AlertDialog.Builder(this)
                            .setTitle("SYNC FILE RECEIVED")
                            .setMessage("Saved as ${saved.name} in the sync folder. Go to SETUP > MULTI-DEVICE SYNC > IMPORT SYNC FILE to merge it into this phone.")
                            .setPositiveButton("OK", null).show()
                    }
                } else if (!locked) {
                    AlertDialog.Builder(this)
                        .setTitle("COULD NOT SAVE THAT SYNC FILE")
                        .setMessage("That text looks like a NERV sync file but could not be saved. Ask the other phone to export and share it again.")
                        .setPositiveButton("OK", null).show()
                }
                return
            }
            pendingSharedText = shared.take(SharedContent.MAX_TEXT_CHARS)
            pendingOpenScreen = "comms"
        }
    }

    private fun maybeStartPendingWidgetListen() {
        if (!pendingWidgetListen) return
        pendingWidgetListen = false
        startVoiceListen()
    }

    private fun maybeApplyPendingOpenScreen() {
        val screen = pendingOpenScreen ?: return
        pendingOpenScreen = null

        if (screen == "timer" && pendingStartTimer) {
            pendingStartTimer = false
            if (::timerStore.isInitialized) {
                val entry = selectedTimer() ?: timerStore.all().firstOrNull()
                if (entry != null) {
                    timerSelectedId = entry.id
                    if (!entry.running) timerStore.start(entry.id, System.currentTimeMillis())
                }
            }
        }
        showScreen(screen)
    }

    private fun frameDrawable(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.BLACK)
        setStroke(dp(3), colorAccent())
        cornerRadius = dp(34).toFloat()
    }

    private fun buildShell() {

        glowFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050200"))

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#050200"))
                cornerRadius = dp(40).toFloat()
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        phoneFrame = FrameLayout(this).apply {
            background = frameDrawable()
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AgentTheme.background)
        }
        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        val statusBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = bottomBorder()
        }
        statusBarView = statusBar
        statusTitleView = monoText("NERV·OS 2.15", 12f, colorAccent())
        statusBar.addView(statusTitleView, LinearLayout.LayoutParams(0, -2, 1f))
        statusIconView = NervStatusIconView(this)
        statusBar.addView(statusIconView, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(6) })
        headerStatus = monoText("● LINK OK", 12f, colorDanger())
        statusBar.addView(headerStatus)
        statusTime = monoText("", 12f, colorAccent())
        statusBar.addView(statusTime)
        root.addView(statusBar)

        val contentFrame = FrameLayout(this)

        screenBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        contentFrame.addView(ScrollView(this).apply {
            isFillViewport = true

            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(screenBody)
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(contentFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = topBorder()
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            navScrollView = this
            addView(navBar)
        }, LinearLayout.LayoutParams(-1, -2))

        installPixelCanvas(phoneFrame)
        phoneFrame.addView(root, FrameLayout.LayoutParams(-1, -1))
        addScanlines()
        glowFrame.addView(phoneFrame, FrameLayout.LayoutParams(-1, -1))
        applyTheme()
        setContentView(glowFrame)
        val ticker = Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                val now = Date()
                statusTime?.text = timeFmtHHmm.format(now)

                val secondText = timeFmtHHmmss.format(now)
                if (secondText != lastClockSecond) {
                    lastClockSecond = secondText
                    clockView?.text = secondText
                }
                msView?.text = (now.time % 1000).toString().padStart(3, '0')
                statusIconView?.status = currentStatus()
                ticker.postDelayed(this, 50)
            }
        }
        ticker.post(tick)
    }

    private fun installPixelCanvas(frame: FrameLayout) {
        pixelCanvasView = PixelCanvasView(this).apply { running = appInForeground }
        frame.addView(pixelCanvasView, 0, FrameLayout.LayoutParams(-1, -1))
    }

    private fun revealPixelCanvas(view: View) {
        fun nearBlack(color: Int): Boolean = Color.alpha(color) > 0 &&
            Color.red(color) <= 5 && Color.green(color) <= 5 && Color.blue(color) <= 5
        fun reveal(drawable: android.graphics.drawable.Drawable?) {
            when (drawable) {
                is GradientDrawable -> {
                    val solid = drawable.color?.defaultColor
                    if (solid != null && nearBlack(solid)) drawable.setColor(Color.TRANSPARENT)
                }
                is android.graphics.drawable.ColorDrawable ->
                    if (nearBlack(drawable.color)) drawable.color = Color.TRANSPARENT
                is android.graphics.drawable.LayerDrawable ->
                    for (index in 0 until drawable.numberOfLayers) {
                        if (drawable.getId(index) != android.R.id.mask) reveal(drawable.getDrawable(index))
                    }
                is android.graphics.drawable.InsetDrawable -> reveal(drawable.drawable)
            }
        }

        if (view.tag != "nerv-clock-inset" && !view.isClickable) reveal(view.background?.mutate())
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) revealPixelCanvas(view.getChildAt(index))
        }
    }
    private fun addScanlines() {
        scanlinesView = ScanlinesView(this).apply { isClickable = false; isFocusable = false }
        phoneFrame.addView(scanlinesView, FrameLayout.LayoutParams(-1, -1))
        scanlinesView?.visibility = if (scanOn) View.VISIBLE else View.GONE
    }

    private fun currentStatus(): NervStatus {
        if (voiceListening || voiceConnector.state == VoiceConnector.VoiceState.LISTENING) return NervStatus.LISTENING
        if (chatBusy) return NervStatus.THINKING
        if (::agentBridge.isInitialized && agentBridge.isRunning()) return NervStatus.ACTING
        if (::agentBridge.isInitialized && agentBridge.providerConfig() == null) return NervStatus.OFFLINE
        if (::voiceConnector.isInitialized && voiceConnector.serviceActive()) return NervStatus.LISTENING
        return NervStatus.IDLE
    }

    private fun updateHeaderStatus() {

        val saver = if (::batterySaver.isInitialized && batterySaver.active) " · SAVER" else ""
        val damaged = armorDamaged.count { it }
        if (damaged > 0) {
            headerStatus.text = "● $damaged DAMAGED$saver"
            headerStatus.setTextColor(colorDanger())
        } else {
            headerStatus.text = "● LINK OK$saver"
            headerStatus.setTextColor(colorDanger())
        }
    }

    private fun showScreen(key: String) {
        locked = false
        current = if (screens.any { it.key == key }) key else "home"

        if (current != "map" && ::locationTracker.isInitialized) {
            locationTracker.stop()
            mapLocationStarted = false
        }
        screenBody.removeAllViews()
        clockView = null
        msView = null
        toxBars.clear()
        toxScreenBars.clear()
        toxScreenValueViews.clear()
        toxPurityView = null
        toxElapsedView = null
        toxReadoutView = null
        timerValueView = null
        timerBarView = null
        timerButtonView = null
        timerEmptyView = null
        timerRowViews.clear()
        mapGlobe = null
        mapCoordView = null
        mapAccuracyView = null
        mapStatusView = null
        mapRotView = null
        syncValueView = null
        syncSliderView = null
        syncLiveBoxView = null
        navBar.removeAllViews()
        screens.forEachIndexed { index, screen ->
            val isSelected = screen.key == current
            val isLast = index == screens.size - 1

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(8))
                background = if (isLast) {
                    GradientDrawable().apply {
                        setColor(if (isSelected) colorAccent() else AgentTheme.background)
                    }
                } else {
                    InsetDrawable(
                        GradientDrawable().apply {
                            setColor(if (isSelected) colorAccent() else AgentTheme.background)
                            setStroke(dp(1), AgentTheme.muted)
                        },
                        -dp(1), -dp(1), 0, -dp(1)
                    )
                }
                isClickable = true
                isFocusable = true
                contentDescription = "${screen.label} screen"
                setOnClickListener { showScreen(screen.key) }
            }
            val fg = if (isSelected) AgentTheme.background else colorAccent()
            tab.addView(minchoText(screen.glyph, 17f, fg).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(2) }
            })
            tab.addView(monoText(screen.label, 11f, fg).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
            })
            navBar.addView(tab, LinearLayout.LayoutParams(dp(64), -2))
        }

        val selectedIndex = screens.indexOfFirst { it.key == current }.coerceAtLeast(0)
        navScrollView?.post {
            val tab = navBar.getChildAt(selectedIndex)
            if (tab != null) navScrollView?.scrollTo((tab.left - dp(64)).coerceAtLeast(0), 0)
        }
        updateHeaderStatus()
        when (current) {
            "home" -> showHome()
            "magi" -> showMagi()
            "sync" -> showSync()
            "armor" -> showArmor()
            "comms" -> showComms()
            "tox" -> showTox()
            "timer" -> showTimer()
            "map" -> showMap()
            "setup" -> showSetup()
            "clasp" -> Unit
        }
        revealPixelCanvas(root)
    }

    private fun showHome() {

        maybeShowFirstRunTutorial()

        maybeShowDailyBriefing()
        screenBody.addView(homeTitle("現在時刻", "ACTIVE TIME DISPLAY", "SYS: NORMAL"))
        val clockFrame = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(colorDanger(), colorAccent(), AgentTheme.green)
            ).apply { setStroke(dp(2), colorAccent()) }
        }

        val clockInner = FrameLayout(this).apply {
            tag = "nerv-clock-inset"
            setBackgroundColor(AgentTheme.background)
        }
        val clockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val clock = minchoText("", 50f, colorAccent()).apply {
            setShadowLayer(12f, 0f, 0f, colorAccent())
        }
        clockView = clock
        msView = monoText("000", 20f, colorAccentSecondary())
        clockRow.addView(clock)
        clockRow.addView(msView)
        clockInner.addView(clockRow, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })
        clockFrame.addView(clockInner, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
        screenBody.addView(clockFrame, LinearLayout.LayoutParams(-1, dp(96)).apply { bottomMargin = dp(12) })

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("STOP", "SLOW", "NORMAL", "RACING").forEachIndexed { index, mode ->
            modeRow.addView(homeModeButton(mode, index == modeIndex) {
                modeIndex = index
                showScreen("home")
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = if (index == 3) 0 else dp(6)
            })
        }
        screenBody.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val wakeBox = borderedPanel()
        wakeBox.addView(boxHeading("HEY MAGI — VOICE COMMANDS"))
        wakeBox.addView(monoText(
            if (::wakeWord.isInitialized) wakeWord.statusLine(voiceListening) else "HEY MAGI: OFF",
            12f, if (voiceListening) colorDanger() else colorAccent()))
        val wakeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        wakeRow.addView(shellButton(
            if (voiceListening) "● STOP LISTENING"
            else "HEY MAGI ▸ TAP TO TALK"
        ) {
            if (voiceListening) {
                startVoiceListen()
            } else if (::wakeWord.isInitialized && wakeWord.enabled) {
                startVoiceListen()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("HEY MAGI IS OFF")
                    .setMessage("Voice commands are switched off, so nothing is listening. Switch HEY MAGI on below — or in SETUP > VOICE COMMANDS — and then tap HEY MAGI to talk. Nothing ever listens in the background: a tap is what starts it, and a tap stops it.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("SWITCH ON") { _, _ ->
                        wakeWord.enabled = true
                        showScreen("home")
                    }
                    .show()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        wakeRow.addView(shellButton(
            if (::wakeWord.isInitialized && wakeWord.enabled) "HEY MAGI: ON — TAP TO TURN OFF" else "HEY MAGI: OFF — TAP TO TURN ON"
        ) {
            wakeWord.enabled = !wakeWord.enabled
            showScreen("home")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        wakeBox.addView(wakeRow)
        screenBody.addView(wakeBox)
        val gridCols = if (isDualPane()) 4 else 2
        val grid = GridLayout(this).apply { columnCount = gridCols }
        screens.drop(1).filter { it.key != "clasp" }.forEachIndexed { index, screen ->
            val tile = ClippedTileLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                setPadding(dp(8), dp(12), dp(8), dp(12))
                background = GradientDrawable().apply {
                    setColor(AgentTheme.background)
                    setStroke(dp(2), colorAccent())
                }
                isClickable = true
                isFocusable = true

                contentDescription = "${screen.label} screen"
                setOnClickListener { showScreen(screen.key) }
            }
            tile.addView(minchoText(screen.glyph, 28f, colorAccent()))
            tile.addView(monoText(screen.label, 11f, colorAccent()))

            val lastColumn = (index + 1) % gridCols == 0
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(72)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, if (lastColumn) 0 else dp(10), dp(10))
            })
        }
        screenBody.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val toxicity = borderedPanel()

        toxicity.addView(boxHeading("MENTAL TOXICITY LEVEL"))
        listOf("00 FIRST", "01 THIRD", "02 SECOND").forEachIndexed { i, name ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

            row.addView(monoText(name.take(2), 18f, colorDanger()).apply { minWidth = dp(26) })
            row.addView(monoText(name.drop(3), 11f, colorAccent()),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            val bar = StripedBarView(this).apply { setValue(toxValues[i]) }
            toxBars.add(bar)
            row.addView(bar, LinearLayout.LayoutParams(0, dp(16), 1f).apply { marginStart = dp(8) })
            toxicity.addView(row)
        }
        screenBody.addView(toxicity)

        screenBody.addView(shellButton("DAILY BRIEFING") { showBriefingDialog() })

        calendarLink.nextEvent()?.let { event ->
            screenBody.addView(monoText("NEXT: ${CalendarLink.lineFor(event)}", 12f, colorAccentSecondary()))
        }
    }

    private fun showMagi() {
        screenBody.addView(title("提訴決議", "CODE : 239", "FILE: MAGI_SYS"))
        val access = borderedPanel()
        access.addView(boxHeading("ACCESS MODE: SUPERUSER"))
        access.addView(shellButton("MOTION: ${magiEngine.motion} ▸") {
            magiEngine.cycleMotion()
            showScreen("magi")
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2) })
        screenBody.addView(access)

        if (magiEngine.offlineMode.enabled) {
            screenBody.addView(monoText("OFFLINE MODE — LOCAL ONLY · cloud providers blocked, LOCAL nodes answer on this phone", 12f, colorDanger()).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
            })
        }

        val wrap = android.widget.RelativeLayout(this)

        fun nodeBox(name: String, voteIndex: Int, clipMode: Int, voteTopMargin: Int): LinearLayout =
            MagiNodeLayout(this, clipMode).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                val ok = magiEngine.votes[voteIndex] == 1
                val col = if (ok) AgentTheme.signal else colorDanger()
                background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(4), col) }
                addView(monoText(name, 15f, col).apply {
                    gravity = Gravity.CENTER
                    setShadowLayer(8f, 0f, 0f, col)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(minchoText(if (ok) "承認" else "否定", 30f, col).apply {
                    gravity = Gravity.CENTER
                    setShadowLayer(8f, 0f, 0f, col)
                    background = GradientDrawable().apply {
                        setColor(AgentTheme.background)
                        setStroke(dp(2), col)
                    }
                    setPadding(0, dp(4), 0, dp(4))
                    layoutParams = LinearLayout.LayoutParams(dp(133), -2).apply { topMargin = dp(voteTopMargin) }
                })
                isClickable = true
                setOnClickListener { magiEngine.toggleVote(voteIndex); showScreen("magi") }
            }
        val top = nodeBox("BALTHASAR·2", 1, 0, 10)
        wrap.addView(top, android.widget.RelativeLayout.LayoutParams(dp(170), -2).apply {
            addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL); topMargin = dp(8)
        })
        val left = nodeBox("CASPER·3", 2, 1, 24)
        wrap.addView(left, android.widget.RelativeLayout.LayoutParams(dp(170), -2).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            leftMargin = dp(8); bottomMargin = dp(8)
        })
        val right = nodeBox("MELCHIOR·1", 0, 2, 24)
        wrap.addView(right, android.widget.RelativeLayout.LayoutParams(dp(170), -2).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            rightMargin = dp(8); bottomMargin = dp(8)
        })

        val hub = minchoText("MAGI", 18f, AgentTheme.signal).apply {
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 0f, AgentTheme.signal)
            background = GradientDrawable().apply {
                setColor(AgentTheme.background)
                setStroke(dp(3), AgentTheme.signal)
            }
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        wrap.addView(hub, android.widget.RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
        })
        hub.translationY = -dp(10).toFloat()
        screenBody.addView(wrap, LinearLayout.LayoutParams(-1, dp(330)).apply { topMargin = dp(4); bottomMargin = dp(8) })

        val yes = magiEngine.votes.sum()
        val approved = yes >= 2
        val resultColor = if (approved) AgentTheme.signal else colorDanger()
        val result = minchoText(
            if (approved) "RESOLUTION APPROVED  $yes/3" else "RESOLUTION DENIED  $yes/3",
            18f, resultColor
        ).apply {
            gravity = Gravity.CENTER

            setShadowLayer(8f, 0f, 0f, resultColor)
            background = GradientDrawable().apply {
                setColor(AgentTheme.background)
                setStroke(dp(3), resultColor)
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        screenBody.addView(result, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(shellButton("RANDOMIZE") {
            magiEngine.randomizeVotes()
            showScreen("magi")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        row.addView(shellButton("ALL 承認") {
            magiEngine.allApprove()
            showScreen("magi")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        screenBody.addView(row)

        screenBody.addView(shellButton("NODE SETUP ▸") { showScreen("setup") },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        screenBody.addView(shellButton("HEAR THE NODES ▸") { showHearNodes() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        screenBody.addView(monoText("Votes live in MagiEngine — the lock screen runs a real vote on these same nodes. Tap a node in CHAT and the debate runs — draft, cross-examine, synthesis — with a deliberation log.", 12f, colorAccentSecondary()), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        if (isDualPane()) {
            showDualPaneSplit()
        }

        val memoryPanel = borderedPanel()
        memoryPanel.addView(boxHeading("MAGI MEMORY — ${magiMemory.count()} REMEMBERED"))
        val recentMemories = magiMemory.recent(3)
        if (recentMemories.isEmpty()) {
            memoryPanel.addView(monoText("Nothing remembered yet — ask MAGI something in CHAT, or let the lock vote run, and it shows up here.", 12f, colorAccent()))
        } else {
            recentMemories.forEach { entry ->
                memoryPanel.addView(monoText("${magiMemory.timeLabel(entry)} — ${entry.title}", 12f, colorAccent()))
                memoryPanel.addView(monoText("  ${entry.decision}", 11f, colorAccentSecondary()))
            }
        }
        val memoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        memoryRow.addView(shellButton("SEARCH MEMORY") { showMemorySearchDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        memoryRow.addView(shellButton("VIEW ALL") { showMemoryListDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        memoryRow.addView(shellButton("CLEAR") { showClearMemoryDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        memoryPanel.addView(memoryRow)
        screenBody.addView(memoryPanel)

        val historyPanel = borderedPanel()
        historyPanel.addView(boxHeading("DEBATE HISTORY — ${debateHistory.count()} SAVED"))
        val recentDebates = debateHistory.recent(3)
        if (recentDebates.isEmpty()) {
            historyPanel.addView(monoText("No debates yet — ask MAGI something in CHAT, or let a routine run, and it shows up here.", 12f, colorAccent()))
        } else {
            recentDebates.forEach { record ->
                historyPanel.addView(monoText("${debateHistory.timeLabel(record)} — ${record.question}", 12f, colorAccent()))
                historyPanel.addView(monoText("  ${record.decision} · winner ${record.winner}", 11f, colorAccentSecondary()))
            }
        }
        val historyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        historyRow.addView(shellButton("SEARCH HISTORY") { showDebateHistorySearchDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        historyRow.addView(shellButton("VIEW ALL") { showDebateHistoryListDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        historyRow.addView(shellButton("CLEAR") { showClearDebateHistoryDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        historyPanel.addView(historyRow)
        screenBody.addView(historyPanel)
    }

    private fun showDualPaneSplit() {
        val split = foldSupport.dualSplit
        val dual = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val debatePanel = borderedPanel()
        debatePanel.addView(boxHeading("MAGI DEBATE"))
        val yes = magiEngine.votes.sum()
        debatePanel.addView(monoText(
            if (yes >= 2) "RESOLUTION APPROVED  $yes/3" else "RESOLUTION DENIED  $yes/3",
            12f, if (yes >= 2) AgentTheme.signal else colorDanger()))
        val logLines = magiEngine.lastLog().takeLast(6)
        if (logLines.isEmpty()) {
            debatePanel.addView(monoText("No debate yet — ask MAGI something in CHAT.", 12f, colorAccent()))
        } else {
            logLines.forEach { line ->
                debatePanel.addView(monoText(line.take(120), 11f, colorAccent()))
            }
        }
        debatePanel.addView(shellButton("OPEN CHAT") { showScreen("comms") })
        dual.addView(debatePanel, LinearLayout.LayoutParams(0, -2, split).apply { marginEnd = dp(4) })

        val divider = View(this).apply {
            background = GradientDrawable().apply {
                setColor(colorAccent())
                cornerRadius = dp(2).toFloat()
            }
            contentDescription = "Drag left or right to resize the debate and phone panes"
            isClickable = true
            isFocusable = true
        }
        dual.addView(divider, LinearLayout.LayoutParams(dp(10), -1).apply {
            marginStart = dp(2); marginEnd = dp(2)
        })

        val phonePanel = borderedPanel()
        phonePanel.addView(boxHeading("PHONE VIEW — LIVE"))
        appConnector.checkAll().forEach { link ->
            val stateColor = when (link.state) {
                AppConnector.LinkState.OK, AppConnector.LinkState.RUNNING -> AgentTheme.signal
                AppConnector.LinkState.OFFLINE -> colorAccentSecondary()
                else -> colorDanger()
            }
            phonePanel.addView(monoText("${link.name}: ${link.state.name} — ${link.detail.take(90)}", 11f, stateColor))
        }
        val latestTask = phoneTaskStore.latest()
        if (latestTask == null) {
            phonePanel.addView(monoText("No phone task yet — ask MAGI something in CHAT and a decision that needs the phone shows up here.", 11f, colorAccent()))
        } else {
            phonePanel.addView(monoText("LATEST TASK: ${latestTask.objective.take(100)}", 11f, colorAccent()))
            val taskColor = when (latestTask.status) {
                PhoneTaskStore.TaskStatus.DONE, PhoneTaskStore.TaskStatus.RUNNING -> AgentTheme.signal
                PhoneTaskStore.TaskStatus.PROPOSED -> colorAccentSecondary()
                else -> colorDanger()
            }
            phonePanel.addView(monoText("STATUS: ${latestTask.status.name}", 11f, taskColor))
            if (latestTask.status == PhoneTaskStore.TaskStatus.RUNNING) {
                phonePanel.addView(monoText(agentBridge.status(), 10f, colorAccentSecondary()))
            } else if (latestTask.result.isNotBlank()) {
                phonePanel.addView(monoText("ANSWER: ${latestTask.result.take(120)}", 10f, colorAccentSecondary()))
            }
            val taskRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            when (latestTask.status) {
                PhoneTaskStore.TaskStatus.PROPOSED -> {
                    taskRow.addView(shellButton("APPROVE") { approvePhoneTask(latestTask) },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
                    taskRow.addView(shellButton("DENY") {
                        phoneTaskStore.markDenied(latestTask.id)
                        showScreen("magi")
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
                }
                PhoneTaskStore.TaskStatus.RUNNING -> {
                    taskRow.addView(shellButton("STOP") {
                        agentBridge.stop()
                        phoneTaskStore.markStopped(latestTask.id)
                        showScreen("magi")
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
                PhoneTaskStore.TaskStatus.DONE, PhoneTaskStore.TaskStatus.FAILED, PhoneTaskStore.TaskStatus.STOPPED -> {
                    if (latestTask.result.isNotBlank()) {
                        taskRow.addView(shellButton("VIEW ANSWER") { showPhoneTaskResultDialog(latestTask) },
                            LinearLayout.LayoutParams(0, -2, 1f))
                    }
                }
                PhoneTaskStore.TaskStatus.DENIED -> {   }
            }
            phonePanel.addView(taskRow)
        }
        phonePanel.addView(shellButton("OPEN CHAT — PHONE TASKS") { showScreen("comms") })
        dual.addView(phonePanel, LinearLayout.LayoutParams(0, -2, 1f - split).apply { marginStart = dp(4) })

        screenBody.addView(dual)
        val splitLabel = monoText(splitLabelText(split), 11f, colorAccentSecondary())
        screenBody.addView(splitLabel)

        divider.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dual.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    splitFromTouch(dual, event.rawX)?.let { fraction ->
                        applySplit(dual, debatePanel, phonePanel, fraction)
                        splitLabel.text = splitLabelText(fraction)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    splitFromTouch(dual, event.rawX)?.let { fraction ->
                        foldSupport.dualSplit = fraction
                        applySplit(dual, debatePanel, phonePanel, fraction)
                        splitLabel.text = splitLabelText(fraction)
                    }
                    dual.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun splitFromTouch(row: LinearLayout, rawX: Float): Float? {
        val width = row.width.toFloat()
        if (width <= 0f) return null
        val loc = IntArray(2)
        row.getLocationOnScreen(loc)
        return ((rawX - loc[0]) / width).coerceIn(FoldSupport.MIN_SPLIT, FoldSupport.MAX_SPLIT)
    }

    private fun applySplit(row: LinearLayout, left: LinearLayout, right: LinearLayout, fraction: Float) {
        (left.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.weight = fraction
            left.layoutParams = it
        }
        (right.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.weight = 1f - fraction
            right.layoutParams = it
        }
        row.requestLayout()
    }

    private fun splitLabelText(split: Float): String {
        val debate = (split * 100).toInt()
        return "Split: $debate% debate / ${100 - debate}% phone — drag the bar between them to change it. NERV remembers the split."
    }

    private fun showHearNodes() {
        val log = magiEngine.lastLog()
        var first = true
        var heard = 0
        MagiEngine.NodeId.values().forEach { id ->
            val name = magiEngine.nodeNames[id] ?: id.name
            val line = log.firstOrNull { it.startsWith("$name:") }
                ?.substringAfter(":")?.trim().orEmpty().take(280)
            if (line.isNotBlank()) {
                magiVoice.speak(id, line, flush = first)
                first = false
                heard++
            }
        }
        if (heard == 0) {
            AlertDialog.Builder(this)
                .setTitle("NOTHING TO HEAR YET")
                .setMessage("No debate yet — ask MAGI something in CHAT first, then come back and hear the nodes.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showMemorySearchDialog() {
        val input = EditText(this).apply {
            hint = "words to find"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("SEARCH MEMORY")
            .setMessage("Searches what MAGI remembers — past debates and decisions, on this phone only.")
            .setView(input)
            .setPositiveButton("SEARCH") { _, _ ->
                val hits = magiMemory.search(input.text.toString())
                val text = if (hits.isEmpty()) {
                    "Nothing remembered matches \"${input.text}\"."
                } else {
                    hits.take(10).joinToString("\n\n") { entry ->
                        "${magiMemory.timeLabel(entry)} — ${entry.title}\n${entry.decision}: ${entry.detail}"
                    }
                }
                val results = AlertDialog.Builder(this)
                    .setTitle("MEMORY — ${hits.size} MATCH${if (hits.size == 1) "" else "ES"}")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .create()
                results.show()
                results.window?.setBackgroundDrawable(
                    GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
                )
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showMemoryListDialog() {
        val items = magiMemory.recent(10)
        val text = if (items.isEmpty()) {
            "Nothing remembered yet."
        } else {
            items.joinToString("\n\n") { entry ->
                "${magiMemory.timeLabel(entry)} — ${entry.title}\n${entry.decision}: ${entry.detail}"
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("MAGI MEMORY — ${magiMemory.count()} REMEMBERED")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showClearMemoryDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("CLEAR MEMORY?")
            .setMessage("Forget all ${magiMemory.count()} remembered debates and decisions? MAGI will start fresh. This cannot be undone.")
            .setPositiveButton("CLEAR") { _, _ ->
                magiMemory.clear()
                showScreen("magi")
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showDebateHistorySearchDialog() {
        val input = EditText(this).apply {
            hint = "words to find"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("SEARCH DEBATE HISTORY")
            .setMessage("Searches every saved debate — question, answer, winning node and final vote — on this phone only.")
            .setView(input)
            .setPositiveButton("SEARCH") { _, _ ->
                val hits = debateHistory.search(input.text.toString())
                val text = if (hits.isEmpty()) {
                    "No saved debate matches \"${input.text}\"."
                } else {
                    hits.take(10).joinToString("\n\n") { record ->
                        "${debateHistory.timeLabel(record)} — ${record.question}\n${record.decision} · winner ${record.winner}\n${record.answerExcerpt}"
                    }
                }
                val results = AlertDialog.Builder(this)
                    .setTitle("DEBATES — ${hits.size} MATCH${if (hits.size == 1) "" else "ES"}")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .create()
                results.show()
                results.window?.setBackgroundDrawable(
                    GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
                )
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showDebateHistoryListDialog() {
        val items = debateHistory.recent(10)
        val text = if (items.isEmpty()) {
            "No debates saved yet."
        } else {
            items.joinToString("\n\n") { record ->
                "${debateHistory.timeLabel(record)} — ${record.question}\n${record.decision} · winner ${record.winner}\n${record.answerExcerpt}"
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("DEBATE HISTORY — ${debateHistory.count()} SAVED")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showClearDebateHistoryDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("CLEAR DEBATE HISTORY?")
            .setMessage("Forget all ${debateHistory.count()} saved debates? The chat messages themselves stay — only this history list is cleared. This cannot be undone.")
            .setPositiveButton("CLEAR") { _, _ ->
                debateHistory.clear()
                showScreen("magi")
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showSync() {
        screenBody.addView(title("同調", "PSYCHOGRAPHIC DISPLAY", "Phase 4"))
        val psych = PsychographView(this) { syncRatio }
        psych.background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), colorAccent()) }
        screenBody.addView(psych, LinearLayout.LayoutParams(-1, dp(200)).apply { bottomMargin = dp(12) })

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        modeRow.addView(monoText(if (syncLive) "● LIVE — RATIO FOLLOWS THIS PHONE" else "○ MANUAL — SLIDER SETS THE RATIO", 12f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        modeRow.addView(shellButton(if (syncLive) "SWITCH TO MANUAL" else "SWITCH TO LIVE") {
            syncLive = !syncLive
            if (syncLive) {

                val signals = syncSignals ?: syncMonitor.sample()
                syncSignals = signals
                syncLiveValue = syncMonitor.ratioFrom(signals)
                syncRatio = syncLiveValue
            } else {
                syncManualRatio = syncRatio
                syncRatio = syncManualRatio
            }
            showScreen("sync")
        })
        screenBody.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val syncRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        syncRow.addView(monoText("SYNC RATIO", 13f, colorAccent()))
        val syncValue = monoText("$syncRatio%", 13f, colorAccent())
        syncValueView = syncValue
        val slider = SeekBar(this).apply {
            max = 100
            progress = syncRatio
            isEnabled = !syncLive
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!syncLive && fromUser) {
                        syncManualRatio = p
                        syncRatio = p
                        syncValue.text = "$syncRatio%"
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        syncSliderView = slider
        syncRow.addView(slider, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
        syncRow.addView(syncValue)
        screenBody.addView(syncRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val liveBox = monoText("", 12f, colorAccentSecondary())
        syncLiveBoxView = liveBox
        screenBody.addView(liveBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        updateSyncViews()

        val eeg = EEGView(this) { syncChannelAmps() }
        eeg.background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), colorAccent()) }
        screenBody.addView(eeg, LinearLayout.LayoutParams(-1, dp(300)))
        screenBody.addView(monoText("In LIVE mode each trace moves with its own signal: TEMPORAL follows CPU load, AMYGDALA follows battery drain, HIPPOCAMPUS follows memory pressure, PARIETAL follows the network, MOTOR follows charging, SENSORY follows the blend. MANUAL keeps the HTML slider behaviour.", 12f, colorAccentSecondary()), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun updateSyncViews() {
        syncValueView?.text = "$syncRatio%"
        if (syncLive) syncSliderView?.progress = syncRatio
        syncSliderView?.isEnabled = !syncLive
        val s = syncSignals
        syncLiveBoxView?.text = if (s == null) {
            if (syncLive) "READING THIS PHONE…" else "MANUAL — THE RATIO FOLLOWS THE SLIDER ABOVE."
        } else {
            val battery = if (s.batteryPct >= 0) "${s.batteryPct}%" else "?"
            val charge = if (s.charging) " · CHARGING" else ""
            val cpu = if (s.cpuLoad >= 0f) "${(s.cpuLoad * 100).toInt()}%" else "?"
            val mem = if (s.memPressure >= 0f) "${(s.memPressure * 100).toInt()}%" else "?"
            "BATTERY $battery$charge · CPU LOAD $cpu · MEMORY PRESSURE $mem · NETWORK ${s.network}"
        }
    }

    private fun syncChannelAmps(): FloatArray {
        val s = syncSignals ?: return FloatArray(6) { 1f }
        return syncMonitor.channelAmps(s)
    }

    private fun showArmor() {
        val experimentsPanel = borderedPanel()
        experimentsPanel.addView(boxHeading("PHYSICAL PHONE EXPERIMENTS"))
        experimentsPanel.addView(monoText("The phyphox sensor lab, built into NERV. Live graphs, recording and exports; experiments depend on this phone's sensors.", 12f, colorAccent()))
        experimentsPanel.addView(shellButton("OPEN EXPERIMENTS / PHYPHOX") {
            startActivity(Intent(this, de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity::class.java))
        })
        experimentsPanel.addView(monoText("Powered by phyphox 1.2.1 / RWTH Aachen University. GPL-3.0 source and credits included.", 10f, colorAccentSecondary()))
        screenBody.addView(experimentsPanel)
        screenBody.addView(title("装甲", "ARMOR PLATE", "PROTECTION LEVEL"))
        for (i in 0 until 6) {
            val damaged = armorDamaged[i]
            val number = String.format(Locale.getDefault(), "%02d", i + 13)
            val plateColor = if (damaged) colorDanger() else colorAccent()
            val plate = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    setColor(AgentTheme.background)
                    setStroke(dp(2), plateColor)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    armorDamaged[i] = !armorDamaged[i]
                    updateHeaderStatus()
                    showScreen("armor")
                }
            }
            val stripe = View(this).apply {
                setBackgroundColor(if (damaged) colorDanger() else AgentTheme.green)
            }
            plate.addView(stripe, LinearLayout.LayoutParams(dp(6), -1))
            plate.addView(minchoText(number, 34f, if (damaged) colorDanger() else AgentTheme.signal).apply {
                minWidth = dp(48)
                gravity = Gravity.CENTER
            })
            val middle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            middle.addView(monoText("PARTITION No. $number", 16f, plateColor))
            middle.addView(monoText(
                "ARMOR PLATE · ${if (damaged) "INTEGRITY 12%" else "INTEGRITY 100%"}",
                10f, colorAccentSecondary()
            ))
            plate.addView(middle, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            if (damaged) {
                val stamp = minchoText("DAMAGED\nCONDITION:RED", 12f, colorDanger()).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), colorDanger()) }
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                }
                plate.addView(stamp)

                ObjectAnimator.ofFloat(stamp, "alpha", 1f, 0.35f).apply {
                    duration = 500
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            screenBody.addView(plate, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(shellButton("STRIKE RANDOM") {
            armorDamaged[(Math.random() * 6).toInt()] = true
            updateHeaderStatus()
            showScreen("armor")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        row.addView(shellButton("REPAIR ALL") {
            for (i in armorDamaged.indices) armorDamaged[i] = false
            updateHeaderStatus()
            showScreen("armor")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        screenBody.addView(row)
        screenBody.addView(monoText("Tap a plate to toggle it. The tools MAGI may use are switched in SETUP > PACKS — a pack holds the tools, and loading it switches them on.", 12f, colorAccentSecondary()), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        showNotesSection()

        showReportsSection()
    }

    private fun showNotesSection() {
        val all = noteStore.all()
        screenBody.addView(title("手帳", "NOTES", "${all.size} NOTE${if (all.size == 1) "" else "S"} · ON THIS PHONE ONLY"))

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf("ALL") + noteStore.allFolders()).forEach { folder ->
            val label = if (folder == "ALL") "ALL (${all.size})" else "$folder (${noteStore.countIn(folder)})"
            val selected = notesFolderFilter == folder
            chips.addView(shellButton(if (selected) "▶ $label" else label) {
                notesFolderFilter = folder
                notesSearchQuery = null
                showScreen("armor")
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        screenBody.addView(HorizontalScrollView(this).apply {
            addView(chips)
            isHorizontalScrollBarEnabled = false
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val searchInput = EditText(this).apply {
            hint = "SEARCH NOTES"
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(notesSearchQuery ?: "")
            htmlInputStyle(this)
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        val runNotesSearch = {
            notesSearchQuery = searchInput.text.toString().trim().takeIf { it.isNotEmpty() }
            showScreen("armor")
        }
        searchRow.addView(shellButton("SEARCH") { runNotesSearch() }, LinearLayout.LayoutParams(dp(84), -2))
        if (notesSearchQuery != null) {
            searchRow.addView(shellButton("CLEAR") {
                notesSearchQuery = null
                showScreen("armor")
            }, LinearLayout.LayoutParams(dp(70), -2).apply { marginStart = dp(6) })
        }
        screenBody.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runNotesSearch(); true } else false
        }

        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionsRow.addView(shellButton("＋ NEW NOTE") { showNewNoteDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        actionsRow.addView(shellButton("FOLDERS") { showManageNoteFoldersDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        screenBody.addView(actionsRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val query = notesSearchQuery
        val shown: List<Pair<NoteStore.Note, String?>> = if (!query.isNullOrBlank()) {
            val hits = noteStore.search(query)
            screenBody.addView(monoText(
                if (hits.isEmpty()) "NO MATCHES FOR \"$query\" — TRY FEWER WORDS"
                else "${hits.size} HIT${if (hits.size == 1) "" else "S"} FOR \"$query\" — TAP TO OPEN",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            hits.mapNotNull { hit ->
                noteStore.all().firstOrNull { it.id == hit.noteId }?.let { it to hit.snippet }
            }
        } else {
            noteStore.notesIn(notesFolderFilter).map { it to null }
        }
        if (shown.isEmpty() && query.isNullOrBlank()) {
            screenBody.addView(monoText(
                if (notesFolderFilter == "ALL") "NO NOTES YET — NEW NOTE TO SAVE ONE, OR ASK MAGI TO SAVE ONE."
                else "NO NOTES IN $notesFolderFilter YET.",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        shown.forEach { (note, snippet) ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(AgentTheme.background)
                    setStroke(dp(2), colorAccent())
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { showNoteDialog(note) }
            }
            box.addView(monoText("${note.folder} · ${note.updatedAt}", 10f, colorAccentSecondary()))
            box.addView(monoText(note.title, 13f, colorAccent()))
            val preview = snippet ?: note.body.replace(Regex("\\s+"), " ").trim().take(120)
            box.addView(monoText(preview, 12f, colorAccentSecondary()))
            screenBody.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        screenBody.addView(monoText("Notes live on this phone only. MAGI can save and read them with the notes tool — ask it to save a note, or to read one back.", 12f, colorAccentSecondary()))
    }

    private fun showNewNoteDialog() {
        val titleInput = EditText(this).apply {
            hint = "Title (blank = first words of the note)"
            maxLines = 1
            AgentTheme.style(this)
        }
        val bodyInput = EditText(this).apply {
            hint = "Write the note…"
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            AgentTheme.style(this)
        }
        val folderInput = EditText(this).apply {
            hint = "Folder (blank = ${if (notesFolderFilter == "ALL") NoteStore.DEFAULT_FOLDER else notesFolderFilter})"
            maxLines = 1
            setText(if (notesFolderFilter == "ALL") "" else notesFolderFilter)
            AgentTheme.style(this)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(monoText("TITLE", 11f, colorAccentSecondary()))
            addView(titleInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("NOTE", 11f, colorAccentSecondary()))
            addView(bodyInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("FOLDER", 11f, colorAccentSecondary()))
            addView(folderInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("Folders: ${noteStore.allFolders().joinToString(", ")}", 11f, colorAccentSecondary()))
        }
        val dialog = AlertDialog.Builder(this).setTitle("New note")
            .setMessage("Saved on this phone only. MAGI can read it back with the notes tool.")
            .setView(fields).setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val folder = folderInput.text.toString().trim()
                    .ifBlank { if (notesFolderFilter == "ALL") "" else notesFolderFilter }
                val error = noteStore.add(folder, titleInput.text.toString(), bodyInput.text.toString())
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not save the note").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    dialog.dismiss()
                    if (current == "armor") showScreen("armor")
                }
            }
        }
        dialog.show()
    }

    private fun showNoteDialog(note: NoteStore.Note) {
        val titleInput = EditText(this).apply {
            hint = "Title"
            maxLines = 1
            setText(note.title)
            AgentTheme.style(this)
        }
        val bodyInput = EditText(this).apply {
            maxLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(note.body)
            AgentTheme.style(this)
        }
        val folderInput = EditText(this).apply {
            hint = "Folder, e.g. MAGI"
            maxLines = 1
            setText(note.folder)
            AgentTheme.style(this)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(monoText("TITLE", 11f, colorAccentSecondary()))
            addView(titleInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("NOTE", 11f, colorAccentSecondary()))
            addView(bodyInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("FOLDER", 11f, colorAccentSecondary()))
            addView(folderInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("Saved ${note.createdAt} · Folders: ${noteStore.allFolders().joinToString(", ")}", 11f, colorAccentSecondary()))
        }
        val dialog = AlertDialog.Builder(this).setTitle(note.title)
            .setView(fields).setNegativeButton("CANCEL", null)
            .setNeutralButton("DELETE", null)
            .setPositiveButton("SAVE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = noteStore.update(note.id, folderInput.text.toString(), titleInput.text.toString(), bodyInput.text.toString())
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not save the note").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    dialog.dismiss()
                    if (current == "armor") showScreen("armor")
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${note.title}\"?")
                    .setMessage("This cannot be undone.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ ->
                        noteStore.delete(note.id)
                        dialog.dismiss()
                        if (current == "armor") showScreen("armor")
                    }
                    .show()
            }
        }
        dialog.show()
    }

    private fun showManageNoteFoldersDialog() {
        var managerDialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        noteStore.allFolders().forEach { folder ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val count = noteStore.countIn(folder)
            row.addView(monoText("$folder ($count)", 13f, colorAccent()),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })

            row.addView(shellButton("RENAME") { managerDialog?.dismiss(); showRenameNoteFolderDialog(folder) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            row.addView(shellButton("DELETE") { managerDialog?.dismiss(); showDeleteNoteFolderDialog(folder) },
                LinearLayout.LayoutParams(-2, -2))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        list.addView(monoText("GENERAL cannot be renamed or deleted. A folder can only be deleted when it holds no notes.", 11f, colorAccentSecondary()))
        managerDialog = AlertDialog.Builder(this).setTitle("Note folders")
            .setView(list).setNegativeButton("CLOSE", null)
            .setPositiveButton("＋ NEW FOLDER", null).create()
        managerDialog.setOnShowListener {
            managerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                managerDialog.dismiss()
                showNewNoteFolderDialog()
            }
        }
        managerDialog.show()
    }

    private fun showNewNoteFolderDialog() {
        val input = EditText(this).apply {
            hint = "Folder name, e.g. MAGI"
            maxLines = 1
            AgentTheme.style(this)
        }
        val dialog = AlertDialog.Builder(this).setTitle("New folder")
            .setView(input.apply { setPadding(dp(24), dp(12), dp(24), dp(12)) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = noteStore.addFolder(input.text.toString())
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not create the folder").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    dialog.dismiss()
                    if (current == "armor") showScreen("armor")
                    showManageNoteFoldersDialog()
                }
            }
        }
        dialog.show()
    }

    private fun showRenameNoteFolderDialog(folder: String) {
        val input = EditText(this).apply {
            hint = "New name"
            maxLines = 1
            setText(folder)
            AgentTheme.style(this)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Rename $folder")
            .setMessage("Every note in $folder moves to the new name with it.")
            .setView(input.apply { setPadding(dp(24), dp(12), dp(24), dp(12)) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("RENAME", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = noteStore.renameFolder(folder, input.text.toString())
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not rename the folder").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    if (notesFolderFilter == folder) {

                        notesFolderFilter = noteStore.allFolders()
                            .firstOrNull { it.equals(input.text.toString(), ignoreCase = true) } ?: "ALL"
                    }
                    dialog.dismiss()
                    if (current == "armor") showScreen("armor")
                    showManageNoteFoldersDialog()
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteNoteFolderDialog(folder: String) {
        val held = noteStore.countIn(folder)
        if (held > 0) {
            AlertDialog.Builder(this).setTitle("Cannot delete $folder")
                .setMessage("That folder still holds $held note${if (held == 1) "" else "s"} — move or delete them first.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Delete folder \"$folder\"?")
            .setMessage("The folder is empty, so no note is lost.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                val error = noteStore.deleteFolder(folder)
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not delete the folder").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    if (notesFolderFilter == folder) notesFolderFilter = "ALL"
                    if (current == "armor") showScreen("armor")
                    showManageNoteFoldersDialog()
                }
            }
            .show()
    }

    private fun showReportsSection() {
        val reports = reportExporter.listReports()
        screenBody.addView(title("報告書", "REPORTS", "${reports.size} SAVED · ON THIS PHONE ONLY"))
        screenBody.addView(monoText("Export a chat, a debate log or a task report as a text file. It stays in the app's reports folder — share it only when you tap SHARE.", 12f, colorAccentSecondary()),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            ReportExporter.Kind.CHAT to "EXPORT CHAT",
            ReportExporter.Kind.DEBATE to "EXPORT DEBATE LOG",
            ReportExporter.Kind.TASKS to "EXPORT TASK REPORT"
        ).forEach { (kind, label) ->
            val count = reportExporter.countFor(kind)
            buttons.addView(shellButton(if (count > 0) "$label ($count)" else label) {
                val saved = reportExporter.export(kind)
                if (saved == null) {
                    AlertDialog.Builder(this).setTitle("Nothing to export yet")
                        .setMessage(when (kind) {
                            ReportExporter.Kind.CHAT -> "There is no chat saved yet — send MAGI a message first."
                            ReportExporter.Kind.DEBATE -> "No MAGI reply has a deliberation log yet — ask MAGI something first."
                            ReportExporter.Kind.TASKS -> "No phone task has been proposed yet — a MAGI answer can propose one."
                        })
                        .setPositiveButton("OK", null).show()
                } else {
                    showReportDialog(saved)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        screenBody.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        if (reports.isEmpty()) {
            screenBody.addView(monoText("NO REPORTS SAVED YET.", 11f, colorAccentSecondary()))
            return
        }
        reports.forEach { report ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText("▤ ${report.name}", 11f, colorAccent()).apply {
                isClickable = true
                setOnClickListener { showReportDialog(report) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            row.addView(monoText(folderManager.formatSize(report.size), 10f, colorAccentSecondary()).apply { minWidth = dp(52) })
            row.addView(shellButton("SHARE") { shareReport(report) })
            row.addView(shellButton("DEL") {
                AlertDialog.Builder(this)
                    .setTitle("Delete ${report.name}?")
                    .setMessage("This deletes the report file. This cannot be undone.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ ->
                        if (!reportExporter.deleteReport(report.file)) {
                            AlertDialog.Builder(this).setTitle("Could not delete").setMessage("That report is protected or outside the reports folder.")
                                .setPositiveButton("OK", null).show()
                        } else showScreen("armor")
                    }
                    .show()
            })
            screenBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
    }

    private fun showReportDialog(report: ReportExporter.Report) {
        val text = reportExporter.readReport(report.file)
        if (text == null) {
            AlertDialog.Builder(this).setTitle(report.name)
                .setMessage("Cannot show this report (too big, or not text).")
                .setPositiveButton("OK", null).show()
            return
        }
        val view = TextView(this).apply {
            setText(text)
            AgentTheme.style(this)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(view) }
        AlertDialog.Builder(this).setTitle(report.name).setView(scroll)
            .setPositiveButton("OK", null)
            .setNeutralButton("SHARE") { _, _ -> shareReport(report) }
            .show()
    }

    private fun shareReport(report: ReportExporter.Report) {
        val text = reportExporter.readReport(report.file)
        if (text == null) {
            AlertDialog.Builder(this).setTitle("Could not share")
                .setMessage("That report cannot be read right now.")
                .setPositiveButton("OK", null).show()
            return
        }
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, report.name)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, "SHARE REPORT"))
        } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Could not share")
                .setMessage("No app on this phone can take the report right now.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun showComms() {

        finalizeRunningTask(refresh = false)

        val nodesOn = magiEngine.enabledNodes().size
        screenBody.addView(title("通信", "COMMS", "MAGI LINK: $nodesOn NODE${if (nodesOn == 1) "" else "S"}"))

        if (magiEngine.offlineMode.enabled) {
            screenBody.addView(monoText("OFFLINE MODE — LOCAL ONLY · cloud providers blocked", 12f, colorDanger()).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
        }

        if (pendingSharedText != null || pendingSharedImage != null) showSharedBanner()

        val folderScroll = HorizontalScrollView(this)
        val folderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipFolders = listOf("ALL") + chatFolderStore.allFolders()
        chipFolders.forEach { folder ->
            val count = if (folder == "ALL") chatStore.entries().size else chatFolderStore.countIn(folder)
            val chip = shellButton(
                (if (folder == chatFolderFilter) "● " else "") + folder + " ($count)"
            ) {
                chatFolderFilter = folder
                chatHighlightIndex = -1
                showScreen("comms")
            }
            folderRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
        }
        folderScroll.addView(folderRow)
        screenBody.addView(folderScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val entries = chatStore.entries()

        val shownEntries = entries.withIndex().filter { (_, msg) ->
            chatFolderFilter == "ALL" || msg.folder == chatFolderFilter
        }

        val renderedEntries = if (chatShowAllMessages || shownEntries.size <= 30) shownEntries
            else shownEntries.takeLast(30)
        if (shownEntries.size > 30) {
            val windowRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            windowRow.addView(monoText(
                "SHOWING LATEST 30 OF ${shownEntries.size} MESSAGES",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(0, -2, 1f))
            windowRow.addView(shellButton(if (chatShowAllMessages) "SHOW FEWER" else "SHOW ALL") {
                chatShowAllMessages = !chatShowAllMessages
                showScreen("comms")
            })
            screenBody.addView(windowRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        renderedEntries.forEach { (index, msg) ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(AgentTheme.background)
                    setStroke(dp(2), when {
                        msg.deleted -> AgentTheme.muted
                        index == chatHighlightIndex -> colorDanger()
                        msg.role == ChatStore.Role.YOU -> AgentTheme.signal
                        else -> colorAccent()
                    })
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    chatHighlightIndex = -1
                    chatStore.toggleDeleted(index)
                    showScreen("comms")
                }

                setOnLongClickListener {
                    showMoveChatDialog(index)
                    true
                }
            }

            if (index == chatHighlightIndex) {
                box.addView(monoText("◉ SEARCH MATCH", 12f, AgentTheme.background).apply {
                    gravity = Gravity.CENTER
                    setBackgroundColor(colorDanger())
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                })
            }
            if (msg.deleted) {
                box.addView(monoText("DELETED DELETED DELETED", 12f, AgentTheme.background).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = 0.2f
                    setBackgroundColor(colorDanger())
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                })
            }
            val sender = when {
                msg.sender.isNotBlank() -> msg.sender
                msg.role == ChatStore.Role.YOU -> "YOU"
                msg.role == ChatStore.Role.AI -> "MAGI"
                else -> "SYSTEM"
            }
            val voteText = if (msg.votes.isNotEmpty()) {
                " / " + msg.votes.joinToString(" / ") { (name, yes) ->
                    "${name.split("·")[0]} ${if (yes) "承認" else "否定"}"
                }
            } else ""
            box.addView(monoText("$sender · ${msg.time}$voteText", 12f, colorAccentSecondary()))
            box.addView(monoText(msg.text, 12f, when {
                msg.deleted -> AgentTheme.muted
                msg.role == ChatStore.Role.YOU -> AgentTheme.signal
                else -> colorAccent()
            }))
            screenBody.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }

        if (smartReplies.enabled) {
            val lastAi = chatStore.entries().lastOrNull { it.role == ChatStore.Role.AI && !it.deleted }
            val suggestions = lastAi?.let { SmartReplies.suggestionsFor(it.text) } ?: emptyList()
            if (suggestions.isNotEmpty()) {
                val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                suggestions.forEach { suggestion ->
                    chipRow.addView(shellButton(suggestion) {
                        if (!chatBusy) runMagiChat(suggestion)
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
                }
                screenBody.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
                screenBody.addView(monoText(
                    "SMART REPLIES — MAGI's suggestions from its last answer. Tap one to send it, or ignore them and type your own.",
                    11f, colorAccentSecondary()
                ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        agentStatusView = monoText(
            if (chatBusy) "● LINKING…" else agentBridge.status(), 11f, colorDanger()
        )
        screenBody.addView(agentStatusView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        val sendRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply {
            hint = "ENTER MESSAGE"

            contentDescription = "Message to MAGI"

            setText(pendingSharedText ?: "")

            filters = arrayOf(InputFilter.LengthFilter(2000))
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEND
            htmlInputStyle(this)
        }
        sendRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        val sendAction = {
            val text = input.text.toString().trim()
            if (text.isNotEmpty() && !chatBusy) {
                input.setText("")
                runMagiChat(text)
            }
        }
        sendRow.addView(shellButton("SEND") { sendAction() }, LinearLayout.LayoutParams(dp(70), -2))
        screenBody.addView(sendRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendAction(); true } else false
        }

        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val searchInput = EditText(this).apply {
            hint = "SEARCH CHAT + LOGS"
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(chatSearchQuery ?: "")
            htmlInputStyle(this)
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        val runSearch = {
            chatSearchQuery = searchInput.text.toString().trim().takeIf { it.isNotEmpty() }
            chatHighlightIndex = -1
            showScreen("comms")
        }
        searchRow.addView(shellButton("SEARCH") { runSearch() }, LinearLayout.LayoutParams(dp(84), -2))
        if (chatSearchQuery != null || chatHighlightIndex >= 0) {
            searchRow.addView(shellButton("CLEAR") {
                chatSearchQuery = null
                chatHighlightIndex = -1
                showScreen("comms")
            }, LinearLayout.LayoutParams(dp(70), -2).apply { marginStart = dp(6) })
        }
        screenBody.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }
        val query = chatSearchQuery
        if (!query.isNullOrBlank()) {
            val hits = ChatSearch.search(chatStore.entries(), query)
            screenBody.addView(monoText(
                if (hits.isEmpty()) "NO MATCHES FOR \"$query\" — TRY FEWER WORDS"
                else "${hits.size} HIT${if (hits.size == 1) "" else "S"} FOR \"$query\" — TAP TO JUMP",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            hits.forEach { hit ->
                val hitEntry = chatStore.entries().getOrNull(hit.entryIndex) ?: return@forEach
                val hitBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = GradientDrawable().apply {
                        setColor(AgentTheme.background)
                        setStroke(dp(2), colorAccentSecondary())
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        chatHighlightIndex = hit.entryIndex
                        chatSearchQuery = null
                        showScreen("comms")
                    }
                }
                val hitSender = if (hitEntry.sender.isNotBlank()) hitEntry.sender
                    else if (hitEntry.role == ChatStore.Role.YOU) "YOU"
                    else if (hitEntry.role == ChatStore.Role.AI) "MAGI" else "SYSTEM"
                val hitTag = if (hit.kind == ChatSearch.HitKind.LOG) "DELIBERATION LOG" else "MESSAGE"
                hitBox.addView(monoText("$hitTag · $hitSender · ${hitEntry.time}", 10f, colorAccentSecondary()))
                hitBox.addView(monoText(hit.snippet, 12f, colorAccent()))
                screenBody.addView(hitBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }

        val folderActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        folderActionRow.addView(shellButton("CHAT FOLDERS ▸") { showManageChatFoldersDialog() },
            LinearLayout.LayoutParams(0, -2, 1f))
        screenBody.addView(folderActionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(shellButton("VIEW DELIBERATION LOG") {
            val log = chatStore.lastAiLog()
            AlertDialog.Builder(this)
                .setTitle("DELIBERATION LOG")
                .setMessage(if (log.isEmpty()) "No debate yet — send a message first." else log.joinToString("\n\n"))
                .setPositiveButton("CLOSE", null)
                .show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        actionRow.addView(shellButton("CLEAR CHAT") {
            chatStore.clear()
            chatSearchQuery = null
            chatHighlightIndex = -1
            showScreen("comms")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        screenBody.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val teachRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        teachRow.addView(shellButton("TEACH MAGI ▸") { showTeachDialog() },
            LinearLayout.LayoutParams(0, -2, 1f))
        screenBody.addView(teachRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val agentRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        agentRow.addView(shellButton("RUN AS AGENT TASK ▸") { runAgentTaskFromChat() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        agentRow.addView(shellButton("STOP") {
            agentBridge.stop()
            if (current == "comms") showScreen("comms")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        screenBody.addView(agentRow)

        showPhoneTasksBox()

        val voiceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        voiceRow.addView(shellButton(if (voiceListening) "LISTENING…" else "VOICE LISTEN ▸") {
            startVoiceListen()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        voiceRow.addView(shellButton("SPEAK REPLIES: ${if (magiVoice.enabled) "ON" else "OFF"}") {
            magiVoice.enabled = !magiVoice.enabled
            showScreen("comms")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        screenBody.addView(voiceRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val smartRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        smartRow.addView(shellButton("SMART REPLIES: ${if (smartReplies.enabled) "ON" else "OFF"}") {
            smartReplies.enabled = !smartReplies.enabled
            showScreen("comms")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        screenBody.addView(smartRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        screenBody.addView(monoText(
            "Chat runs MagiEngine. ANTHROPIC (Claude), CHATGPT, GROK and LOCAL (Ollama) nodes all answer for real now — " +
            "give each node its own key in SETUP. With no provider configured the replies are LOCAL DEMO drafts. " +
            "Agent tasks need your approval first — a MAGI decision that needs the phone shows up above as a PHONE TASK, " +
            "and never runs until you approve it. Teach MAGI with TEACH MAGI — a loaded skill is part of every debate; " +
            "type \"teach MAGI…\" and CHAT will offer to save it. SEARCH CHAT looks through past chats and " +
            "deliberation logs in every folder — tap a hit to jump to that message. Tap a message to delete it — tap it again to bring it back. Long-press a message to move it to another folder.", 12f, colorAccentSecondary()))
    }

    private fun showSharedBanner() {
        val box = borderedPanel()
        box.addView(boxHeading("SHARED FROM ANOTHER APP"))
        val sharedText = pendingSharedText
        if (sharedText != null) {
            box.addView(monoText("SHARED ${SharedContent.kindOf(sharedText)} — tap ASK MAGI to send it to MAGI for a debate, or edit it in the box below and tap SEND. Nothing is sent until you tap.", 12f, colorAccent()))
            box.addView(monoText(SharedContent.preview(sharedText), 12f, colorAccent()))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(shellButton("ASK MAGI ▸") {
                val text = pendingSharedText
                pendingSharedText = null
                if (text != null) runMagiChat(text)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            row.addView(shellButton("DISCARD") {
                pendingSharedText = null
                showScreen("comms")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
            box.addView(row)
        }
        val sharedImage = pendingSharedImage
        if (sharedImage != null) {
            box.addView(monoText("SHARED PHOTO — ${sharedImage.name} (${SharedContent.formatSize(sharedImage.length())})", 12f, colorAccent()))
            box.addView(monoText("MAGI cannot see the photo itself — only its name and size. Ask MAGI what to do with it, and describe what is in it if MAGI asks. The photo is saved in NERV's shared folder — see SETUP > FILES.", 12f, colorAccentSecondary()))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(shellButton("ASK MAGI ABOUT THIS PHOTO ▸") {
                val file = pendingSharedImage
                pendingSharedImage = null
                if (file != null) runMagiChat(
                    "I shared a photo called ${file.name} (${SharedContent.formatSize(file.length())}). " +
                    "It is saved in NERV's shared folder. What should I do with it?"
                )
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            row.addView(shellButton("DISCARD") {
                pendingSharedImage = null
                showScreen("comms")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
            box.addView(row)
        }
        screenBody.addView(box)
    }

    private fun showManageChatFoldersDialog() {
        var managerDialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        chatFolderStore.allFolders().forEach { folder ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val count = chatFolderStore.countIn(folder)
            row.addView(monoText("$folder ($count)", 13f, colorAccent()),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })

            row.addView(shellButton("RENAME") { managerDialog?.dismiss(); showRenameChatFolderDialog(folder) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            row.addView(shellButton("DELETE") { managerDialog?.dismiss(); showDeleteChatFolderDialog(folder) },
                LinearLayout.LayoutParams(-2, -2))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        list.addView(monoText("GENERAL cannot be renamed or deleted. A folder can only be deleted when it holds no messages — move them to another folder first (long-press a message). Search looks in every folder, whatever the filter shows.", 11f, colorAccentSecondary()))
        managerDialog = AlertDialog.Builder(this).setTitle("Chat folders")
            .setView(list).setNegativeButton("CLOSE", null)
            .setPositiveButton("＋ NEW FOLDER", null).create()
        managerDialog.setOnShowListener {
            managerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                managerDialog.dismiss()
                showNewChatFolderDialog()
            }
        }
        managerDialog.show()
    }

    private fun showNewChatFolderDialog() {
        val input = EditText(this).apply {
            hint = "Folder name, e.g. MAGI"
            maxLines = 1
            AgentTheme.style(this)
        }
        val dialog = AlertDialog.Builder(this).setTitle("New folder")
            .setView(input.apply { setPadding(dp(24), dp(12), dp(24), dp(12)) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = chatFolderStore.addFolder(input.text.toString())
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not create the folder").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    dialog.dismiss()
                    if (current == "comms") showScreen("comms")
                    showManageChatFoldersDialog()
                }
            }
        }
        dialog.show()
    }

    private fun showRenameChatFolderDialog(folder: String) {
        val input = EditText(this).apply {
            hint = "New name"
            maxLines = 1
            setText(folder)
            AgentTheme.style(this)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Rename $folder")
            .setMessage("Every message in $folder moves to the new name with it.")
            .setView(input.apply { setPadding(dp(24), dp(12), dp(24), dp(12)) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("RENAME", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = chatFolderStore.renameFolder(folder, input.text.toString())
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not rename the folder").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    if (chatFolderFilter == folder) {

                        chatFolderFilter = chatFolderStore.allFolders()
                            .firstOrNull { it.equals(input.text.toString(), ignoreCase = true) } ?: "ALL"
                    }
                    dialog.dismiss()
                    if (current == "comms") showScreen("comms")
                    showManageChatFoldersDialog()
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteChatFolderDialog(folder: String) {
        val held = chatFolderStore.countIn(folder)
        if (held > 0) {
            AlertDialog.Builder(this).setTitle("Cannot delete $folder")
                .setMessage("That folder still holds $held message${if (held == 1) "" else "s"} — move or delete them first.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Delete folder \"$folder\"?")
            .setMessage("The folder is empty, so no message is lost.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                val error = chatFolderStore.deleteFolder(folder)
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("Could not delete the folder").setMessage(error)
                        .setPositiveButton("OK", null).show()
                } else {
                    if (chatFolderFilter == folder) chatFolderFilter = "ALL"
                    if (current == "comms") showScreen("comms")
                    showManageChatFoldersDialog()
                }
            }
            .show()
    }

    private fun showMoveChatDialog(index: Int) {
        val folders = chatFolderStore.allFolders()
        val labels = folders.map { "$it (${chatFolderStore.countIn(it)})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("MOVE MESSAGE TO FOLDER")
            .setItems(labels) { _, which ->
                chatStore.setFolder(index, folders[which])
                showScreen("comms")
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun startVoiceListen() {
        if (voiceListening) {
            voiceConnector.stopListening()
            voiceListening = false
            NervVoiceWidget.saveStatus(this, currentStatus())
            NervVoiceWidget.updateAll(this)
            if (current == "comms") showScreen("comms")
            return
        }
        voiceListening = true
        showScreen("comms")
        NervVoiceWidget.saveStatus(this, NervStatus.LISTENING)
        NervVoiceWidget.updateAll(this)
        voiceConnector.listenOnce(
            onHeard = { heard ->
                voiceListening = false
                runMagiChat(heard)
            },
            onError = { message ->
                voiceListening = false
                NervVoiceWidget.saveStatus(this, currentStatus())
                NervVoiceWidget.updateAll(this)
                AlertDialog.Builder(this).setTitle("Voice").setMessage(message)
                    .setPositiveButton("OK") { _, _ -> if (current == "comms") showScreen("comms") }
                    .show()
            }
        )
    }

    private fun runMagiChat(text: String) {
        if (text.isBlank() || chatBusy) return

        val cleanText = if (::wakeWord.isInitialized) wakeWord.stripWakeWord(text) else text.trim()
        if (cleanText.isBlank()) return
        val text = cleanText
        chatHighlightIndex = -1
        val now = timeFmtHHmm.format(Date())
        chatStore.add(ChatStore.Entry(ChatStore.Role.YOU, text, now, sender = "YOU"))

        pendingTeach = if (TaughtSkillStore.looksLikeTeaching(text)) text else null
        chatBusy = true
        showScreen("comms")
        Thread {
            val result = try {

                val debateContext = listOf(magiMemory.memoryContext(), calendarLink.calendarContext())
                    .filter { it.isNotBlank() }.joinToString("\n")
                magiEngine.converse(text, chatStore.history(), debateContext)
            } catch (e: Exception) {
                MagiEngine.MagiResult(
                    "MAGI could not answer: ${e.message ?: "unknown error"}",
                    listOf("SYSTEM: ${e.message}"), emptyList(), localDemo = true
                )
            }

            magiMemory.rememberDebate(text, result)

            debateHistory.record(text, result)
            val replyTime = timeFmtHHmm.format(Date())
            chatStore.add(
                ChatStore.Entry(
                    ChatStore.Role.AI, result.text, replyTime, sender = "MAGI",
                    votes = result.votes, log = result.log
                )
            )
            runOnUiThread {
                chatBusy = false

                NervVoiceWidget.saveReply(this, result.text)
                NervVoiceWidget.saveStatus(this, currentStatus())
                NervVoiceWidget.updateAll(this)

                if (magiVoice.enabled) {
                    magiVoice.speak(result.chairNode ?: MagiEngine.NodeId.MELCHIOR, result.text)
                }
                if (current == "comms") showScreen("comms")

                val proposed = result.phoneTask?.let { phoneTaskStore.propose(it, text) }

                if (proposed != null && !appInForeground) {
                    notificationHelper.notifyApprovalNeeded(proposed.objective)
                }

                val teach = pendingTeach
                pendingTeach = null
                val calTitle = result.calendarEvent

                val afterCalendar: () -> Unit = {
                    if (teach != null && current == "comms") showTeachDialog(teach)
                }
                if (proposed != null && current == "comms") {
                    showPhoneTaskProposalDialog(proposed) {
                        if (calTitle != null && current == "comms") showCalendarEventProposalDialog(calTitle, afterCalendar)
                        else afterCalendar()
                    }
                } else if (calTitle != null && current == "comms") {
                    showCalendarEventProposalDialog(calTitle, afterCalendar)
                } else if (teach != null && current == "comms") showTeachDialog(teach)
            }
        }.start()
    }

    private fun runAgentTaskFromChat() {
        val lastQuestion = chatStore.entries().lastOrNull { it.role == ChatStore.Role.YOU }?.text ?: ""
        if (lastQuestion.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Nothing to run yet")
                .setMessage("Send a message in CHAT first, so MAGI has a decision to hand over.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val lastAnswer = chatStore.entries().lastOrNull { it.role == ChatStore.Role.AI }?.text ?: ""
        val objective = if (lastQuestion.isBlank()) "" else "MAGI decision: $lastAnswer\n\nTask: $lastQuestion"
        showTaskApprovalDialog(objective, task = null)
    }

    private fun showTaskApprovalDialog(objective: String, task: PhoneTaskStore.PhoneTask?) {
        when (agentBridge.requestTask(objective)) {
            AgentBridge.TaskRequest.NEEDS_PROVIDER -> {
                AlertDialog.Builder(this)
                    .setTitle("No provider configured")
                    .setMessage("Add your own API key in SETUP first — keys go in Android Keystore only.")
                    .setPositiveButton("OPEN SETUP") { _, _ -> showScreen("setup") }
                    .setNegativeButton("CANCEL", null)
                    .show()
            }
            AgentBridge.TaskRequest.ALREADY_RUNNING -> {
                AlertDialog.Builder(this)
                    .setTitle("A task is already running")
                    .setMessage(agentBridge.status())
                    .setPositiveButton("STOP IT") { _, _ -> agentBridge.stop(); showScreen("comms") }
                    .setNegativeButton("CANCEL", null)
                    .show()
            }
            AgentBridge.TaskRequest.NEEDS_APPROVAL -> {
                val config = agentBridge.providerConfig()
                AlertDialog.Builder(this)
                    .setTitle("Approve agent task?")
                    .setMessage(
                        "Send this to ${config?.endpoint} using ${config?.model}?\n\n$objective\n\n" +
                        "Max 8 model calls, 3 minutes, ~24k tokens. No file is sent. " +
                        "Phone tools only work in a TAKE CONTROL session, with approval per action. STOP cancels at any time."
                    )
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("RUN") { _, _ ->
                        if (agentBridge.startApprovedTask(objective) && task != null) {
                            phoneTaskStore.markRunning(task.id)
                            handler.postDelayed(taskWatcherRunnable, 500)
                        }
                        showScreen("comms")
                    }
                    .show()
            }
            AgentBridge.TaskRequest.STARTED -> showScreen("comms")
        }
    }

    private fun showPhoneTasksBox() {
        val tasks = phoneTaskStore.all()
        if (tasks.isEmpty()) return
        val box = borderedPanel()
        box.addView(boxHeading("PHONE TASKS"))
        tasks.take(5).forEach { task ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(task.objective.take(80), 11f, colorAccent()),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            val stateColor = when (task.status) {
                PhoneTaskStore.TaskStatus.DONE, PhoneTaskStore.TaskStatus.RUNNING -> AgentTheme.signal
                PhoneTaskStore.TaskStatus.PROPOSED -> colorAccentSecondary()
                else -> colorDanger()
            }
            row.addView(monoText(task.status.name, 11f, stateColor),
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            when (task.status) {
                PhoneTaskStore.TaskStatus.PROPOSED -> {
                    row.addView(shellButton("APPROVE") { approvePhoneTask(task) },
                        LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
                    row.addView(shellButton("DENY") {
                        phoneTaskStore.markDenied(task.id)
                        if (current == "comms") showScreen("comms")
                    })
                }
                PhoneTaskStore.TaskStatus.RUNNING -> {
                    row.addView(shellButton("STOP") {
                        agentBridge.stop()
                        phoneTaskStore.markStopped(task.id)
                        if (current == "comms") showScreen("comms")
                    })
                }
                PhoneTaskStore.TaskStatus.DONE, PhoneTaskStore.TaskStatus.FAILED, PhoneTaskStore.TaskStatus.STOPPED -> {
                    if (task.result.isNotBlank()) {
                        row.addView(shellButton("VIEW") { showPhoneTaskResultDialog(task) })
                    }
                }
                PhoneTaskStore.TaskStatus.DENIED -> {   }
            }
            box.addView(row)
            if (task.status == PhoneTaskStore.TaskStatus.RUNNING) {
                box.addView(monoText(agentBridge.status(), 10f, colorAccentSecondary()))
            } else if (task.status == PhoneTaskStore.TaskStatus.PROPOSED && task.question.isNotBlank()) {
                box.addView(monoText("Asked: ${task.question.take(100)} · ${task.createdAt}", 10f, colorAccentSecondary()))
            }
        }
        if (tasks.size > 5) {
            box.addView(monoText("…and ${tasks.size - 5} older task(s).", 10f, colorAccentSecondary()))
        }
        box.addView(monoText("A MAGI decision that needs the phone becomes a task here. Nothing runs until you approve it — approve in CHAT or here. STOP cancels a running task at any time.", 11f, colorAccentSecondary()))
        screenBody.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun showPhoneTaskProposalDialog(task: PhoneTaskStore.PhoneTask, onDismiss: () -> Unit = {}) {
        var approved = false
        AlertDialog.Builder(this)
            .setTitle("MAGI proposes a phone task")
            .setMessage(
                "MAGI's decision needs the phone to do something:\n\n${task.objective}\n\n" +
                "Nothing runs until you approve it. Approve to send it to AgentRuntime as an agent task; deny and it stays here as denied."
            )
            .setOnDismissListener { if (!approved) onDismiss() }
            .setNegativeButton("DENY") { _, _ ->
                phoneTaskStore.markDenied(task.id)
                if (current == "comms") showScreen("comms")
            }
            .setPositiveButton("APPROVE ▸") { _, _ ->
                approved = true
                approvePhoneTask(task)
            }
            .show()
    }

    private fun approvePhoneTask(task: PhoneTaskStore.PhoneTask) {
        showTaskApprovalDialog(task.objective, task)
    }

    private fun showPhoneTaskResultDialog(task: PhoneTaskStore.PhoneTask) {
        AlertDialog.Builder(this)
            .setTitle("Phone task — ${task.status}")
            .setMessage("${task.objective}\n\n${task.result.ifBlank { "No answer recorded." }}")
            .setPositiveButton("CLOSE", null)
            .setNeutralButton("DELETE") { _, _ ->
                phoneTaskStore.delete(task.id)
                if (current == "comms") showScreen("comms")
            }
            .show()
    }

    private fun finalizeRunningTask(refresh: Boolean = true) {
        if (!::phoneTaskStore.isInitialized) return
        val running = phoneTaskStore.running() ?: return
        if (agentBridge.isRunning()) return
        val answer = agentBridge.answer().trim()
        phoneTaskStore.markDone(running.id, if (answer.isNotBlank()) answer else agentBridge.status())

        if (!appInForeground) notificationHelper.notifyTaskFinished(running.objective)
        agentStatusView?.text = agentBridge.status()
        if (refresh && current == "comms") showScreen("comms")
    }

    private fun showTox() {
        val elapsed = 120 + (System.currentTimeMillis() - toxStartTime) / 60000
        screenBody.addView(title("毒性", "MENTAL TOXICITY LEVEL", "ELAPSED $elapsed min."))

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        modeRow.addView(monoText(if (toxLive) "● LIVE — BARS FOLLOW THIS PHONE" else "○ MANUAL — BARS DRIFT BY HAND", 12f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        modeRow.addView(shellButton(if (toxLive) "SWITCH TO MANUAL" else "SWITCH TO LIVE") {
            toxLive = !toxLive
            if (toxLive) {
                for (i in toxOffsets.indices) toxOffsets[i] = 0
                val signals = toxSignals ?: toxMonitor.sample()
                toxSignals = signals
                val live = toxMonitor.valuesFrom(signals)
                for (i in live.indices) {
                    toxScreenValues[i] = live[i]
                    toxValues[i] = live[i]
                }
            }
            showScreen("tox")
        })
        screenBody.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val panel = borderedPanel()
        listOf("00 FIRST", "01 THIRD", "02 SECOND").forEachIndexed { i, name ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

            row.addView(monoText(name.take(2), 18f, colorDanger()).apply { minWidth = dp(26) })
            row.addView(monoText(name.drop(3), 11f, colorAccent()),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            val bar = StripedBarView(this).apply { setValue(toxScreenValues[i]); setHot(toxScreenValues[i] >= 85) }
            toxScreenBars.add(bar)
            row.addView(bar, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
            val valueView = monoText("${toxScreenValues[i]}", 12f, colorAccent()).apply {
                minWidth = dp(24)
                gravity = Gravity.END
            }
            toxScreenValueViews.add(valueView)
            row.addView(valueView)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(7) })
        }

        val scaleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scaleRow.addView(monoText("0", 10f, colorAccentSecondary()))
        scaleRow.addView(monoText("CAUTION 60", 10f, colorAccentSecondary()).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        scaleRow.addView(monoText("DANGER 85", 10f, colorAccentSecondary()))
        panel.addView(scaleRow)
        screenBody.addView(panel)
        val purity = borderedPanel()
        val purityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        purityRow.addView(monoText("L.C.L. PURITY", 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        toxPurityView = monoText("", 13f, colorAccent())
        purityRow.addView(toxPurityView)
        purity.addView(purityRow)
        screenBody.addView(purity)

        val readout = monoText("", 12f, colorAccentSecondary())
        toxReadoutView = readout
        screenBody.addView(readout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val healthPanel = borderedPanel()
        healthPanel.addView(boxHeading("HEALTH DASHBOARD"))
        val healthLines = monoText("", 12f, colorAccent())
        healthLinesView = healthLines
        healthPanel.addView(healthLines)
        val healthWarnings = monoText("", 12f, AgentTheme.signal)
        healthWarningsView = healthWarnings
        healthPanel.addView(healthWarnings)
        screenBody.addView(healthPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        updateToxScreen()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(shellButton("INJECT +15") {
            if (toxLive) {

                for (i in toxOffsets.indices) toxOffsets[i] = (toxOffsets[i] + 15).coerceAtMost(85)
            } else {
                for (i in toxScreenValues.indices) toxScreenValues[i] = minOf(100, toxScreenValues[i] + 15)
            }
            updateToxScreen()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        row.addView(shellButton("PURGE") {
            if (toxLive) {

                for (i in toxOffsets.indices) toxOffsets[i] = 10 - toxScreenValues[i]
            } else {
                for (i in toxScreenValues.indices) toxScreenValues[i] = 10
            }
            updateToxScreen()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        screenBody.addView(row)
        screenBody.addView(monoText(if (toxLive) "LIVE — bars follow this phone's battery, CPU load and memory. INJECT and PURGE are a temporary nudge; the bars drift back to the live values." else "MANUAL — bars drift by hand, like the HTML. Switch to LIVE to follow this phone.", 12f, colorAccentSecondary()))
    }

    private fun updateToxScreen() {
        if (toxLive) {

            for (i in toxScreenValues.indices) {
                toxScreenBars.getOrNull(i)?.let { bar ->
                    bar.setValue(toxScreenValues[i])
                    bar.setHot(toxScreenValues[i] >= 85)
                }
                toxScreenValueViews.getOrNull(i)?.text = "${toxScreenValues[i]}"
            }
        } else {
            for (i in toxScreenValues.indices) {
                val drift = (Math.random() - 0.5) * 8
                toxScreenValues[i] = (toxScreenValues[i] + drift).toInt().coerceIn(2, 100)
                toxScreenBars.getOrNull(i)?.let { bar ->
                    bar.setValue(toxScreenValues[i])
                    bar.setHot(toxScreenValues[i] >= 85)
                }
                toxScreenValueViews.getOrNull(i)?.text = "${toxScreenValues[i]}"
            }
        }
        val purity = 99.9999989 - toxScreenValues.sum() / 3_000_000.0
        toxPurityView?.text = String.format(Locale.getDefault(), "%.7f%%", purity)
        val elapsed = 120 + (System.currentTimeMillis() - toxStartTime) / 60000
        toxElapsedView?.text = "$elapsed min."
        toxReadoutView?.text = if (toxLive) {
            val s = toxSignals
            if (s == null) "READING THIS PHONE…" else toxMonitor.readout(s)
        } else {
            "MANUAL — THE BARS DRIFT BY HAND."
        }

        if (::healthDashboard.isInitialized) {
            val snap = healthDashboard.snapshot()
            healthLinesView?.text = healthDashboard.lines(snap)
            healthWarningsView?.text = if (snap.warnings.isEmpty()) {
                "ALL HEALTHY — NOTHING TO WORRY ABOUT."
            } else {
                snap.warnings.joinToString("\n") { "⚠ $it" }
            }
        }
    }

    private fun selectedTimer(): TimerStore.TimerEntry? {
        val timers = timerStore.all()
        return timerSelectedId?.let { id -> timers.firstOrNull { it.id == id } }
            ?: timers.firstOrNull()
    }

    private fun formatTimer(millis: Long, withTenths: Boolean): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs)
        } else if (withTenths) {
            val tenth = ((millis % 1000) / 100).toInt()
            String.format(Locale.getDefault(), "%02d:%02d.%d", mins, secs, tenth)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
        }
    }

    private fun showTimer() {
        screenBody.addView(title("起動", "ENTRY PLUG", "ACTIVATION TIME"))

        val clockFrame = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(colorDanger(), colorAccent(), AgentTheme.green)
            ).apply { setStroke(dp(2), colorAccent()) }
        }
        timerValueView = minchoText("", 56f, colorAccent()).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(AgentTheme.background)
            setShadowLayer(12f, 0f, 0f, colorAccent())
        }
        clockFrame.addView(timerValueView, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
        screenBody.addView(clockFrame, LinearLayout.LayoutParams(-1, dp(120)).apply { bottomMargin = dp(12) })
        val timerBarBox = borderedPanel()
        timerBarView = StripedBarView(this).apply { setValue(100) }
        timerBarBox.addView(timerBarView, LinearLayout.LayoutParams(-1, dp(18)))
        screenBody.addView(timerBarBox)
        timerEmptyView = monoText("", 12f, colorAccentSecondary()).apply {
            gravity = Gravity.CENTER
        }
        screenBody.addView(timerEmptyView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timerButtonView = shellButton("START") {
            val entry = selectedTimer() ?: return@shellButton
            val now = System.currentTimeMillis()
            if (entry.running) timerStore.stop(entry.id, now) else timerStore.start(entry.id, now)
            showScreen("timer")
        }
        row.addView(timerButtonView, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        row.addView(shellButton("RESET") {
            val entry = selectedTimer() ?: return@shellButton
            timerStore.reset(entry.id, System.currentTimeMillis())
            showScreen("timer")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
        row.addView(shellButton("+1:00") {
            val entry = selectedTimer() ?: return@shellButton
            timerStore.addMinute(entry.id, System.currentTimeMillis())
            updateTimerViews()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        screenBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        screenBody.addView(monoText("NEW TIMER — TAP A PRESET", 12f, colorAccentSecondary()).apply {
            setPadding(0, 0, 0, dp(6))
        })

        TimerStore.PRESETS.chunked(3).forEach { chunk ->
            val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { index, preset ->
                presets.addView(shellButton(preset.label) {
                    val added = timerStore.add("TIMER ${timerStore.all().size + 1}", preset.millis)
                    if (added != null) {
                        timerSelectedId = added.id
                        showScreen("timer")
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = if (index == chunk.size - 1) 0 else dp(4)
                })
            }
            screenBody.addView(presets, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }

        screenBody.addView(monoText("TIMERS (${timerStore.all().size}/8)", 12f, colorAccentSecondary()).apply {
            setPadding(0, 0, 0, dp(6))
        })
        timerRowViews.clear()
        for (entry in timerStore.all()) {
            val timerRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setStroke(dp(1), AgentTheme.muted)
                }
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val nameView = monoText(entry.name, 13f, if (entry.finished) colorDanger() else colorAccent()).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setOnClickListener {
                    timerSelectedId = entry.id
                    showScreen("timer")
                }
            }
            topRow.addView(nameView, LinearLayout.LayoutParams(0, -2, 1f))
            val valueView = monoText("", 15f, colorAccent()).apply { gravity = Gravity.CENTER_VERTICAL }
            topRow.addView(valueView, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            val toggle = shellButton(if (entry.finished) "RESET" else if (entry.running) "STOP" else "START") {
                val now = System.currentTimeMillis()
                when {
                    entry.finished -> timerStore.reset(entry.id, now)
                    entry.running -> timerStore.stop(entry.id, now)
                    else -> timerStore.start(entry.id, now)
                }
                showScreen("timer")
            }
            topRow.addView(toggle, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            topRow.addView(shellButton("DEL") {
                val wasSelected = entry.id == selectedTimer()?.id
                timerStore.remove(entry.id)
                if (wasSelected) timerSelectedId = timerStore.all().firstOrNull()?.id
                showScreen("timer")
            }, LinearLayout.LayoutParams(-2, -2))
            timerRow.addView(topRow)
            val bar = StripedBarView(this).apply { setValue(100) }
            timerRow.addView(bar, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(6) })
            timerRowViews[entry.id] = Triple(valueView, bar, toggle)
            screenBody.addView(timerRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        updateTimerViews()
    }

    private fun updateTimerViews() {
        val now = System.currentTimeMillis()
        val selected = selectedTimer()
        val millis = selected?.remainingMillis(now) ?: 0L
        timerValueView?.text = if (selected == null) "--:--" else formatTimer(millis, true)
        timerValueView?.setTextColor(
            if (selected?.finished == true) colorDanger()
            else if (millis <= 10_000L && millis > 0L) colorDanger()
            else colorAccent()
        )
        val percent = if (selected != null && selected.totalMillis > 0) (millis * 100 / selected.totalMillis).toInt() else 0
        timerBarView?.setValue(percent)
        timerBarView?.setHot(selected?.finished == true || (millis <= 10_000L && millis > 0L))
        timerButtonView?.text = if (selected?.running == true) "STOP" else "START"
        timerButtonView?.isEnabled = selected != null
        timerEmptyView?.text = when {
            timerStore.all().isEmpty() -> "NO TIMERS — TAP A PRESET ABOVE TO ADD ONE"
            selected?.finished == true -> "\"${selected.name}\" FINISHED — RESET TO RUN IT AGAIN"
            else -> ""
        }
        for (entry in timerStore.all()) {
            val views = timerRowViews[entry.id] ?: continue
            val left = entry.remainingMillis(now)
            views.first.text = if (entry.finished) "DONE" else formatTimer(left, false)
            views.first.setTextColor(
                if (entry.finished || (left <= 10_000L && left > 0L)) colorDanger() else colorAccent()
            )
            val rowPercent = if (entry.totalMillis > 0) (left * 100 / entry.totalMillis).toInt() else 0
            views.second.setValue(rowPercent)
            views.second.setHot(entry.finished || (left <= 10_000L && left > 0L))
            views.third.text = if (entry.finished) "RESET" else if (entry.running) "STOP" else "START"
        }
    }

    private fun ringTimerAlarm(entry: TimerStore.TimerEntry) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 400, 200, 400, 200, 400), -1)
            }
        } catch (e: Exception) {

        }

        if (soundOn) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                handler.postDelayed({ try { tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600) } catch (e: Exception) { } }, 700)
                handler.postDelayed({ try { tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600) } catch (e: Exception) { } finally { tone.release() } }, 1400)
            } catch (e: Exception) {

            }
        }
        if (current == "timer") {
            AlertDialog.Builder(this)
                .setTitle("TIMER FINISHED")
                .setMessage("\"${entry.name}\" reached 00:00.")
                .setPositiveButton("OK", null)
                .setNegativeButton("RESET") { _, _ ->
                    timerStore.reset(entry.id, System.currentTimeMillis())
                    showScreen("timer")
                }
                .show()
        }

        if (!appInForeground) notificationHelper.notifyTimerFinished(entry.name)
    }

    private fun showMap() {
        screenBody.addView(title("地球", "WORLD MONITOR", "DRAG TO ROTATE"))
        val globe = GlobeView(this)
        mapGlobe = globe
        globe.background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), colorAccent()) }
        var lastX = 0f
        globe.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    lastX = event.x
                    globe.addRotation(dx * 0.01)
                    true
                }
                else -> false
            }
        }
        screenBody.addView(globe, LinearLayout.LayoutParams(-1, dp(360)).apply { bottomMargin = dp(10) })
        val box = borderedPanel()
        val coordRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        coordRow.addView(monoText("LAT / LON", 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        mapCoordView = monoText("--", 13f, colorAccent())
        coordRow.addView(mapCoordView)
        box.addView(coordRow)
        val accRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        accRow.addView(monoText("ACCURACY", 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        mapAccuracyView = monoText("--", 13f, colorAccent())
        accRow.addView(mapAccuracyView)
        box.addView(accRow)
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        statusRow.addView(monoText("STATUS", 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        mapStatusView = monoText("--", 13f, colorAccent())
        statusRow.addView(mapStatusView)
        box.addView(statusRow)
        val rotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rotRow.addView(monoText("ROT", 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        mapRotView = monoText("0.0°", 13f, colorAccent())
        rotRow.addView(mapRotView)
        box.addView(rotRow)
        screenBody.addView(box)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttonRow.addView(shellButton("CENTRE ON ME") {
            val fix = mapFix
            if (fix != null) {
                mapGlobe?.setYouMarker(fix.lat, fix.lon)
                mapGlobe?.centreOn(fix.lon)
                updateMapViews()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        if (!locationTracker.hasPermission()) {
            buttonRow.addView(shellButton("ENABLE LOCATION") {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    locationRequestCode,
                )
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        }
        screenBody.addView(buttonRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        screenBody.addView(monoText("Location stays on this phone — NERV never sends it anywhere. GPS runs only while MAP is open.", 11f, colorAccentSecondary()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })
        updateMapViews()
        startMapLocation()
    }

    private fun startMapLocation() {
        if (!::locationTracker.isInitialized || mapLocationStarted) return
        if (!locationTracker.hasPermission()) return
        mapLocationStarted = locationTracker.start { fix ->
            mapFix = fix
            if (current == "map") updateMapViews()
        }
    }

    private fun updateMapViews() {
        if (!::locationTracker.isInitialized) return
        val fix = mapFix
        val globe = mapGlobe
        if (fix != null) globe?.setYouMarker(fix.lat, fix.lon)
        mapCoordView?.text = if (fix != null) {
            "${LocationTracker.formatLat(fix.lat)}  ${LocationTracker.formatLon(fix.lon)}"
        } else "--"
        mapAccuracyView?.text = if (fix != null && fix.accuracyM >= 0) "±${fix.accuracyM.toInt()} m" else "--"
        mapStatusView?.text = when {
            !locationTracker.hasPermission() -> if (fix != null) "LAST KNOWN — PERMISSION REVOKED" else "PERMISSION NEEDED"
            fix == null -> if (locationTracker.isLocationEnabled()) "WAITING FOR FIX…" else "LOCATION OFF"
            fix.live -> "${fix.provider.uppercase(Locale.ROOT)} · LIVE · ${LocationTracker.formatAge(fix.timeMs)}"
            else -> "${fix.provider.uppercase(Locale.ROOT)} · LAST KNOWN · ${LocationTracker.formatAge(fix.timeMs)}"
        }
        val deg = globe?.rotationDegrees() ?: 0.0
        mapRotView?.text = String.format(Locale.getDefault(), "%.1f°", deg)
    }

    private fun showSetup() {
        screenBody.addView(title("設定", "SYSTEM SETUP", "DISPLAY"))
        val panel = borderedPanel()
        panel.addView(boxHeading("COLOR SCHEME"))

        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        palettes.forEachIndexed { index, (name, _) ->

            val selected = name == palette
            val button = if (selected) {
                shellButtonOn(name.uppercase(Locale.ROOT)) {
                    palette = name
                    displaySettings.palette = name
                    applyTheme()
                    showScreen("setup")
                }
            } else {
                shellButton(name.uppercase(Locale.ROOT)) {
                    palette = name
                    displaySettings.palette = name
                    applyTheme()
                    showScreen("setup")
                }
            }
            themeRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = if (index == palettes.size - 1) 0 else dp(4)
            })
        }
        panel.addView(themeRow)
        screenBody.addView(panel)

        val customBox = borderedPanel()
        customBox.addView(boxHeading("CUSTOM ACCENT"))
        val customName = displaySettings.customAccentName()
        customBox.addView(monoText(
            if (customAccent == 0) "Using the theme's own accent colour."
            else "Custom accent: $customName — the theme's second colour and dim stay as they are.",
            12f, colorAccent()))
        val customRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        DisplaySettings.CUSTOM_ACCENTS.forEachIndexed { index, (name, color) ->
            val label = if (customAccent == color) "● $name" else name
            customRow.addView(shellButton(label) {
                customAccent = color
                displaySettings.customAccent = color
                applyTheme()
                showScreen("setup")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {

                marginEnd = if (index == DisplaySettings.CUSTOM_ACCENTS.size - 1) 0 else dp(4)
            })
        }
        customBox.addView(customRow)
        customBox.addView(shellButton("THEME COLOUR — USE THE THEME'S OWN ACCENT") {
            customAccent = 0
            displaySettings.customAccent = 0
            applyTheme()
            showScreen("setup")
        })
        screenBody.addView(customBox)
        val display = borderedPanel()
        display.addView(boxHeading("DISPLAY"))
        display.addView(toggleRow("SCANLINES", if (scanOn) "ON" else "OFF") {
            scanOn = !scanOn
            displaySettings.scanlines = scanOn
            scanlinesView?.visibility = if (scanOn) View.VISIBLE else View.GONE
            showScreen("setup")
        })
        display.addView(toggleRow("FLICKER", if (reduceMotion) "OFF" else if (flickerOn) "ON" else "OFF") {

            if (reduceMotion) {
                AlertDialog.Builder(this)
                    .setTitle("FLICKER PAUSED")
                    .setMessage("Flicker is off while REDUCE MOTION is on (ACCESSIBILITY below). Turn off REDUCE MOTION to use flicker again.")
                    .setPositiveButton("OK", null)
                    .show()
                return@toggleRow
            }
            flickerOn = !flickerOn
            displaySettings.flicker = flickerOn
            if (!flickerOn) phoneFrame.alpha = 1f
            showScreen("setup")
        })

        display.addView(toggleRow("SOUND", if (soundOn) "ON" else "OFF") {
            soundOn = !soundOn
            displaySettings.sound = soundOn
            showScreen("setup")
        })

        display.addView(toggleRow("LOCK SCREEN", "LOCK") { showLock() })

        val textSizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        textSizeRow.addView(monoText("TEXT SIZE", 12f, colorAccent()))
        val textSizeValue = monoText("$textSizeSp", 12f, colorAccent())
        val textSizeBar = SeekBar(this).apply {
            max = DisplaySettings.MAX_TEXT_SIZE_SP - DisplaySettings.MIN_TEXT_SIZE_SP
            progress = textSizeSp - DisplaySettings.MIN_TEXT_SIZE_SP
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    textSizeValue.text = "${progress + DisplaySettings.MIN_TEXT_SIZE_SP}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) { }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    textSizeSp = seekBar.progress + DisplaySettings.MIN_TEXT_SIZE_SP
                    displaySettings.textSizeSp = textSizeSp
                    showScreen("setup")
                }
            })
        }
        textSizeRow.addView(textSizeBar, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(8); marginEnd = dp(8)
        })
        textSizeRow.addView(textSizeValue)
        display.addView(textSizeRow)
        screenBody.addView(display)

        val accessBox = borderedPanel()
        accessBox.addView(boxHeading("ACCESSIBILITY"))
        accessBox.addView(shellButton("HIGH CONTRAST: ${if (highContrast) "ON" else "OFF"}") {
            highContrast = !highContrast
            displaySettings.highContrast = highContrast
            applyTheme()
            showScreen("setup")
        })
        accessBox.addView(shellButton("REDUCE MOTION: ${if (reduceMotion) "ON" else "OFF"}") {
            reduceMotion = !reduceMotion
            displaySettings.reduceMotion = reduceMotion
            applyReduceMotion()

            showScreen("setup")
        })

        accessBox.addView(shellButton("BIGGER TEXT: ${if (textBoost) "ON" else "OFF"}") {
            textBoost = !textBoost
            displaySettings.textBoost = textBoost
            showScreen("setup")
        })

        accessBox.addView(shellButton("TALKBACK CHECK — SPEAK A TEST LINE") { button ->
            AccessibilityHelper.announce(
                button,
                "NERV accessibility check. TalkBack is reading NERV. Every button says what it does."
            )
        })
        accessBox.addView(monoText(
            "Screen reader labels: ON — every button says what it does, the nav tabs, HOME tiles and lock vote boxes are labelled, and the globe, waves and bars say what they are. The hexagon pattern and scanlines are silent decoration, so TalkBack skips them. HIGH CONTRAST brightens dim text and borders to the full theme colour on every screen. REDUCE MOTION stills the flicker, the globe spin and the SYNC waves. BIGGER TEXT makes body text 25% bigger on top of the TEXT SIZE slider above. TalkBack itself is switched on in Android Settings, never by NERV. Not yet tested with TalkBack on a real phone — the labels are in place, but the swipe order still needs checking there.",
            11f, colorAccentSecondary()))
        accessBox.addView(monoText(
            if (AccessibilityHelper.screenReaderOn(this)) "TalkBack is ON right now on this phone."
            else "TalkBack is OFF right now — turn it on in Android Settings > Accessibility > TalkBack to hear NERV read out loud.",
            11f, colorAccent()))
        screenBody.addView(accessBox)
        val purity = borderedPanel()
        purity.addView(boxHeading("L.C.L. PURITY"))
        val purityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        purityRow.addView(monoText("99.9999989%", 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        purityRow.addView(monoText("ELAPSED 120 min", 13f, colorAccent()))
        purity.addView(purityRow)
        screenBody.addView(purity)
        val providerBox = borderedPanel()
        providerBox.addView(boxHeading("AI PROVIDER (SHARED)"))
        providerBox.addView(monoText(agentBridge.providerLabel(), 12f, colorAccent()))
        providerBox.addView(monoText("One shared config. The key is encrypted in Android Keystore — never in a file, never in a chat.", 11f, colorAccentSecondary()))
        providerBox.addView(shellButton("CONFIGURE PROVIDER") { showProviderDialog() })
        providerBox.addView(shellButton("FORGET KEY") {
            agentBridge.forgetProvider()
            showScreen("setup")
        })
        screenBody.addView(providerBox)

        val nodesBox = borderedPanel()
        nodesBox.addView(boxHeading("MAGI NODE LINKS"))
        magiEngine.nodes.forEach { node ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val nodeName = magiEngine.nodeNames[node.id] ?: node.id.name
            row.addView(monoText(nodeName, 12f, if (node.enabled) AgentTheme.signal else colorDanger()).apply {
                minWidth = dp(110)
                isClickable = true
                setOnClickListener { node.enabled = !node.enabled; magiEngine.saveNode(node); showScreen("setup") }
            })
            val providerNames = mapOf(
                MagiEngine.ProviderKind.CLAUDE_BUILTIN to "CLAUDE",
                MagiEngine.ProviderKind.ANTHROPIC to "ANTHROPIC",
                MagiEngine.ProviderKind.OPENAI to "CHATGPT",
                MagiEngine.ProviderKind.XAI to "GROK",
                MagiEngine.ProviderKind.LOCAL to "LOCAL"
            )
            row.addView(shellButton(providerNames[node.provider] ?: "CLAUDE") {
                val all = MagiEngine.ProviderKind.values()
                node.provider = all[(all.indexOf(node.provider) + 1) % all.size]
                magiEngine.saveNode(node)
                showScreen("setup")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            row.addView(monoText(if (node.enabled) "ON" else "OFF", 11f, if (node.enabled) AgentTheme.signal else colorDanger()))
            nodesBox.addView(row)
            val modelInput = EditText(this).apply {
                hint = when (node.provider) {
                    MagiEngine.ProviderKind.LOCAL -> "model, e.g. llama3.2 (must be pulled in Ollama)"
                    MagiEngine.ProviderKind.ANTHROPIC -> "model, e.g. claude-sonnet-5-5 (blank = claude-sonnet-5-5)"
                    else -> "model (blank = shared model)"
                }
                setText(node.model)
                maxLines = 1
                htmlInputStyle(this)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus && text.toString() != node.model) {
                        node.model = text.toString().trim()
                        magiEngine.saveNode(node)
                    }
                }
            }
            nodesBox.addView(modelInput)

            if (node.provider == MagiEngine.ProviderKind.LOCAL) {
                val endpointInput = EditText(this).apply {
                    hint = "endpoint, e.g. http://localhost:11434/v1"
                    setText(node.endpoint)
                    maxLines = 1
                    htmlInputStyle(this)
                    setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus && text.toString() != node.endpoint) {
                            node.endpoint = text.toString().trim()
                            magiEngine.saveNode(node)
                        }
                    }
                }
                nodesBox.addView(endpointInput)
            }

            val keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val keySet = nodeKeyStore.hasKey(node.id)
            keyRow.addView(monoText(if (keySet) "KEY SET" else "NO KEY", 11f, if (keySet) AgentTheme.signal else colorAccentSecondary()), LinearLayout.LayoutParams(0, -2, 1f))
            keyRow.addView(shellButton("SET KEY") { showNodeKeyDialog(node) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            if (keySet) keyRow.addView(shellButton("FORGET") {
                nodeKeyStore.clear(node.id)
                showScreen("setup")
            })
            nodesBox.addView(keyRow)
        }
        nodesBox.addView(monoText("Tap a node name to switch it on/off. Tap its provider to cycle CLAUDE / ANTHROPIC / CHATGPT / GROK / LOCAL. A node with its own key uses that key; without one it falls back to the shared provider key above — except ANTHROPIC, which only ever uses its own key, so an Anthropic key is never sent to another provider. ANTHROPIC really calls api.anthropic.com (blank model = claude-sonnet-5-5); CLAUDE (built-in) has no on-device link, so pick ANTHROPIC for real Claude answers. A LOCAL node really talks to Ollama on this device (http://localhost:11434/v1) — plain http is allowed only to localhost. It needs a model name already pulled in Ollama; LIST OLLAMA MODELS in PORTS below shows what is installed. No key is needed for Ollama.", 11f, colorAccentSecondary()))
        screenBody.addView(nodesBox)

        val packsBox = borderedPanel()
        packsBox.addView(boxHeading("PACKS"))
        packManager.allPacks().forEach { pack ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(pack.name, 12f, if (pack.enabled) AgentTheme.signal else colorDanger()).apply {
                minWidth = dp(120)
            })
            row.addView(monoText(
                if (!pack.implemented) "NOT WIRED YET" else if (pack.enabled) "LOADED" else "UNLOADED",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(0, -2, 1f))
            if (pack.implemented) {
                row.addView(shellButton(if (pack.enabled) "UNLOAD" else "LOAD") {
                    packManager.toggle(pack.id)
                    showScreen("setup")
                })
            }
            if (pack.custom) {
                row.addView(shellButton("DELETE") { showDeletePackDialog(pack) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            }
            packsBox.addView(row)
            packsBox.addView(monoText(
                (if (pack.custom) "CUSTOM — " else "") + pack.description,
                10f, colorAccentSecondary()))
        }
        packsBox.addView(shellButton("ADD PACK") { showAddPackDialog() })
        packsBox.addView(monoText("A custom pack only bundles tools NERV already has — it never adds new code or runs anything new. Load it when you need the group, unload it when you don't. A tool stays available while any pack holding it is loaded.", 11f, colorAccentSecondary()))
        screenBody.addView(packsBox)

        showPackStoreBox()

        val taughtBox = borderedPanel()
        taughtBox.addView(boxHeading("TAUGHT SKILLS"))
        if (taughtSkillStore.all().isEmpty()) {
            taughtBox.addView(monoText("Nothing taught yet — teach MAGI in CHAT, or tap TEACH MAGI below.", 11f, colorAccentSecondary()))
        }
        taughtSkillStore.all().forEach { skill ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(skill.name, 12f, if (skill.enabled) AgentTheme.signal else colorDanger()).apply {
                minWidth = dp(120)
            })
            row.addView(monoText(
                if (skill.enabled) "LOADED" else "UNLOADED",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(shellButton(if (skill.enabled) "UNLOAD" else "LOAD") {
                taughtSkillStore.toggle(skill.id)
                showScreen("setup")
            })
            row.addView(shellButton("DELETE") { showDeleteTaughtSkillDialog(skill) },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            taughtBox.addView(row)
            taughtBox.addView(monoText(
                "TAUGHT — " + skill.instruction +
                (if (skill.tools.isEmpty()) "" else " [tools: ${skill.tools.joinToString(", ")}]") +
                (if (skill.taughtAt.isBlank()) "" else " · ${skill.taughtAt}"),
                10f, colorAccentSecondary()))
        }
        taughtBox.addView(shellButton("TEACH MAGI") { showTeachDialog() })
        taughtBox.addView(monoText("A taught skill is an instruction MAGI follows when it is loaded — it never adds new code, never downloads anything, never holds a secret. Teach in CHAT with TEACH MAGI, or type \"teach MAGI…\" and CHAT will offer to save it. A skill that names a tool still needs that tool's pack loaded to run it.", 11f, colorAccentSecondary()))
        screenBody.addView(taughtBox)

        val agentBox = borderedPanel()
        agentBox.addView(boxHeading("AGENT CONTROL"))
        agentBox.addView(monoText(agentBridge.status(), 12f, colorAccent()))

        val latestTask = phoneTaskStore.latest()
        agentBox.addView(monoText(
            if (latestTask == null) "PHONE TASK: none yet — a MAGI decision that needs the phone will appear in CHAT for approval."
            else "PHONE TASK: ${latestTask.status} — ${latestTask.objective.take(70)}",
            12f, colorAccentSecondary()))
        agentBox.addView(monoText(
            if (agentBridge.controlActive()) "TAKE CONTROL is ON — 5 min session." else "TAKE CONTROL is OFF.",
            12f, if (agentBridge.controlActive()) AgentTheme.signal else colorDanger()))
        agentBox.addView(shellButton("ENABLE ACCESSIBILITY") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        agentBox.addView(shellButton("TAKE CONTROL (5 MIN)") {
            AlertDialog.Builder(this)
                .setTitle("Allow five minutes of phone control?")
                .setMessage("For five minutes, an AI task can request a bounded view of visible app labels and buttons. Every tap, scroll, text entry or navigation still requires your separate on-screen approval. You can press STOP at any time.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("TAKE CONTROL") { _, _ ->
                    val result = agentBridge.startTakeControl()
                    AlertDialog.Builder(this).setTitle("TAKE CONTROL").setMessage(result)
                        .setPositiveButton("OK") { _, _ -> showScreen("setup") }.show()
                }
                .show()
        })
        agentBox.addView(shellButton("STOP AGENT + CONTROL") {
            agentBridge.stop()
            agentBridge.stopTakeControl()
            showScreen("setup")
        })
        screenBody.addView(agentBox)
        showConnectionsBox()
        showPortsBox()
        showVoiceBox()
        showWakeWordBox()
        showVoiceWidgetBox()
        showLockWidgetsBox()
        showTileBox()
        showShortcutsBox()
        showShareBox()
        showBatterySaverBox()
        showMagiVoicesBox()
        showSmartRepliesBox()
        showNotificationsBox()
        showOfflineBox()
        showLockSecurityBox()
        showFoldBox()
        showTutorialBox()
        showDailyBriefingBox()
        showRoutinesBox()
        showCalendarBox()
        showContactsBox()
        showFilesBox()
        showBackupBox()
        showSyncBox()
        screenBody.addView(shellButton("LOCK SCREEN") { showLock() })
    }

    private fun showFoldBox() {
        val box = borderedPanel()
        box.addView(boxHeading("FOLD PHONE"))
        box.addView(monoText(foldSupport.stateLabel(this), 12f, colorAccent()))
        box.addView(shellButton("DUAL-PANE: ${if (foldSupport.dualPaneEnabled) "ON — TAP TO TURN OFF" else "OFF — TAP TO TURN ON"}") {
            foldSupport.dualPaneEnabled = !foldSupport.dualPaneEnabled
            showScreen("setup")
        })
        box.addView(monoText("Not tested on a real folding phone yet — this layout was written from the Android fold APIs and needs a real foldable to check: fold it, unfold it, and the layout should flip between one column and two. On an unfolded phone, MAGI splits into the debate on one half and the live phone view on the other — drag the bar between them to resize, and NERV remembers the split. Needs android:configChanges set in AndroidManifest.xml (screenSize, smallestScreenSize, screenLayout, orientation).", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showBackupBox() {
        val box = borderedPanel()
        box.addView(boxHeading("BACKUP — EXPORT EVERYTHING"))
        val now = nervBackup.summaryNow()
        box.addView(monoText(
            "On this phone now: ${backupSummaryText(now)}.",
            12f, colorAccent()))
        box.addView(shellButton("EXPORT BACKUP") { exportBackup() })
        box.addView(shellButton("RESTORE BACKUP") { pickBackupToRestore() })
        box.addView(monoText("One file holds it all — chats, notes, packs, taught skills, timers, routines and settings. Your API keys and lock PIN are never in the file, and a restore never touches them. Restoring asks first, then replaces what is on this phone now. Backups live in the backups folder — see FILES above.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun backupSummaryText(s: NervBackup.Summary): String =
        "${s.chats} chats · ${s.notes} notes · ${s.customPacks} custom packs · ${s.taughtSkills} taught skills · ${s.routines} routines · ${s.timers} timers"

    private fun exportBackup() {
        val file = nervBackup.export()
        if (file == null) {
            AlertDialog.Builder(this).setTitle("Could not export")
                .setMessage("The backup could not be written. Try again — if it keeps failing, check the backups folder in FILES above.")
                .setPositiveButton("OK", null).show()
            return
        }
        val summary = nervBackup.summaryNow()
        AlertDialog.Builder(this)
            .setTitle("Backup exported")
            .setMessage("Saved as ${file.name} in the backups folder.\n\n${backupSummaryText(summary)}.\n\nNo API key and no lock PIN is in the file.")
            .setPositiveButton("OK", null)
            .setNegativeButton("SHARE") { _, _ -> shareBackup(file) }
            .show()
    }

    private fun shareBackup(file: java.io.File) {
        val text = nervBackup.readBackup(file)
        if (text == null) {
            AlertDialog.Builder(this).setTitle("Could not share")
                .setMessage("That backup is too big to share as text, or cannot be read. Find it in SETUP > FILES > backups and share it from there.")
                .setPositiveButton("OK", null).show()
            return
        }
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, "SHARE BACKUP"))
        } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Could not share")
                .setMessage("No app on this phone can take the backup right now.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun pickBackupToRestore() {
        val backups = nervBackup.listBackups()
        if (backups.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No backups yet")
                .setMessage("Tap EXPORT BACKUP first — a backup is saved in the backups folder, then you can restore from it.")
                .setPositiveButton("OK", null).show()
            return
        }
        val names = backups.map { "${it.name} (${it.length() / 1024} KB)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Restore from a backup")
            .setItems(names) { _, which ->
                confirmRestore(backups[which])
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmRestore(file: java.io.File) {
        val check = nervBackup.validate(file)
        if (!check.ok || check.summary == null) {
            AlertDialog.Builder(this).setTitle("Not a NERV backup")
                .setMessage(check.error ?: "That file cannot be restored.")
                .setPositiveButton("OK", null).show()
            return
        }
        val summary = check.summary
        AlertDialog.Builder(this)
            .setTitle("Restore this backup?")
            .setMessage(
                "This backup holds:\n${backupSummaryText(summary)}.\n\n" +
                "Restoring REPLACES what is on this phone now — your current chats, notes, packs, taught skills, timers, routines and settings will be replaced by the backup's. " +
                "Your API keys and lock PIN are not in the backup and are not touched.\n\n" +
                "Export a fresh backup first if you want to keep what is here now."
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("RESTORE") { _, _ -> doRestore(file) }
            .show()
    }

    private fun doRestore(file: java.io.File) {
        val result = nervBackup.restore(file)
        if (!result.ok || result.summary == null) {
            AlertDialog.Builder(this).setTitle("Restore failed")
                .setMessage(result.error ?: "The backup could not be restored.")
                .setPositiveButton("OK", null).show()
            return
        }

        palette = displaySettings.palette
        customAccent = displaySettings.customAccent
        scanOn = displaySettings.scanlines
        flickerOn = displaySettings.flicker
        soundOn = displaySettings.sound
        textSizeSp = displaySettings.textSizeSp
        textBoost = displaySettings.textBoost
        highContrast = displaySettings.highContrast
        reduceMotion = displaySettings.reduceMotion
        timerSelectedId = null
        scanlinesView?.visibility = if (scanOn) View.VISIBLE else View.GONE
        applyTheme()
        applyReduceMotion()

        batterySaver.check(this)
        applyBatterySaver()
        AlertDialog.Builder(this).setTitle("Backup restored")
            .setMessage("Restored:\n${backupSummaryText(result.summary)}.\n\nYour API keys and lock PIN were not touched.")
            .setPositiveButton("OK") { _, _ -> showScreen("setup") }
            .show()
    }

    private fun showSyncBox() {
        val box = borderedPanel()
        box.addView(boxHeading("MULTI-DEVICE SYNC — MANUAL SYNC FOR NOW, NO SERVER"))
        val now = nervBackup.summaryNow()
        box.addView(monoText(
            "What would sync from this phone: ${backupSummaryText(now)}.",
            12f, colorAccent()))
        box.addView(monoText(deviceSync.lastSyncLabel(), 12f, colorAccent()))
        box.addView(shellButton("EXPORT SYNC FILE") { exportSyncFile() })
        box.addView(shellButton("IMPORT SYNC FILE") { pickSyncFileToImport() })
        box.addView(monoText(DeviceSync.NO_SERVER_TEXT, 11f, colorAccentSecondary()))
        box.addView(monoText(DeviceSync.MERGE_RULE_TEXT, 11f, colorAccentSecondary()))
        box.addView(monoText("Sync files live in the sync folder — see FILES above. A file shared into NERV from another app lands there too, ready to import.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun exportSyncFile() {
        val file = deviceSync.export()
        if (file == null) {
            AlertDialog.Builder(this).setTitle("Could not export")
                .setMessage("The sync file could not be written. Try again — if it keeps failing, check the sync folder in FILES above.")
                .setPositiveButton("OK", null).show()
            return
        }
        val summary = nervBackup.summaryNow()
        AlertDialog.Builder(this)
            .setTitle("Sync file exported")
            .setMessage("Saved as ${file.name} in the sync folder.\n\n${backupSummaryText(summary)}.\n\nNo API key and no lock PIN is in the file. Share it to your other phone, then share it into NERV there and tap IMPORT SYNC FILE.")
            .setPositiveButton("OK", null)
            .setNegativeButton("SHARE") { _, _ -> shareSyncFile(file) }
            .show()
    }

    private fun shareSyncFile(file: java.io.File) {
        val text = deviceSync.read(file)
        if (text == null) {
            AlertDialog.Builder(this).setTitle("Could not share")
                .setMessage("That sync file is too big to share as text, or cannot be read. Find it in SETUP > FILES > sync and share it from there.")
                .setPositiveButton("OK", null).show()
            return
        }
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, "SHARE SYNC FILE"))
        } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Could not share")
                .setMessage("No app on this phone can take the sync file right now.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun pickSyncFileToImport() {
        val files = deviceSync.listFiles()
        if (files.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No sync files yet")
                .setMessage("Tap EXPORT SYNC FILE first — or, on this phone, share a sync file sent from your other phone into NERV and it lands here, ready to import.")
                .setPositiveButton("OK", null).show()
            return
        }
        val names = files.map { "${it.name} (${it.length() / 1024} KB)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Import a sync file")
            .setItems(names) { _, which ->
                confirmSyncImport(files[which])
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmSyncImport(file: java.io.File) {
        val check = deviceSync.validate(file)
        if (!check.ok || check.summary == null) {
            AlertDialog.Builder(this).setTitle("Not a NERV sync file")
                .setMessage(check.error ?: "That file cannot be merged.")
                .setPositiveButton("OK", null).show()
            return
        }
        val summary = check.summary
        AlertDialog.Builder(this)
            .setTitle("Merge this sync file?")
            .setMessage(
                "This sync file holds:\n${backupSummaryText(summary)}.\n\n" +
                "Merging KEEPS both sides — it does not replace what is on this phone. " +
                "Newest wins per item, and nothing is silently deleted: a message only counts as deleted when it is deleted on both phones. " +
                "Your API keys and lock PIN are not in the file and are not touched."
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("MERGE") { _, _ -> doSyncImport(file) }
            .show()
    }

    private fun doSyncImport(file: java.io.File) {
        val result = deviceSync.merge(file)
        if (!result.ok || result.summary == null) {
            AlertDialog.Builder(this).setTitle("Sync failed")
                .setMessage(result.error ?: "The sync file could not be merged.")
                .setPositiveButton("OK", null).show()
            return
        }
        deviceSync.markSynced(result.summary)

        palette = displaySettings.palette
        customAccent = displaySettings.customAccent
        scanOn = displaySettings.scanlines
        flickerOn = displaySettings.flicker
        soundOn = displaySettings.sound
        textSizeSp = displaySettings.textSizeSp
        textBoost = displaySettings.textBoost
        highContrast = displaySettings.highContrast
        reduceMotion = displaySettings.reduceMotion
        timerSelectedId = null
        scanlinesView?.visibility = if (scanOn) View.VISIBLE else View.GONE
        applyTheme()
        applyReduceMotion()
        batterySaver.check(this)
        applyBatterySaver()
        AlertDialog.Builder(this).setTitle("Sync merged")
            .setMessage("This phone now holds:\n${backupSummaryText(result.summary)}.\n\nYour API keys and lock PIN were not touched.")
            .setPositiveButton("OK") { _, _ -> showScreen("setup") }
            .show()
    }

    private fun maybeShowFirstRunTutorial() {
        if (tutorialShownThisRun) return
        tutorialShownThisRun = true
        if (!tutorialGuide.isFirstRun()) return
        showTutorialWelcome()
    }

    private fun showTutorialWelcome() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("WELCOME TO NERV")
            .setMessage(
                "A quick tour takes about 2 minutes: all 9 screens, then " +
                    "how to link each MAGI node's API key step by step. " +
                    "You can replay it any time in SETUP > TUTORIAL."
            )
            .setPositiveButton("START TOUR") { _, _ -> showTourStep(0) }
            .setNegativeButton("SKIP") { _, _ -> tutorialGuide.markSeen() }
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showTourStep(index: Int) {
        val steps = tutorialGuide.tourSteps
        val step = steps[index]
        val builder = AlertDialog.Builder(this)
            .setTitle("${step.glyph} ${step.title}")
            .setMessage("${step.body}\n\nStep ${index + 1} of ${steps.size}.")
            .setNeutralButton("GO TO SCREEN") { _, _ ->
                tutorialGuide.markSeen()
                showScreen(step.screenKey)
            }
        if (index < steps.size - 1) {
            builder.setPositiveButton("NEXT") { _, _ -> showTourStep(index + 1) }
        } else {
            builder.setPositiveButton("LINK API KEYS") { _, _ -> showKeyStep(0) }
        }
        if (index > 0) {
            builder.setNegativeButton("BACK") { _, _ -> showTourStep(index - 1) }
        } else {
            builder.setNegativeButton("SKIP TOUR") { _, _ -> tutorialGuide.markSeen() }
        }
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showKeyStep(index: Int) {
        val steps = tutorialGuide.keySteps
        val step = steps[index]
        val keyState = if (nodeKeyStore.hasKey(step.node)) "KEY SET" else "NO KEY"
        val builder = AlertDialog.Builder(this)
            .setTitle("${step.nodeName} — ${step.providerName}")
            .setMessage(
                "KEY STATUS NOW: $keyState (checked just now — the key " +
                    "itself is never shown on screen).\n\n" +
                    "1. WHERE TO GET IT\n${step.whereToGet}\n\n" +
                    "2. WHERE TO PASTE IT\n${step.whereToPaste}\n\n" +
                    "3. HOW TO TELL IT WORKED\n${step.howToTell}\n\n" +
                    "Step ${index + 1} of ${steps.size}."
            )
            .setNeutralButton("TEST DEBATE") { _, _ -> runTutorialTestDebate() }
        if (index < steps.size - 1) {
            builder.setPositiveButton("NEXT NODE") { _, _ -> showKeyStep(index + 1) }
        } else {
            builder.setPositiveButton("FINISH") { _, _ -> tutorialGuide.markSeen() }
        }
        if (index > 0) {
            builder.setNegativeButton("BACK") { _, _ -> showKeyStep(index - 1) }
        } else {
            builder.setNegativeButton("CLOSE") { _, _ -> tutorialGuide.markSeen() }
        }
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun runTutorialTestDebate() {
        if (tutorialTestRunning) return
        tutorialTestRunning = true
        Thread {
            val result = try {
                magiEngine.converse("Reply with one short sentence saying hello.", emptyList())
            } catch (e: Exception) {
                MagiEngine.MagiResult(
                    "Test debate could not run: ${e.message ?: "unknown error"}",
                    listOf("SYSTEM: ${e.message}"), emptyList(), localDemo = true
                )
            }
            runOnUiThread {
                tutorialTestRunning = false
                val message = if (result.localDemo) {
                    "LOCAL DEMO — the key did not work, or no node is " +
                        "switched on. Check SETUP > MAGI NODE LINKS: the " +
                        "node needs to be ON and say KEY SET.\n\n" +
                        result.text.take(200)
                } else {
                    "LIVE ANSWER — the key works. MAGI answered for real:\n\n" +
                        result.text.take(200)
                }
                val dialog = AlertDialog.Builder(this)
                    .setTitle("TEST DEBATE RESULT")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .create()
                dialog.show()
                dialog.window?.setBackgroundDrawable(
                    GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
                )
            }
        }.start()
    }

    private fun showTutorialBox() {
        val box = borderedPanel()
        box.addView(boxHeading("TUTORIAL"))
        box.addView(monoText(
            if (tutorialGuide.isFirstRun()) "NOT SEEN YET — it shows on the first visit to HOME."
            else "SEEN — replay it any time from here.",
            12f, colorAccent()
        ))
        box.addView(shellButton("REPLAY TOUR — ALL 9 SCREENS") {

            showTourStep(0)
        })
        box.addView(shellButton("LINK API KEYS — STEP BY STEP") { showKeyStep(0) })
        screenBody.addView(box)
    }

    private fun maybeShowDailyBriefing() {
        if (tutorialGuide.isFirstRun()) return
        if (!dailyBriefing.shouldShowToday()) return
        dailyBriefing.markShownToday()
        showBriefingDialog()
    }

    private fun showBriefingDialog() {

        val text = dailyBriefing.build(phoneTaskStore, timerStore, noteStore, magiMemory, calendarLink.briefingLines())
        val dialog = AlertDialog.Builder(this)
            .setTitle("DAILY BRIEFING")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showDailyBriefingBox() {
        val box = borderedPanel()
        box.addView(boxHeading("DAILY BRIEFING"))
        box.addView(monoText(
            "Morning briefing: ${if (dailyBriefing.enabled) "ON" else "OFF"} — last shown ${dailyBriefing.lastShownDate()}. " +
                "Shows once a day from 6am on the first visit to HOME.",
            12f, colorAccent()
        ))
        box.addView(shellButton("VIEW TODAY'S BRIEFING") { showBriefingDialog() })
        box.addView(shellButton("BRIEFING: ${if (dailyBriefing.enabled) "ON — TAP TO TURN OFF" else "OFF — TAP TO TURN ON"}") {
            dailyBriefing.enabled = !dailyBriefing.enabled
            showScreen("setup")
        })
        screenBody.addView(box)
    }

    private fun showCalendarBox() {
        val box = borderedPanel()
        box.addView(boxHeading("CALENDAR"))
        val read = calendarLink.hasReadPermission()
        val write = calendarLink.hasWritePermission()
        box.addView(monoText(
            "Read: ${if (read) "ALLOWED" else "OFF"} · Add events: ${if (write) "ALLOWED" else "OFF"}",
            12f, if (read) AgentTheme.signal else colorDanger()
        ))
        if (!read || !write) {
            box.addView(shellButton("ALLOW CALENDAR ACCESS") {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                    calendarRequestCode
                )
            })
        }
        if (read) {
            val events = calendarLink.today()
            if (events.isEmpty()) {
                box.addView(monoText("Nothing in the calendar today.", 12f, colorAccent()))
            } else {
                box.addView(monoText("TODAY", 12f, colorAccentSecondary()))
                events.forEach { event ->
                    box.addView(monoText("· ${CalendarLink.lineFor(event)}", 12f, colorAccent()))
                }
            }
            calendarLink.nextEvent()?.let { event ->
                if (!CalendarLink.isToday(event.startMs)) {
                    box.addView(monoText("NEXT: ${CalendarLink.lineFor(event)}", 12f, colorAccentSecondary()))
                }
            }
        } else {
            box.addView(monoText("MAGI can read the calendar and remind you about events once you allow it — and it can suggest adding an event, but one is only added after you approve it here. Nothing from the calendar is ever sent anywhere.", 11f, colorAccentSecondary()))
        }
        box.addView(shellButton("ADD EVENT") { showAddCalendarEventDialog("") })
        screenBody.addView(box)
    }

    private fun showCalendarEventProposalDialog(title: String, onDismiss: () -> Unit = {}) {
        showAddCalendarEventDialog(title, onDismiss)
    }

    private fun showAddCalendarEventDialog(prefillTitle: String, onDismiss: () -> Unit = {}) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val titleInput = EditText(this).apply {
            hint = "event title"
            setText(prefillTitle)
            maxLines = 1
            AgentTheme.style(this)
        }
        val dateInput = EditText(this).apply {
            hint = "date — yyyy-MM-dd"
            setText(CalendarLink.todayDateString())
            maxLines = 1
            AgentTheme.style(this)
        }
        val timeInput = EditText(this).apply {
            hint = "start time — HH:mm, e.g. 10:30"
            setText("10:00")
            maxLines = 1
            AgentTheme.style(this)
        }
        val durationInput = EditText(this).apply {
            hint = "length in minutes, e.g. 60"
            setText("60")
            maxLines = 1
            inputType = InputType.TYPE_CLASS_NUMBER
            AgentTheme.style(this)
        }
        layout.addView(monoText("TITLE", 11f, colorAccentSecondary()))
        layout.addView(titleInput)
        layout.addView(monoText("DATE", 11f, colorAccentSecondary()))
        layout.addView(dateInput)
        layout.addView(monoText("START TIME", 11f, colorAccentSecondary()))
        layout.addView(timeInput)
        layout.addView(monoText("LENGTH (MINUTES)", 11f, colorAccentSecondary()))
        layout.addView(durationInput)

        var dismissRan = false
        val runDismissOnce = { if (!dismissRan) { dismissRan = true; onDismiss() } }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (prefillTitle.isBlank()) "ADD EVENT" else "ADD THIS EVENT?")
            .setMessage(
                if (prefillTitle.isBlank()) "Check the details, then tap ADD. The event goes in the phone's main calendar."
                else "MAGI suggested adding this event. Check the date, start time and length — nothing is added until you tap ADD."
            )
            .setView(layout)
            .setPositiveButton("ADD") { _, _ ->
                val start = CalendarLink.parseDateTime(dateInput.text.toString(), timeInput.text.toString())
                val duration = durationInput.text.toString().trim().toIntOrNull() ?: 60
                if (start == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Date or time did not parse")
                        .setMessage("Use yyyy-MM-dd for the date and HH:mm for the time, e.g. 2026-11-20 and 10:30.")
                        .setPositiveButton("OK") { _, _ -> runDismissOnce() }
                        .show()
                    return@setPositiveButton
                }
                val error = calendarLink.addEvent(titleInput.text.toString(), start, duration)
                AlertDialog.Builder(this)
                    .setTitle(if (error == null) "EVENT ADDED" else "COULD NOT ADD")
                    .setMessage(error ?: "Added to the calendar: ${titleInput.text} — ${dateInput.text} ${timeInput.text}.")
                    .setPositiveButton("OK") { _, _ ->
                        runDismissOnce()
                        if (current == "setup") showScreen("setup")
                    }
                    .show()
            }
            .setNegativeButton("CANCEL") { _, _ -> runDismissOnce() }
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showContactsBox() {
        val box = borderedPanel()
        box.addView(boxHeading("CONTACTS"))
        val allowed = contactLookup.hasPermission()
        box.addView(monoText(
            "Read contacts: ${if (allowed) "ALLOWED" else "OFF"}",
            12f, if (allowed) AgentTheme.signal else colorDanger()
        ))
        box.addView(monoText(
            "MAGI looks a contact up before a call or message task, so it dials the right person. A lookup shows a name and a number here only — it is never saved to a chat, a note, a report or a debate, and it is never sent anywhere.",
            11f, colorAccentSecondary()
        ))
        if (!allowed) {
            box.addView(shellButton("ALLOW CONTACTS ACCESS") {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    contactsRequestCode
                )
            })
        } else {
            box.addView(shellButton("LOOK UP A CONTACT") { showContactLookupDialog() })
        }
        screenBody.addView(box)
    }

    private fun showContactLookupDialog() {
        val input = EditText(this).apply {
            hint = "name to look up"
            maxLines = 1
            AgentTheme.style(this)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("LOOK UP A CONTACT")
            .setMessage("Shows the name and number here only. Nothing is saved, and nothing is sent anywhere.")
            .setView(input)
            .setPositiveButton("LOOK UP") { _, _ ->
                val name = input.text.toString().trim()
                val matches = contactLookup.lookup(name)
                val text = when {
                    name.isEmpty() -> "Type a name first."
                    matches.isEmpty() -> "No contact matches \"$name\"."
                    else -> matches.joinToString("\n\n") { ContactLookup.lineFor(it) }
                }
                val results = AlertDialog.Builder(this)
                    .setTitle("CONTACTS — ${matches.size} MATCH${if (matches.size == 1) "" else "ES"}")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .create()
                results.show()
                results.window?.setBackgroundDrawable(
                    GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
                )
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun runDueRoutines() {
        if (!::routineStore.isInitialized) return
        val due = routineStore.due()
        if (due.isEmpty()) return
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        due.forEach { routine ->
            routineStore.markRun(routine.id, routineStore.todayDate())
            Thread {
                val result = try {
                    magiEngine.converse(routine.prompt, chatStore.history(), magiMemory.memoryContext())
                } catch (e: Exception) {
                    MagiEngine.MagiResult(
                        "Routine \"${routine.name}\" could not run: ${e.message ?: "unknown error"}",
                        listOf("SYSTEM: ${e.message}"), emptyList(), localDemo = true
                    )
                }
                magiMemory.rememberDebate("ROUTINE: ${routine.name} — ${routine.prompt}", result)

                debateHistory.record("ROUTINE: ${routine.name} — ${routine.prompt}", result)
                chatStore.add(
                    ChatStore.Entry(
                        ChatStore.Role.AI, result.text, now,
                        sender = "ROUTINE: ${routine.name}",
                        votes = result.votes, log = result.log
                    )
                )
                runOnUiThread {
                    NervVoiceWidget.saveReply(this, result.text)
                    NervVoiceWidget.updateAll(this)
                    if (current == "comms") showScreen("comms")
                    val proposed = result.phoneTask?.let { phoneTaskStore.propose(it, routine.prompt) }
                    if (proposed != null && !appInForeground) {
                        notificationHelper.notifyApprovalNeeded(proposed.objective)
                    } else if (proposed != null && current == "comms") {
                        showPhoneTaskProposalDialog(proposed)
                    }
                    if (!appInForeground) {
                        notificationHelper.notifyRoutineDone(routine.name)
                    }
                }
            }.start()
        }
    }

    private fun showRoutinesBox() {
        val box = borderedPanel()
        box.addView(boxHeading("ROUTINES — SCHEDULED MAGI TASKS"))
        box.addView(monoText(
            "A routine runs its saved prompt at its set time. It only runs after you approve it once — a new routine never runs on its own.",
            12f, colorAccent()
        ))
        val routines = routineStore.all()
        if (routines.isEmpty()) {
            box.addView(monoText("No routines yet — tap NEW ROUTINE below.", 12f, colorAccent()))
        }
        routines.forEach { routine ->
            val status = (if (routine.enabled) "ON" else "OFF") + " · " +
                (if (routine.approved) "APPROVED" else "NOT APPROVED — will not run") +
                (if (routine.lastRunDate.isNotBlank()) " · last ran ${routine.lastRunDate}" else "")
            box.addView(monoText(
                "${routine.name} — ${routine.time} ${routine.days}\n  $status",
                12f, colorAccent()
            ))
            box.addView(monoText("  \"${routine.prompt.take(80)}\"", 11f, colorAccentSecondary()))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (!routine.approved) {
                row.addView(shellButton("APPROVE") { showApproveRoutineDialog(routine) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            }
            row.addView(shellButton(if (routine.enabled) "TURN OFF" else "TURN ON") {
                routineStore.setEnabled(routine.id, !routine.enabled)
                showScreen("setup")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            row.addView(shellButton("DELETE") { showDeleteRoutineDialog(routine) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
            box.addView(row)
        }
        box.addView(shellButton("NEW ROUTINE") { showNewRoutineDialog() })
        screenBody.addView(box)
    }

    private fun showApproveRoutineDialog(routine: RoutineStore.Routine) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("APPROVE THIS ROUTINE?")
            .setMessage(
                "MAGI will run this prompt at ${routine.time}, ${routine.days}:\n\n" +
                    "\"${routine.prompt}\"\n\n" +
                    "It only ever runs this saved prompt, at most once a day. " +
                    "Anything it wants to do on the phone still needs your " +
                    "approval each time. Switch it OFF or DELETE it any time " +
                    "in SETUP > ROUTINES to stop it."
            )
            .setPositiveButton("APPROVE ONCE") { _, _ ->
                routineStore.setApproved(routine.id)
                showScreen("setup")
            }
            .setNegativeButton("NOT YET", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showDeleteRoutineDialog(routine: RoutineStore.Routine) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("DELETE ROUTINE?")
            .setMessage("Delete \"${routine.name}\" (${routine.time} ${routine.days})? It will never run again.")
            .setPositiveButton("DELETE") { _, _ ->
                routineStore.delete(routine.id)
                showScreen("setup")
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showNewRoutineDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val nameInput = EditText(this).apply {
            hint = "Name — e.g. Morning briefing"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val timeInput = EditText(this).apply {
            hint = "Time — HH:mm, 24-hour, e.g. 07:30"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val promptInput = EditText(this).apply {
            hint = "What should MAGI do? — e.g. Give me the morning briefing"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        layout.addView(nameInput)
        layout.addView(timeInput)
        layout.addView(promptInput)
        var daysChoice = "DAILY"
        val dayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dayButtons = mutableMapOf<String, Button>()
        RoutineStore.DAY_CHOICES.forEach { choice ->
            val button = shellButton(choice) {
                daysChoice = choice
                dayButtons.forEach { (name, btn) ->
                    if (name == daysChoice) {
                        btn.setBackgroundColor(colorDanger())
                        btn.setTextColor(AgentTheme.background)
                    } else {
                        AgentTheme.style(btn)
                    }
                }
            }
            if (choice == daysChoice) {
                button.setBackgroundColor(colorDanger())
                button.setTextColor(AgentTheme.background)
            }
            dayButtons[choice] = button
            dayRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        layout.addView(monoText("DAYS", 12f, colorAccentSecondary()))
        layout.addView(dayRow)
        val dialog = AlertDialog.Builder(this)
            .setTitle("NEW ROUTINE")
            .setMessage("A new routine is NOT approved — it cannot run until you approve it once in SETUP > ROUTINES.")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val error = routineStore.add(
                    nameInput.text.toString(),
                    timeInput.text.toString(),
                    promptInput.text.toString(),
                    daysChoice
                )
                if (error != null) {
                    val fail = AlertDialog.Builder(this)
                        .setTitle("COULD NOT SAVE")
                        .setMessage(error)
                        .setPositiveButton("OK") { _, _ -> showNewRoutineDialog() }
                        .create()
                    fail.show()
                    fail.window?.setBackgroundDrawable(
                        GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
                    )
                } else {
                    showScreen("setup")
                }
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showConnectionsBox() {
        val box = borderedPanel()
        box.addView(boxHeading("CONNECTIONS"))
        val links = appConnector.checkAll()
        links.forEach { link ->
            val col = when (link.state) {
                AppConnector.LinkState.OK -> AgentTheme.signal
                AppConnector.LinkState.RUNNING -> AgentTheme.signal
                AppConnector.LinkState.NEEDS_SETUP -> colorAccentSecondary()
                else -> colorDanger()
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(link.name, 12f, col).apply { minWidth = dp(110) })
            row.addView(monoText(
                when (link.state) {
                    AppConnector.LinkState.OK -> "LINK OK"
                    AppConnector.LinkState.RUNNING -> "RUNNING"
                    AppConnector.LinkState.OFFLINE -> "OFFLINE"
                    AppConnector.LinkState.NEEDS_SETUP -> "NEEDS SETUP"
                    AppConnector.LinkState.ERROR -> "ERROR"
                }, 11f, col).apply { minWidth = dp(90) })
            row.addView(monoText(link.detail, 10f, colorAccentSecondary()), LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(row)
        }
        box.addView(shellButton("TEST ALL") {
            appConnector.testProvider { check ->
                runOnUiThread {
                    val providerLine = when (check) {
                        null -> "PROVIDER: no provider configured, or its address is bad."
                        else -> "PROVIDER PORT: ${check.state} — ${check.detail}"
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Connection test")
                        .setMessage(appConnector.checkAll().joinToString("\n") { "${it.name}: ${it.state} — ${it.detail}" } + "\n\n$providerLine")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        })
        box.addView(monoText("Accessibility cannot be switched on by the app itself — use ENABLE ACCESSIBILITY above if it says NEEDS SETUP.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showPortsBox() {
        val box = borderedPanel()
        box.addView(boxHeading("PORTS"))
        val resultView = monoText("", 11f, colorAccentSecondary())
        portManager.endpoints().forEach { endpoint ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(endpoint.label, 11f, colorAccent()).apply { minWidth = dp(120) })
            row.addView(monoText("${endpoint.host}:${endpoint.port}", 11f, colorAccentSecondary()), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(shellButton("TEST") {
                Thread {
                    val check = portManager.test(endpoint)
                    runOnUiThread {
                        resultView.text = "${endpoint.label}: ${check.state} — ${check.detail}"
                        resultView.setTextColor(if (check.state == PortManager.PortState.OPEN) AgentTheme.signal else colorDanger())
                    }
                }.start()
            })
            if (!endpoint.builtin) {
                row.addView(shellButton("DEL") {
                    portManager.removeCustom(endpoint)
                    showScreen("setup")
                })
            }
            box.addView(row)
        }
        box.addView(resultView)
        box.addView(shellButton("TEST ALL PORTS") {
            resultView.text = "Testing…"
            portManager.testAll { checks ->
                runOnUiThread {
                    resultView.text = checks.joinToString("\n") { "${it.endpoint.label}: ${it.state} — ${it.detail}" }
                }
            }
        })
        box.addView(shellButton("ADD PORT") { showAddPortDialog() })
        box.addView(shellButton("LIST OLLAMA MODELS") {
            val localEndpoint = magiEngine.nodes.firstOrNull { it.provider == MagiEngine.ProviderKind.LOCAL }?.endpoint
                ?: "http://localhost:11434/v1"
            resultView.text = "Asking Ollama at $localEndpoint…"
            portManager.ollamaModels(localEndpoint) { result ->
                runOnUiThread {
                    resultView.text = result.fold(
                        onSuccess = { names ->
                            if (names.isEmpty()) "Ollama at $localEndpoint answered but has no models pulled yet — pull one first (e.g. ollama pull llama3.2), then type its name in MAGI NODE LINKS."
                            else "Ollama models at $localEndpoint:\n" + names.joinToString("\n")
                        },
                        onFailure = { e -> "Ollama check failed: ${e.message ?: e.javaClass.simpleName} — is Ollama running on this device?" }
                    )
                }
            }
        })
        box.addView(monoText("Ollama (localhost:11434) is now a real MAGI provider: set a node to LOCAL in MAGI NODE LINKS above, with a model name already pulled in Ollama. Plain http is allowed only to localhost — any other address still needs https. LIST OLLAMA MODELS shows what this device has installed.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showAddPortDialog() {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(12)) }
        fun field(hintValue: String, value: String = ""): EditText = EditText(this).apply {
            hint = hintValue
            setText(value)
            setSingleLine(true)
            AgentTheme.style(this)
            fields.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val label = field("Label, e.g. MY SERVER")
        val host = field("Host, e.g. 192.168.1.10")
        val port = field("Port, e.g. 8080")
        val scheme = field("Scheme http or https", "http")
        val dialog = AlertDialog.Builder(this).setTitle("Add a port")
            .setView(fields).setNegativeButton("CANCEL", null)
            .setPositiveButton("ADD", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val portNumber = port.text.toString().trim().toIntOrNull() ?: -1
                val error = portManager.addCustom(
                    label.text.toString().trim(), host.text.toString().trim(),
                    portNumber, scheme.text.toString().trim().lowercase()
                )
                if (error != null) {
                    label.error = error
                } else {
                    dialog.dismiss()
                    showScreen("setup")
                }
            }
        }
        dialog.show()
    }

    private fun showVoiceBox() {
        val box = borderedPanel()
        box.addView(boxHeading("VOICE"))
        val stateText = when (voiceConnector.state) {
            VoiceConnector.VoiceState.SERVICE_RUNNING -> "Voice service is RUNNING."
            VoiceConnector.VoiceState.LISTENING -> "Listening…"
            VoiceConnector.VoiceState.SPEAKING -> "Speaking a reply."
            VoiceConnector.VoiceState.UNAVAILABLE -> "Voice is unavailable on this phone."
            VoiceConnector.VoiceState.OFF -> "Voice is OFF."
        }
        box.addView(monoText(stateText, 12f, colorAccent()))
        box.addView(monoText(
            "Mic permission: ${if (voiceConnector.hasMicPermission()) "allowed" else "OFF — allow it in Android Settings"}. " +
            "Provider: ${if (voiceConnector.providerConfigured()) "configured" else "not configured — add a key in SETUP"}.",
            11f, colorAccentSecondary()))
        box.addView(shellButton(if (voiceConnector.serviceActive()) "STOP VOICE SERVICE" else "START VOICE SERVICE") {
            val result = if (voiceConnector.serviceActive()) voiceConnector.stopService() else voiceConnector.startService()
            AlertDialog.Builder(this).setTitle("Voice service").setMessage(result)
                .setPositiveButton("OK") { _, _ -> showScreen("setup") }.show()
        })
        box.addView(shellButton("LISTEN (PUSH-TO-TALK)") {
            voiceListening = true
            voiceConnector.listenOnce(
                onHeard = { heard ->
                    voiceListening = false
                    showScreen("comms")
                    runMagiChat(heard)
                },
                onError = { message ->
                    voiceListening = false
                    AlertDialog.Builder(this).setTitle("Voice").setMessage(message)
                        .setPositiveButton("OK") { _, _ -> showScreen("setup") }.show()
                }
            )
        })
        box.addView(monoText("The voice service sends heard speech to AgentRuntime and speaks the reply. Push-to-talk sends heard speech to MAGI CHAT instead.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showWakeWordBox() {
        val box = borderedPanel()
        box.addView(boxHeading("VOICE COMMANDS — HEY MAGI"))
        box.addView(monoText(wakeWord.statusLine(voiceListening), 12f, if (voiceListening) colorDanger() else colorAccent()))
        box.addView(shellButton(
            if (wakeWord.enabled) "HEY MAGI: ON — TAP TO TURN OFF" else "HEY MAGI: OFF — TAP TO TURN ON"
        ) {
            wakeWord.enabled = !wakeWord.enabled
            showScreen("setup")
        })
        box.addView(shellButton(if (voiceListening) "● STOP LISTENING" else "TRY IT — TAP TO TALK") {
            if (voiceListening) startVoiceListen()
            else if (wakeWord.enabled) startVoiceListen()
            else {
                AlertDialog.Builder(this)
                    .setTitle("HEY MAGI IS OFF")
                    .setMessage("Switch HEY MAGI on above first — then tap TRY IT and say \"Hey MAGI, …\".")
                    .setPositiveButton("OK", null).show()
            }
        })
        box.addView(monoText(
            "Mic permission: ${if (voiceConnector.hasMicPermission()) "allowed" else "OFF — allow it in Android Settings"}. " +
            "Plain truth: a phone does not give other apps an always-on hotword listener — only the phone maker's own assistant gets one. So NERV cannot hear \"Hey MAGI\" while it sits in the background, and it never tries: when the switch is OFF nothing listens at all, and when it is ON a tap is still what starts listening. While it listens the ● LISTENING line shows here and in the status bar, and a tap stops it. Say \"Hey MAGI, …\" and the wake words are taken off the front before MAGI hears the question. Nothing is ever recorded in the background. Not yet run on the emulator — the tap-to-talk flow and the listening indicator need checking on Spiros's phone.",
            11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showVoiceWidgetBox() {
        val box = borderedPanel()
        box.addView(boxHeading("VOICE WIDGET"))
        val pinned = NervVoiceWidget.isPinned(this)
        box.addView(monoText(
            if (pinned) "The NERV voice widget is on your home screen — tap its TALK button and speak; MAGI answers in CHAT."
            else "No NERV voice widget on your home screen yet.",
            12f, colorAccent()))
        NervVoiceWidget.lastReply(this)?.let { reply ->
            box.addView(monoText("Last MAGI reply: $reply", 11f, colorAccentSecondary()))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            box.addView(shellButton(if (pinned) "ADD ANOTHER WIDGET" else "ADD WIDGET TO HOME") {
                if (!NervVoiceWidget.requestPin(this)) {
                    AlertDialog.Builder(this).setTitle("Could not pin the widget")
                        .setMessage("Android would not pin it. Long-press your home screen, tap Widgets, find NERV Voice, and drag it onto the home screen.")
                        .setPositiveButton("OK", null).show()
                }
            })
        } else {
            box.addView(monoText("This Android version cannot pin a widget from inside the app. Long-press your home screen, tap Widgets, find NERV Voice, and drag it onto the home screen.", 11f, colorAccentSecondary()))
        }
        box.addView(shellButton("OPEN CHAT + LISTEN NOW") { startVoiceListen() })
        box.addView(monoText("The widget shows MAGI's status and the last reply. Its TALK button opens NERV and starts listening straight away — no key or transcript is stored in the widget, only the last reply text on this phone.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showLockWidgetsBox() {
        val box = borderedPanel()
        box.addView(boxHeading("LOCK SCREEN WIDGETS"))
        box.addView(monoText(
            "The lock screen inside NERV already shows the clock, the next timer and the MAGI status — live, without unlocking. " +
            "That works on every phone.",
            12f, colorAccent()))
        val pinned = NervLockWidget.isPinned(this)
        box.addView(monoText(
            if (pinned) "The NERV Lock widget is placed — it shows the same clock, timer and MAGI status."
            else "No NERV Lock widget placed yet.",
            12f, colorAccent()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            box.addView(shellButton(if (pinned) "ADD ANOTHER WIDGET" else "ADD WIDGET TO HOME") {
                if (!NervLockWidget.requestPin(this)) {
                    AlertDialog.Builder(this).setTitle("Could not pin the widget")
                        .setMessage("Android would not pin it. Long-press your home screen, tap Widgets, find NERV Lock, and drag it onto the home screen.")
                        .setPositiveButton("OK", null).show()
                }
            })
        } else {
            box.addView(monoText("This Android version cannot pin a widget from inside the app. Long-press your home screen, tap Widgets, find NERV Lock, and drag it onto the home screen.", 11f, colorAccentSecondary()))
        }
        box.addView(monoText("Plain truth about the lock screen: Android stopped letting other apps put widgets on the real lock screen years ago (Android 5.0) — on a modern phone this widget can only sit on the home screen, and no app can change that. The in-app lock screen above is the part that works everywhere, and it shows the same clock, next timer and MAGI status without unlocking. Needs the receiver entry in AndroidManifest.xml + res/layout, res/xml files in widget/ — not yet run on the emulator.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showTileBox() {
        val box = borderedPanel()
        box.addView(boxHeading("QUICK SETTINGS TILE"))
        val armed = NervTileService.isArmed(this)
        box.addView(monoText(
            if (armed) "The NERV MAGI tile is lit right now — the next tap on it starts push-to-talk."
            else "The NERV MAGI tile is dark — tap it once to open MAGI, tap again while it is lit to talk.",
            12f, colorAccent()))
        box.addView(monoText("To add it: pull the quick-settings shade down, tap the edit (pencil) button, find \"NERV MAGI\" in the tiles below, and drag it up into the active tiles. Android makes every app's tile an add-it-yourself step — it never appears on its own.", 12f, colorAccent()))
        box.addView(monoText("Tap once: opens NERV on the MAGI screen and lights the tile. Tap again while lit: starts push-to-talk, like the voice widget's TALK button. Long-press just opens NERV's app info — that is Android, no app can change it. If the phone is locked, the MAGI vote (and PIN, if set) still comes first. Needs the service entry in AndroidManifest.xml + res/drawable/nerv_tile_icon.xml on Spiros's PC — not yet run on the emulator: the tile must be added by hand in quick settings and the two taps checked there.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showShortcutsBox() {
        val box = borderedPanel()
        box.addView(boxHeading("APP SHORTCUTS — LONG-PRESS THE NERV ICON"))
        box.addView(monoText(
            "Long-press the NERV icon on your home screen and three shortcuts appear:",
            12f, colorAccent()))
        box.addView(monoText("OPEN MAGI — opens NERV on the MAGI screen.", 12f, colorAccent()))
        box.addView(monoText("START A TIMER — opens TIMER and starts the timer (the one picked, or ACTIVATION first).", 12f, colorAccent()))
        box.addView(monoText("TALK TO MAGI — opens NERV and starts listening straight away.", 12f, colorAccent()))
        box.addView(shellButton("TRY: OPEN MAGI NOW") { showScreen("magi") })
        box.addView(shellButton("TRY: START A TIMER NOW") {
            pendingOpenScreen = "timer"
            pendingStartTimer = true
            maybeApplyPendingOpenScreen()
        })
        box.addView(monoText("A shortcut never skips the lock: if the phone is locked, the MAGI vote (and PIN, if set) still comes first, and the shortcut waits until access is granted. Needs the shortcuts entry in AndroidManifest.xml + res/xml/shortcuts.xml and res/drawable/nerv_shortcut_icon.xml on Spiros's PC — not yet run on the emulator: long-press the NERV icon on the real phone and check all three shortcuts open the right screen.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showShareBox() {
        val box = borderedPanel()
        box.addView(boxHeading("SHARE TO NERV"))
        box.addView(monoText(
            "Share text, a link or a photo into NERV from any app — browser, Photos, a chat — and it lands in CHAT with ASK MAGI / DISCARD. Nothing goes to MAGI until you tap ASK MAGI; DISCARD drops it. Shared text is prefilled in the message box so you can edit it first. A shared photo is saved in NERV's own shared/ folder (10 MB limit) — see FILES below — and MAGI only ever hears its name and size, never the picture itself.",
            12f, colorAccent()))
        val shared = SharedContent.sharedDir(this).listFiles()?.sortedByDescending { it.lastModified() }?.take(3)
        if (!shared.isNullOrEmpty()) {
            box.addView(monoText("Latest shared photos:", 12f, colorAccentSecondary()))
            shared.forEach { file ->
                box.addView(monoText("${file.name} (${SharedContent.formatSize(file.length())})", 11f, colorAccent()))
            }
        }
        box.addView(monoText("Needs the ACTION_SEND intent-filter in AndroidManifest.xml on Spiros's PC — not yet run on the emulator: share a link and a photo from another app into NERV on the real phone and check the CHAT banner, ASK MAGI and DISCARD.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showBatterySaverBox() {
        val box = borderedPanel()
        box.addView(boxHeading("BATTERY SAVER"))
        box.addView(monoText(batterySaver.statusLine(), 12f, colorAccent()))
        box.addView(shellButton("BATTERY SAVER: ${if (batterySaver.manual) "ON — TAP TO TURN OFF" else "OFF — TAP TO TURN ON"}") {
            batterySaver.manual = !batterySaver.manual
            batterySaver.check(this)
            applyBatterySaver()
            showScreen("setup")
        })
        box.addView(shellButton("AUTO-ON: ${if (batterySaver.autoEnabled) "ON — TAP TO TURN OFF" else "OFF — TAP TO TURN ON"}") {
            batterySaver.autoEnabled = !batterySaver.autoEnabled
            batterySaver.check(this)
            applyBatterySaver()
            showScreen("setup")
        })
        box.addView(shellButton("AUTO-ON BELOW: ${batterySaver.autoThreshold}% — TAP TO CHANGE") {
            val thresholds = BatterySaver.THRESHOLDS
            batterySaver.autoThreshold = thresholds[(thresholds.indexOf(batterySaver.autoThreshold) + 1) % thresholds.size]
            batterySaver.check(this)
            applyBatterySaver()
            showScreen("setup")
        })
        box.addView(monoText("When the battery is low, NERV slows the globe, the SYNC waves and the flicker, and the SYNC and TOX live readings stretch from every 2 seconds to every 8 — and a BATTERY SAVER badge shows in the status bar on every screen. Timers, the lock vote, chats, debates and phone tasks are never slowed. Manual ON always wins; auto switches on below the threshold and off again when charging or 5 points above it. Not yet run on the emulator — the real battery behaviour (auto-on at the threshold, badge, slower globe) needs checking on Spiros's phone.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showMagiVoicesBox() {
        val box = borderedPanel()
        box.addView(boxHeading("MAGI VOICES"))
        box.addView(monoText(
            "Spoken replies: ${if (magiVoice.enabled) "ON" else "OFF"} — a MAGI answer is read out loud in the voice of the node that chaired the debate.",
            12f, colorAccent()
        ))
        box.addView(shellButton("SPEAK REPLIES: ${if (magiVoice.enabled) "ON — TAP TO TURN OFF" else "OFF — TAP TO TURN ON"}") {
            magiVoice.enabled = !magiVoice.enabled
            showScreen("setup")
        })
        MagiEngine.NodeId.values().forEach { id ->
            val name = magiEngine.nodeNames[id] ?: id.name
            val voiceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            voiceRow.addView(monoText("$name — ${MagiVoice.voiceLabel(id)}", 12f, colorAccent()),
                LinearLayout.LayoutParams(0, -2, 1f))
            voiceRow.addView(shellButton("TEST VOICE") { magiVoice.testVoice(id) })
            box.addView(voiceRow)
        }
        box.addView(monoText("Each node has its own pitch and speed, so you can tell them apart by ear. Truly different voices depend on the voices your phone's speech engine offers — most phones only have one voice per language, so pitch and speed are what tell the nodes apart. Nothing secret is ever read out loud: task and calendar lines, and anything mentioning a key or a PIN, are skipped.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showSmartRepliesBox() {
        val box = borderedPanel()
        box.addView(boxHeading("SMART REPLIES"))
        box.addView(monoText(
            if (smartReplies.enabled) "ON — after MAGI answers, up to 3 short suggestions appear as chips under the latest reply."
            else "OFF — no suggestion chips in CHAT.",
            12f, colorAccent()))
        box.addView(shellButton("SMART REPLIES: ${if (smartReplies.enabled) "ON" else "OFF"}") {
            smartReplies.enabled = !smartReplies.enabled
            showScreen("setup")
        })
        box.addView(monoText("A question gets Yes / No / Tell me more; a proposal gets Approve / Deny / Ask MAGI why; a finished answer gets Thanks / What next? / Show me. The suggestions are made on this phone from the last answer's words only — no provider call, no network, no key. Tapping a chip sends it as your message; nothing is ever auto-sent.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showNotificationsBox() {
        val box = borderedPanel()
        box.addView(boxHeading("NOTIFICATIONS"))
        val state = when {
            !notificationHelper.enabled -> "OFF — NERV stays silent"
            !notificationHelper.hasPermission() -> "NOT GRANTED — Android is blocking NERV's alerts"
            else -> "ON — MAGI alerts you when a task finishes or needs approval"
        }
        box.addView(monoText(state, 12f, colorAccent()))
        box.addView(shellButton("NOTIFICATIONS: ${if (notificationHelper.enabled) "ON" else "OFF"}") {
            notificationHelper.enabled = !notificationHelper.enabled
            showScreen("setup")
        })

        if (!notificationHelper.hasPermission() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            box.addView(shellButton("REQUEST PERMISSION") {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    notificationRequestCode,
                )
            })
        }
        box.addView(shellButton("TEST NOTIFICATION") {
            notificationHelper.notifyTest()
            if (!notificationHelper.canNotify()) {
                AlertDialog.Builder(this)
                    .setTitle("No notification sent")
                    .setMessage(
                        if (!notificationHelper.enabled) "Notifications are switched OFF above — turn them on first."
                        else "Android has not granted the notification permission — tap REQUEST PERMISSION above."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        })

        box.addView(boxHeading("REMINDERS"))
        box.addView(monoText(
            if (taskReminders.enabled) "ON — a phone task waiting ${taskReminders.waitingThresholdMinutes} minutes, or a timer with 1 minute left, gets one alert."
            else "OFF — no waiting-task or timer reminders.",
            12f, colorAccent()))
        box.addView(shellButton("REMINDERS: ${if (taskReminders.enabled) "ON" else "OFF"}") {
            taskReminders.enabled = !taskReminders.enabled
            showScreen("setup")
        })
        box.addView(shellButton("WAITING TASK REMINDER: ${taskReminders.waitingThresholdMinutes} MIN — TAP TO CHANGE") {
            taskReminders.cycleThreshold()
            showScreen("setup")
        })
        box.addView(monoText("NERV alerts you when: a phone task you approved finishes, MAGI proposes a phone task that needs your approval, or a timer runs out while NERV is in the background. Nothing alerts while NERV is in front — the dialog on screen is the alert then. The alert text is only the task or timer name; no key, chat or location is ever in a notification. Needs POST_NOTIFICATIONS in AndroidManifest.xml on Android 13+.", 11f, colorAccentSecondary()))
        box.addView(monoText("Reminders, same channel: a phone task that has waited (proposed, not started) longer than the time above gets one alert, and a running timer gets one alert when it has 1 minute left. A reminder never starts, approves or stops anything — it only says the task or timer is waiting. No reminder fires while you are looking at the screen that already shows it (CHAT for a waiting task, TIMER for the countdown). A task saved before this update has no proposal time, so it never reminds.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showOfflineBox() {
        val box = borderedPanel()
        box.addView(boxHeading("OFFLINE MODE"))
        box.addView(monoText(magiEngine.offlineMode.summary(magiEngine.nodes), 12f, colorAccent()))
        box.addView(shellButton("OFFLINE MODE: ${if (magiEngine.offlineMode.enabled) "ON" else "OFF"}") {
            magiEngine.offlineMode.enabled = !magiEngine.offlineMode.enabled
            showScreen("setup")
        })

        magiEngine.nodes.forEach { node ->
            val nodeName = magiEngine.nodeNames[node.id] ?: node.id.name
            val state = when {
                !node.enabled -> "OFF"
                magiEngine.offlineMode.blocksProvider(node.provider) -> "BLOCKED — cloud, offline mode is on"
                node.provider == MagiEngine.ProviderKind.LOCAL -> "LIVE — answers on this phone (Ollama)"
                node.provider == MagiEngine.ProviderKind.CLAUDE_BUILTIN -> "NO LINK — set a real provider in MAGI NODE LINKS"
                else -> "LIVE — cloud (blocked while offline mode is on)"
            }
            box.addView(monoText("$nodeName — $state", 11f, colorAccent()))
        }

        val ollamaView = monoText("OLLAMA: not checked yet — tap CHECK OLLAMA.", 11f, colorAccentSecondary())
        box.addView(ollamaView)
        box.addView(shellButton("CHECK OLLAMA") {
            ollamaView.text = "OLLAMA: checking localhost:11434…"
            Thread {
                val check = magiEngine.offlineMode.probeOllama()
                runOnUiThread { ollamaView.text = "OLLAMA: ${check.detail}" }
            }.start()
        })
        box.addView(monoText("OFFLINE MODE cuts the internet: Claude, ChatGPT and Grok are blocked, and only a LOCAL node — a model already pulled in Ollama on this phone — can answer. A blocked node falls back to a clearly-labelled LOCAL DEMO draft in a debate and abstains in a lock vote, so nothing is ever presented as a real answer when it is not. Switch it off to let the cloud answer again. No new permission, no network call beyond localhost, no key.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showLockSecurityBox() {
        val box = borderedPanel()
        box.addView(boxHeading("LOCK SECURITY"))
        box.addView(monoText(lockSecurity.summary(), 12f, colorAccent()))
        box.addView(shellButton(if (lockSecurity.hasPin()) "CHANGE PIN" else "SET PIN") { showSetPinDialog() })
        if (lockSecurity.hasPin()) {
            box.addView(shellButton("REMOVE PIN") {
                AlertDialog.Builder(this)
                    .setTitle("Remove the lock PIN?")
                    .setMessage("The lock screen will go back to the MAGI vote only. The PIN hash and salt are wiped.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("REMOVE") { _, _ -> lockSecurity.clearPin(); showScreen("setup") }
                    .show()
            })
            val bio = lockSecurity.biometricStatus()
            box.addView(shellButton("FINGERPRINT: ${if (lockSecurity.biometricEnabled) "ON" else "OFF"}") {
                if (bio != LockSecurity.BiometricStatus.AVAILABLE) {
                    AlertDialog.Builder(this).setTitle("Fingerprint").setMessage(lockSecurity.biometricStatusText()).setPositiveButton("OK", null).show()
                } else {
                    lockSecurity.biometricEnabled = !lockSecurity.biometricEnabled
                    showScreen("setup")
                }
            })
            box.addView(monoText(lockSecurity.biometricStatusText(), 11f, colorAccentSecondary()))
        } else {
            box.addView(monoText("Set a PIN first — fingerprint is only offered as a second way past the PIN gate.", 11f, colorAccentSecondary()))
        }
        box.addView(shellButton("KEYSTORE AUDIT") {
            val keystoreLines = lockSecurity.auditKeystore().joinToString("\n") { "${it.label}: ${it.state}" }
            val prefsLines = lockSecurity.auditPlaintextSecrets().joinToString("\n") { "${it.label}: ${it.state}" }
            AlertDialog.Builder(this)
                .setTitle("Keystore audit")
                .setMessage("KEYSTORE ENTRIES\n$keystoreLines\n\nPREFERENCE FILES (key names only — no values)\n$prefsLines\n\nThe PIN is stored as a salted hash only, never in plain text.")
                .setPositiveButton("OK", null)
                .show()
        })
        box.addView(monoText("The PIN is stored as a salted hash, never in plain text. API keys live in Android Keystore only — the audit above checks that. The PIN proves it is you; the MAGI vote still decides access.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showSetPinDialog() {
        val first = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New PIN (4–8 digits)"
        }
        val second = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Repeat the PIN"
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(first)
            addView(second)
        }
        AlertDialog.Builder(this)
            .setTitle(if (lockSecurity.hasPin()) "Change lock PIN" else "Set lock PIN")
            .setMessage("The PIN is stored as a salted hash only — NERV never keeps the PIN itself. You will enter it on the lock screen before the MAGI vote.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE PIN") { _, _ ->
                val pin = first.text.toString()
                val confirm = second.text.toString()
                val error = when {
                    pin.length !in LockSecurity.MIN_PIN_LENGTH..LockSecurity.MAX_PIN_LENGTH -> "PIN must be ${LockSecurity.MIN_PIN_LENGTH}–${LockSecurity.MAX_PIN_LENGTH} digits."
                    !pin.all { it.isDigit() } -> "PIN must be digits only."
                    pin != confirm -> "The two PINs do not match."
                    else -> null
                }
                if (error != null) {
                    AlertDialog.Builder(this).setTitle("PIN not saved").setMessage(error)
                        .setPositiveButton("TRY AGAIN") { _, _ -> showSetPinDialog() }.show()
                } else {
                    lockSecurity.setPin(pin)
                    showScreen("setup")
                }
            }
            .show()
    }

    private fun showFilesBox() {
        val box = borderedPanel()
        box.addView(boxHeading("FILES"))
        box.addView(monoText(folderManager.summary(), 12f, colorAccent()))
        val folder = folderDir ?: folderManager.root
        box.addView(monoText("Folder: ${folder.name} (${folder.path})", 11f, colorAccentSecondary()))
        val upRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        upRow.addView(shellButton("UP ▸") {
            folderManager.parentOf(folder)?.let { folderDir = it }
            showScreen("setup")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        upRow.addView(shellButton("CREATE FOLDER") {
            val input = EditText(this).apply { hint = "Folder name"; AgentTheme.style(this) }
            AlertDialog.Builder(this).setTitle("New folder").setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CREATE") { _, _ ->
                    val error = folderManager.createFolder(folder, input.text.toString())
                    if (error != null) {
                        AlertDialog.Builder(this).setTitle("Could not create").setMessage(error)
                            .setPositiveButton("OK", null).show()
                    } else showScreen("setup")
                }
                .show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        box.addView(upRow)
        folderListView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(folderListView)
        screenBody.addView(box)
        refreshFilesList(folder)
    }

    private fun refreshFilesList(dir: java.io.File) {
        val listView = folderListView ?: return
        listView.removeAllViews()
        val items = folderManager.list(dir)
        if (items.isEmpty()) {
            listView.addView(monoText("(empty)", 11f, colorAccentSecondary()))
            return
        }
        items.forEach { item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(if (item.isDir) "▣ ${item.name}" else "▤ ${item.name}", 11f, colorAccent()).apply {
                isClickable = true
                setOnClickListener {
                    if (item.isDir) {
                        folderDir = item.file
                        showScreen("setup")
                    } else {
                        val text = folderManager.readText(item.file)
                        AlertDialog.Builder(this@NervActivity)
                            .setTitle(item.name)
                            .setMessage(text ?: "Cannot show this file (too big, or not text).")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (!item.isDir) {
                row.addView(monoText(folderManager.formatSize(item.size), 10f, colorAccentSecondary()).apply { minWidth = dp(52) })
            }
            row.addView(shellButton("RENAME") {
                val input = EditText(this).apply { hint = "New name"; setText(item.name); AgentTheme.style(this) }
                AlertDialog.Builder(this).setTitle("Rename ${item.name}").setView(input)
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("RENAME") { _, _ ->
                        val error = folderManager.rename(item.file, input.text.toString())
                        if (error != null) {
                            AlertDialog.Builder(this).setTitle("Could not rename").setMessage(error)
                                .setPositiveButton("OK", null).show()
                        } else showScreen("setup")
                    }
                    .show()
            })
            row.addView(shellButton("DEL") {
                AlertDialog.Builder(this)
                    .setTitle("Delete ${item.name}?")
                    .setMessage(if (item.isDir) "This deletes the folder and everything inside it. This cannot be undone." else "This deletes the file. This cannot be undone.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ ->
                        if (!folderManager.delete(item.file)) {
                            AlertDialog.Builder(this).setTitle("Could not delete").setMessage("That file is protected or outside the app's files.")
                                .setPositiveButton("OK", null).show()
                        } else showScreen("setup")
                    }
                    .show()
            })
            listView.addView(row)
        }
    }

    private fun showProviderDialog() {
        val saved = agentBridge.providerConfig()
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(12)) }
        fun field(hintValue: String, value: String, secret: Boolean = false): EditText = EditText(this).apply {
            hint = hintValue
            setText(value)
            setSingleLine(true)
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT
            AgentTheme.style(this)
            fields.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val endpoint = field("HTTPS base URL, ending in /v1", saved?.endpoint ?: "https://api.openai.com/v1")
        val model = field("Provider model ID", saved?.model ?: "")
        val key = field("API key • blank keeps existing key", "", true)
        val dialog = AlertDialog.Builder(this).setTitle("Your AI provider")
            .setMessage("Use a tool-capable OpenAI-compatible model. Your key is encrypted on this device in Android Keystore. API charges are separate. Only approved task data is sent.")
            .setView(fields).setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(saved == null || endpoint.text.toString().trim().trimEnd('/') == saved.endpoint || key.text.isNotBlank()) {
                        "Enter a new key when changing provider endpoint"
                    }
                    agentBridge.saveProvider(
                        ProviderConfig(
                            endpoint.text.toString().trim(),
                            model.text.toString().trim(),
                            key.text.toString().ifBlank { saved?.apiKey ?: "" }
                        )
                    )
                    key.setText("")
                    dialog.dismiss()
                    showScreen("setup")
                } catch (e: Exception) {
                    key.error = e.message ?: "Invalid configuration"
                }
            }
        }
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun showNodeKeyDialog(node: MagiEngine.NodeConfig) {
        val nodeName = magiEngine.nodeNames[node.id] ?: node.id.name
        val key = EditText(this).apply {
            hint = "API key for $nodeName • blank cancels"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            AgentTheme.style(this)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(key, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(this).setTitle("$nodeName — API key")
            .setMessage("This key is encrypted into this node's own Android Keystore entry and never shown again. Leave blank to keep the current key, or use FORGET on the SETUP row to remove it. A node with no key falls back to the shared provider key.")
            .setView(fields).setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val typed = key.text.toString().trim()
                    if (typed.isNotBlank()) nodeKeyStore.save(node.id, typed)
                    key.setText("")
                    dialog.dismiss()
                    showScreen("setup")
                } catch (e: Exception) {
                    key.error = e.message ?: "Could not save key"
                }
            }
        }
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun showAddPackDialog() {
        val nameInput = EditText(this).apply {
            hint = "Pack name, e.g. MATH"
            maxLines = 1
            AgentTheme.style(this)
        }
        val descInput = EditText(this).apply {
            hint = "What is it for? (optional)"
            maxLines = 1
            AgentTheme.style(this)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(monoText("NAME", 11f, colorAccentSecondary()))
            addView(nameInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("DESCRIPTION", 11f, colorAccentSecondary()))
            addView(descInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("TOOLS IN THIS PACK", 11f, colorAccentSecondary()))
        }
        val toolBoxes = packManager.availableTools().map { tool ->
            val owner = packManager.packForTool(tool)?.name ?: ""
            CheckBox(this).apply {
                text = "$tool — $owner"
                setTextColor(colorAccent())
                textSize = 12f
            }.also { fields.addView(it) }
        }
        val dialog = AlertDialog.Builder(this).setTitle("Add a pack")
            .setMessage("A pack bundles tools NERV already has, so one tap loads or unloads the whole group. It never adds new code, never downloads anything, and never holds a secret.")
            .setView(fields).setNegativeButton("CANCEL", null)
            .setPositiveButton("ADD", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val picked = packManager.availableTools()
                    .filterIndexed { index, _ -> toolBoxes[index].isChecked }
                val error = packManager.addCustomPack(
                    nameInput.text.toString(),
                    descInput.text.toString(),
                    picked
                )
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Could not add pack")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    dialog.dismiss()
                    showScreen("setup")
                }
            }
        }
        dialog.show()
    }

    private fun showPackStoreBox() {
        val box = borderedPanel()
        box.addView(boxHeading("PACK STORE"))

        packManager.allPacks().forEach { pack ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(pack.name, 12f, if (pack.enabled) AgentTheme.signal else colorDanger()).apply {
                minWidth = dp(110)
            })
            row.addView(monoText(
                (if (pack.custom) "CUSTOM" else "BUILT-IN") +
                " · ${pack.tools.size} TOOL${if (pack.tools.size == 1) "" else "S"}" +
                " · ${if (pack.enabled) "LOADED" else "UNLOADED"}",
                11f, colorAccentSecondary()
            ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            row.addView(shellButton("SHARE") {
                val file = packStore.sharePack(pack)
                if (file == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Could not share")
                        .setMessage("That pack could not be written as a file.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Pack shared as a file")
                        .setMessage("${pack.name} is saved as ${file.name} in the packs folder — send it from SETUP > FILES, like a report.")
                        .setPositiveButton("OK") { _, _ -> showScreen("setup") }
                        .show()
                }
            })
            box.addView(row)
            box.addView(monoText(pack.description, 10f, colorAccentSecondary()))
        }

        val shared = packStore.listSharedPacks()
        box.addView(boxHeading("PACK FILES IN THIS PHONE (${shared.size})"))
        if (shared.isEmpty()) {
            box.addView(monoText("No pack files yet — tap SHARE on a pack above to save one here.", 11f, colorAccentSecondary()))
        }
        shared.forEach { sharedPack ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(monoText(sharedPack.name, 12f, if (sharedPack.readable) colorAccent() else colorDanger()), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            row.addView(monoText(folderManager.formatSize(sharedPack.file.length()), 10f, colorAccentSecondary()).apply { minWidth = dp(52) })
            if (sharedPack.readable) {
                row.addView(shellButton("IMPORT") { showImportPackDialog(sharedPack.file) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            }
            row.addView(shellButton("DEL") {
                AlertDialog.Builder(this)
                    .setTitle("Delete ${sharedPack.file.name}?")
                    .setMessage("This deletes the pack file. A pack already added from it stays in SETUP > PACKS — only the file goes.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ ->
                        packStore.deleteSharedPack(sharedPack.file)
                        showScreen("setup")
                    }
                    .show()
            })
            box.addView(row)
            box.addView(monoText(
                if (sharedPack.readable) "TOOLS: ${sharedPack.tools.joinToString(", ")}" else sharedPack.description,
                10f, colorAccentSecondary()))
        }
        box.addView(monoText("There is no public pack store — a pack is a file you share yourself. SHARE writes the pack (name, description, tools only — never code, never a secret) into the app's packs folder, so it can be sent from SETUP > FILES. IMPORT reads a pack file from that folder, checks it is a NERV pack with tools NERV already has, and asks you to confirm before adding it. A pack from someone else needs its file in this folder first — NERV does not download packs and does not take a pack file shared from another app yet.", 11f, colorAccentSecondary()))
        screenBody.addView(box)
    }

    private fun showImportPackDialog(file: java.io.File) {
        val (parsed, error) = packStore.readPackFile(file)
        if (parsed == null) {
            AlertDialog.Builder(this)
                .setTitle("Cannot import this pack")
                .setMessage(error ?: "That file is not a NERV pack.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Import this pack?")
            .setMessage(
                "Name: ${parsed.name}\n" +
                "About: ${parsed.description.ifBlank { "(no description)" }}\n" +
                "Tools: ${parsed.tools.joinToString(", ")}\n\n" +
                "It will be added as one of your custom packs in SETUP > PACKS. A pack never runs anything new — it only bundles tools NERV already has."
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("IMPORT") { _, _ ->
                val addError = packManager.addCustomPack(parsed.name, parsed.description, parsed.tools)
                if (addError != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Could not import")
                        .setMessage(addError)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Pack imported")
                        .setMessage("${parsed.name} is now one of your custom packs — load it in SETUP > PACKS when you need it.")
                        .setPositiveButton("OK") { _, _ -> showScreen("setup") }
                        .show()
                }
            }
            .show()
    }

    private fun showDeletePackDialog(pack: PackManager.Pack) {
        AlertDialog.Builder(this)
            .setTitle("Delete pack \"${pack.name}\"?")
            .setMessage("This removes the \"${pack.name}\" pack (${pack.tools.joinToString(", ")}). The tools themselves stay — they still belong to their built-in packs.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                packManager.deleteCustomPack(pack.id)
                showScreen("setup")
            }
            .show()
    }

    private fun showTeachDialog(prefill: String = "") {
        val nameInput = EditText(this).apply {
            hint = "Skill name, e.g. MORNING BRIEF"
            maxLines = 1
            setText(if (prefill.isBlank()) "" else TaughtSkillStore.suggestedName(prefill))
            AgentTheme.style(this)
        }
        val instructionInput = EditText(this).apply {
            hint = "What should MAGI learn? e.g. When I say 'morning brief', give me the time, the battery and one dice roll."
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefill)
            AgentTheme.style(this)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(monoText("NAME", 11f, colorAccentSecondary()))
            addView(nameInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("WHAT SHOULD MAGI LEARN?", 11f, colorAccentSecondary()))
            addView(instructionInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(monoText("TOOLS IT MAY USE (OPTIONAL)", 11f, colorAccentSecondary()))
        }
        val toolBoxes = packManager.availableTools().map { tool ->
            val owner = packManager.packForTool(tool)?.name ?: ""
            CheckBox(this).apply {
                text = "$tool — $owner"
                setTextColor(colorAccent())
                textSize = 12f
            }.also { fields.addView(it) }
        }
        val dialog = AlertDialog.Builder(this).setTitle("Teach MAGI")
            .setMessage("A taught skill is an instruction MAGI follows in every debate while it is loaded. It never adds new code, never downloads anything, and never holds a secret. A tool it names still needs its own pack loaded to run.")
            .setView(fields).setNegativeButton("CANCEL", null)
            .setPositiveButton("TEACH", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val picked = packManager.availableTools()
                    .filterIndexed { index, _ -> toolBoxes[index].isChecked }
                val error = taughtSkillStore.add(
                    nameInput.text.toString(),
                    instructionInput.text.toString(),
                    picked
                )
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Could not teach MAGI")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    dialog.dismiss()
                    val savedName = nameInput.text.toString().trim().uppercase()
                    AlertDialog.Builder(this)
                        .setTitle("MAGI learned \"$savedName\"")
                        .setMessage("The skill is loaded now, so the next debate already follows it. Unload it in SETUP > TAUGHT SKILLS to stop MAGI using it, or delete it to forget it for good.")
                        .setPositiveButton("OK") { _, _ -> if (current == "comms") showScreen("comms") }
                        .show()
                    if (current == "setup") showScreen("setup")
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteTaughtSkillDialog(skill: TaughtSkillStore.TaughtSkill) {
        AlertDialog.Builder(this)
            .setTitle("Forget \"${skill.name}\"?")
            .setMessage("MAGI will stop following this skill: \"${skill.instruction}\". This cannot be undone — teach it again in CHAT if you change your mind.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("FORGET") { _, _ ->
                taughtSkillStore.delete(skill.id)
                showScreen("setup")
            }
            .show()
    }

    private fun showLock() {
        locked = true
        lockVoteRunning = false

        pinVerified = false
        pinEntry = ""
        pinDotsView = null
        pinAuthButton = null
        phoneFrame.removeAllViews()
        val lockFrame = FrameLayout(this).apply { setBackgroundColor(AgentTheme.background) }
        installPixelCanvas(lockFrame)
        val globe = GlobeView(this).apply { alpha = 0.55f }
        lockFrame.addView(globe, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val lockRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        lockRoot.addView(minchoText("現在時刻", 13f, colorAccentSecondary()).apply { gravity = Gravity.CENTER })
        lockRoot.addView(minchoText("MAGI AUTHENTICATION", 22f, colorAccent()).apply { gravity = Gravity.CENTER })

        val lockClock = minchoText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 64f, colorAccent()).apply {
            gravity = Gravity.CENTER
            setShadowLayer(14f, 0f, 0f, colorAccent())
        }
        lockClockView = lockClock
        lockRoot.addView(lockClock)

        lockRoot.addView(monoText(SimpleDateFormat("EEE MMM dd yyyy", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()), 11f, colorAccentSecondary()).apply { gravity = Gravity.CENTER })
        lockNodeValueViews.clear()
        lockNodeBoxViews.clear()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("MELCHIOR·1", "BALTHASAR·2", "CASPER·3").forEach { node ->
            val nodeBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), colorAccent()) }
            }

            nodeBox.contentDescription = "$node vote — waiting"
            nodeBox.addView(monoText(node, 9f, AgentTheme.signal).apply { gravity = Gravity.CENTER })

            val valueView = minchoText("---", 20f, AgentTheme.muted).apply { gravity = Gravity.CENTER }
            nodeBox.addView(valueView)
            lockNodeValueViews.add(valueView)
            lockNodeBoxViews.add(nodeBox)
            row.addView(nodeBox, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        lockRoot.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(12) })

        val lockWidgets = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), AgentTheme.muted) }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        lockTimerView = monoText(nextTimerLine(), 12f, colorAccent()).apply { gravity = Gravity.CENTER }
        lockWidgets.addView(lockTimerView)
        lockMagiView = monoText(magiStatusLine(), 12f, AgentTheme.signal).apply { gravity = Gravity.CENTER }
        lockWidgets.addView(lockMagiView)
        lockRoot.addView(lockWidgets, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val needsPin = lockSecurity.hasPin()
        if (needsPin) {
            val dots = monoText(pinDots(), 22f, colorAccent()).apply { gravity = Gravity.CENTER }
            pinDotsView = dots
            lockRoot.addView(dots)
            val lockedOut = lockSecurity.isLockedOut()
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("CLR", "0", "⌫")).forEach { keys ->
                val keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                keys.forEach { key ->
                    keyRow.addView(shellButton(key) { onPinKey(key) }.apply {
                        isEnabled = !lockedOut
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
                }
                lockRoot.addView(keyRow, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.8).toInt(), -2).apply { gravity = Gravity.CENTER })
            }
            if (lockSecurity.biometricOffered()) {
                lockRoot.addView(shellButton("FINGERPRINT  ▸") { showBiometricPrompt() },
                    LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.8).toInt(), -2).apply { gravity = Gravity.CENTER })
            }
            if (lockedOut) {

                Handler(Looper.getMainLooper()).postDelayed(
                    { if (locked) showLock() },
                    lockSecurity.lockoutSecondsLeft() * 1000L + 200L
                )
            }
        }
        val auth = shellButton(if (needsPin) "AUTHENTICATE  ▸  (PIN REQUIRED)" else "AUTHENTICATE  ▸") { runLockVote(it) }
        auth.isEnabled = !needsPin
        pinAuthButton = auth
        lockRoot.addView(auth, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.8).toInt(), -2).apply { gravity = Gravity.CENTER })
        val lockMessage = monoText(
            if (needsPin && lockSecurity.isLockedOut()) "LOCKED OUT — TRY AGAIN IN ${lockSecurity.lockoutSecondsLeft()}s"
            else "AUTHENTICATION REQUIRED", 12f, colorDanger()).apply { gravity = Gravity.CENTER }
        lockMessageView = lockMessage
        lockRoot.addView(lockMessage)
        lockFrame.addView(lockRoot, FrameLayout.LayoutParams(-1, -1))
        revealPixelCanvas(lockFrame)
        phoneFrame.addView(lockFrame, FrameLayout.LayoutParams(-1, -1))
        addScanlines()
        setContentView(glowFrame)

        handler.removeCallbacks(lockTickRunnable)
        lockTickCount = 0
        refreshLockWidget()
        handler.post(lockTickRunnable)
    }

    private fun nextTimerLine(now: Long = System.currentTimeMillis()): String {
        if (!::timerStore.isInitialized) return "NO TIMER RUNNING"
        val running = timerStore.all().filter { it.running }
        val next = running.minByOrNull { it.remainingMillis(now) }
        if (next != null) return "NEXT TIMER — ${next.name} ${formatTimer(next.remainingMillis(now), false)}"
        val paused = timerStore.all().firstOrNull { !it.finished && it.leftMillis > 0 }
        if (paused != null) return "TIMER PAUSED — ${paused.name} ${formatTimer(paused.leftMillis, false)}"
        return "NO TIMER RUNNING"
    }

    private fun magiStatusLine(): String {
        if (!::magiEngine.isInitialized) return "MAGI — NODES UNKNOWN"
        val on = magiEngine.nodes.count { it.enabled }
        val total = magiEngine.nodes.size
        val offline = if (magiEngine.offlineMode.enabled) " · OFFLINE MODE — LOCAL ONLY" else ""
        return "MAGI — $on/$total NODES ON$offline"
    }

    private fun refreshLockWidget() {
        val timer = nextTimerLine()
        val magi = magiStatusLine()
        if (timer == lastSavedLockTimer && magi == lastSavedLockMagi) return
        lastSavedLockTimer = timer
        lastSavedLockMagi = magi
        NervLockWidget.saveTimer(this, timer)
        NervLockWidget.saveMagi(this, magi)
        NervLockWidget.updateAll(this)
    }

    private fun pinDots(): String {
        val len = lockSecurity.pinLength().coerceAtLeast(1)
        return (0 until len).joinToString(" ") { if (it < pinEntry.length) "●" else "○" }
    }

    private fun onPinKey(key: String) {
        if (lockSecurity.isLockedOut()) {
            lockMessageView?.apply {
                text = "LOCKED OUT — TRY AGAIN IN ${lockSecurity.lockoutSecondsLeft()}s"
                setTextColor(colorDanger())
            }
            return
        }
        when (key) {
            "CLR" -> pinEntry = ""
            "⌫" -> if (pinEntry.isNotEmpty()) pinEntry = pinEntry.dropLast(1)
            else -> if (pinEntry.length < lockSecurity.pinLength()) pinEntry += key
        }
        pinDotsView?.text = pinDots()
        if (pinEntry.length != lockSecurity.pinLength() || pinEntry.isEmpty()) return
        val entered = pinEntry
        pinEntry = ""
        if (lockSecurity.verify(entered)) {
            pinVerified = true
            pinDotsView?.text = pinDots()
            pinAuthButton?.apply { isEnabled = true; text = "AUTHENTICATE  ▸" }
            lockMessageView?.apply { text = "PIN ACCEPTED — MAGI VOTE NEXT"; setTextColor(colorAccent()) }
        } else if (lockSecurity.isLockedOut()) {

            showLock()
        } else {
            pinDotsView?.text = pinDots()
            lockMessageView?.apply {
                text = "WRONG PIN — ${lockSecurity.failuresLeft()} TRIES LEFT"
                setTextColor(colorDanger())
            }
        }
    }

    @androidx.annotation.RequiresPermission(Manifest.permission.USE_BIOMETRIC)
    private fun showBiometricPrompt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            lockMessageView?.apply { text = "FINGERPRINT NEEDS ANDROID 10 OR NEWER"; setTextColor(colorDanger()) }
            return
        }
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("NERV — FINGERPRINT")
                .setDescription("Unlock NERV with your fingerprint, then MAGI votes.")
                .setNegativeButton("USE PIN", mainExecutor) { _, _ -> }
                .build()
            prompt.authenticate(
                CancellationSignal(), mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        pinVerified = true
                        pinEntry = ""
                        pinDotsView?.text = pinDots()
                        pinAuthButton?.apply { isEnabled = true; text = "AUTHENTICATE  ▸" }
                        lockMessageView?.apply { text = "FINGERPRINT ACCEPTED — MAGI VOTE NEXT"; setTextColor(colorAccent()) }
                    }

                    override fun onAuthenticationFailed() {
                        lockMessageView?.apply {
                            text = "FINGERPRINT NOT RECOGNISED — TRY AGAIN OR USE PIN"
                            setTextColor(colorDanger())
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        lockMessageView?.apply {
                            text = "FINGERPRINT: ${errString ?: "unavailable"}"
                            setTextColor(colorDanger())
                        }
                    }
                }
            )
        } catch (e: Exception) {
            lockMessageView?.apply { text = "FINGERPRINT UNAVAILABLE"; setTextColor(colorDanger()) }
        }
    }

    private fun runLockVote(button: Button) {
        if (lockVoteRunning) return
        lockVoteRunning = true
        button.isEnabled = false
        button.text = "VOTING…"
        lockMessageView?.apply { text = "MAGI VOTE IN PROGRESS"; setTextColor(colorAccentSecondary()) }
        Thread {
            val result = try {
                magiEngine.voteOnAccess()
            } catch (e: Exception) {
                MagiEngine.VoteResult(emptyList(), ran = false, granted = false)
            }
            runOnUiThread { revealLockVote(result) }
        }.start()
    }

    private fun revealLockVote(result: MagiEngine.VoteResult) {
        val main = Handler(Looper.getMainLooper())
        val votes = result.votes
        votes.forEachIndexed { index, nodeVote ->
            main.postDelayed({
                val valueView = lockNodeValueViews.getOrNull(index) ?: return@postDelayed
                val box = lockNodeBoxViews.getOrNull(index)
                val (label, col) = when (nodeVote.outcome) {
                    MagiEngine.VoteOutcome.APPROVE -> "承認" to AgentTheme.signal
                    MagiEngine.VoteOutcome.DENY -> "否定" to colorDanger()
                    MagiEngine.VoteOutcome.ABSTAIN -> "保留" to AgentTheme.muted
                }
                valueView.text = label
                valueView.setTextColor(col)

                val spoken = when (nodeVote.outcome) {
                    MagiEngine.VoteOutcome.APPROVE -> "approved"
                    MagiEngine.VoteOutcome.DENY -> "denied"
                    MagiEngine.VoteOutcome.ABSTAIN -> "abstained"
                }
                val nodeName = listOf("MELCHIOR·1", "BALTHASAR·2", "CASPER·3").getOrNull(index) ?: "NODE"
                box?.contentDescription = "$nodeName vote — $spoken"
                box?.background = GradientDrawable().apply {
                    setColor(AgentTheme.background); setStroke(dp(2), col)
                }
            }, 400L + index * 450L)
        }
        main.postDelayed({
            if (!result.ran) {
                lockVoteRunning = false
                showNoVoteDialog(result)
                return@postDelayed
            }

            if (::magiMemory.isInitialized) {
                val summary = result.votes.joinToString(" · ") { v ->
                    "${magiEngine.nodeNames[v.node]} ${v.outcome.name}"
                }
                magiMemory.rememberDecision(
                    "LOCK VOTE", summary,
                    if (result.granted) "ACCESS GRANTED" else "ACCESS DENIED"
                )
            }
            if (result.granted) {
                lockMessageView?.apply { text = "ACCESS GRANTED"; setTextColor(AgentTheme.signal) }
                main.postDelayed({
                    locked = false
                    lockVoteRunning = false
                    buildShell()
                    showScreen("home")
                    maybeStartPendingWidgetListen()
                    maybeApplyPendingOpenScreen()
                }, 500)
            } else {
                lockMessageView?.apply { text = "ACCESS DENIED"; setTextColor(colorDanger()) }
                main.postDelayed({ showLock() }, 1400)
            }
        }, 400L + votes.size * 450L + 200L)
    }

    private fun showNoVoteDialog(result: MagiEngine.VoteResult) {
        val reasons = if (result.votes.isEmpty()) {
            "The vote itself failed to run."
        } else {
            result.votes.joinToString("\n") { v ->
                "${magiEngine.nodeNames[v.node]}: ${v.reason}"
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("MAGI vote could not run")
            .setMessage(
                "No MAGI node could vote — check that at least one node is switched on in SETUP and has its key or provider set.\n\n$reasons"
            )
            .setPositiveButton("LOCAL DEMO VOTE") { _, _ -> showDemoVoteDialog() }
            .setNegativeButton("CANCEL") { _, _ -> showLock() }
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun showDemoVoteDialog() {

        val dialog = AlertDialog.Builder(this)
            .setTitle("MAGI vote — demo only")
            .setMessage("All three local demo votes approve access.")
            .setPositiveButton("ACCESS GRANTED") { _, _ -> locked = false; buildShell(); showScreen("home"); maybeStartPendingWidgetListen(); maybeApplyPendingOpenScreen() }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply { setColor(Color.BLACK); setStroke(dp(2), colorAccent()) }
        )
    }

    private fun applyTheme() {
        val colors = palettes.first { it.first == palette }.second

        AgentTheme.accent = if (customAccent != 0) customAccent else colors[0]

        AgentTheme.accentSecondary = if (highContrast) AgentTheme.accent else colors[1]
        AgentTheme.muted = if (highContrast) colors[1] else colors[2]
        AgentTheme.stroke = AgentTheme.accent
        phoneFrame.background = frameDrawable()
        glowFrame.background = GradientDrawable().apply {
            setColor(Color.parseColor("#050200"))
            setStroke(dp(2), AgentTheme.muted)
            cornerRadius = dp(40).toFloat()
        }
        root.setBackgroundColor(AgentTheme.background)

        statusTitleView?.setTextColor(colorAccent())
        statusTime?.setTextColor(colorAccent())
        statusBarView?.background = bottomBorder()
        if (::navBar.isInitialized) navBar.background = topBorder()

        revealPixelCanvas(root)
        pixelCanvasView?.invalidate()
        if (::headerStatus.isInitialized) updateHeaderStatus()
    }

    private fun applyReduceMotion() {
        GlobeView.reduceMotion = reduceMotion
        PsychographView.reduceMotion = reduceMotion
        EEGView.reduceMotion = reduceMotion
        PixelCanvasView.reduceMotion = reduceMotion
        pixelCanvasView?.invalidate()
        if (reduceMotion && ::phoneFrame.isInitialized) phoneFrame.alpha = 1f
    }

    private fun applyBatterySaver() {
        val scale = batterySaver.animationScale()
        GlobeView.speedScale = scale
        PixelCanvasView.speedScale = scale
        pixelCanvasView?.invalidate()
        PsychographView.speedScale = scale
        EEGView.speedScale = scale
        if (::headerStatus.isInitialized) updateHeaderStatus()
    }

    private fun playTapBeep() {
        if (!soundOn) return
        try {
            if (tapTone == null) tapTone = ToneGenerator(AudioManager.STREAM_MUSIC, 30)
            tapTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
        } catch (e: Exception) {
        }
    }

    private fun textScale() = textSizeSp / DisplaySettings.DEFAULT_TEXT_SIZE_SP.toFloat() *
        (if (textBoost) 1.25f else 1f)

    private fun title(glyph: String, english: String, subtitle: String): View {
        val band = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        band.addView(doubleRule())
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        row.addView(minchoText(glyph, 22f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(monoText("$english\n$subtitle", 10f, colorAccentSecondary()).apply { gravity = Gravity.END })
        row.addView(minchoText(glyph, 22f, colorAccent()).apply { gravity = Gravity.END })
        band.addView(row)
        band.addView(doubleRule())
        return band
    }

    private fun homeTitle(glyph: String, english: String, subtitle: String): View =
        title(glyph, english, subtitle)

    private fun doubleRule(): View {
        val rule = LinearLayout(this)
        rule.orientation = LinearLayout.VERTICAL
        rule.layoutParams = LinearLayout.LayoutParams(-1, dp(3))
        val topLine = View(this)
        topLine.setBackgroundColor(colorAccent())
        topLine.layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        val bottomLine = View(this)
        bottomLine.setBackgroundColor(colorAccent())
        bottomLine.layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(1) }
        rule.addView(topLine)
        rule.addView(bottomLine)
        return rule
    }

    private fun homeModeButton(textValue: String, selected: Boolean, action: (Button) -> Unit): Button =
        Button(this).apply {
            text = textValue
            contentDescription = textValue
            isAllCaps = false
            textSize = 12f * textScale()
            setTextColor(if (selected) AgentTheme.background else colorAccent())
            background = GradientDrawable().apply {
                setColor(if (selected) colorDanger() else AgentTheme.background)
                setStroke(dp(2), if (selected) colorDanger() else colorAccent())
                cornerRadius = 0f
            }
            setPadding(dp(2), dp(6), dp(2), dp(6))
            minimumHeight = 0
            setOnClickListener { playTapBeep(); action(this) }
        }

    private fun borderedPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = GradientDrawable().apply { setColor(AgentTheme.background); setStroke(dp(2), colorAccent()) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun toggleRow(label: String, state: String, action: (Button) -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            addView(monoText(label, 13f, colorAccent()), LinearLayout.LayoutParams(0, -2, 1f))
            addView(shellButton(state, action), LinearLayout.LayoutParams(dp(70), -2))
        }

    private fun boxHeading(value: String): TextView = monoText(value, 13f, colorAccentSecondary()).apply {
        letterSpacing = 0.08f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    }

    private fun htmlInputStyle(input: EditText) {
        AgentTheme.style(input)
        input.background = GradientDrawable().apply {
            setColor(AgentTheme.background)
            setStroke(dp(2), colorAccent())
        }
        input.setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun shellButton(textValue: String, action: (Button) -> Unit): Button =
        Button(this).apply {
            text = textValue

            contentDescription = textValue
            isAllCaps = false

            textSize = 12f * textScale()
            typeface = AgentTheme.monoTypeface(this@NervActivity)
            setTextColor(colorAccent())
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33ff9a1a")),
                GradientDrawable().apply {
                    setColor(AgentTheme.background)
                    setStroke(dp(2), colorAccent())
                },
                null
            )
            setPadding(dp(2), dp(6), dp(2), dp(6))
            minimumHeight = 0
            minimumWidth = 0
            setOnClickListener { playTapBeep(); action(this) }
        }

    private fun shellButtonOn(textValue: String, action: (Button) -> Unit): Button =
        shellButton(textValue, action).apply {
            setTextColor(AgentTheme.background)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33ff2a1a")),
                GradientDrawable().apply {
                    setColor(colorDanger())
                    setStroke(dp(2), colorDanger())
                },
                null
            )
        }

    private fun shellButtonOk(textValue: String, action: (Button) -> Unit): Button =
        shellButton(textValue, action).apply {
            setTextColor(AgentTheme.background)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#336fffe0")),
                GradientDrawable().apply {
                    setColor(AgentTheme.signal)
                    setStroke(dp(2), AgentTheme.signal)
                },
                null
            )
        }

    private fun monoText(value: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = value

        textSize = size * textScale()
        setTextColor(color)
        typeface = AgentTheme.monoTypeface(this@NervActivity)
        includeFontPadding = false
    }

    private fun minchoText(value: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = AgentTheme.minchoTypeface(this@NervActivity)
        includeFontPadding = false
    }

    private fun dp(value: Int) = AgentTheme.dp(this, value)
    private fun colorAccent() = AgentTheme.accent
    private fun colorAccentSecondary() = AgentTheme.accentSecondary
    private fun colorDanger() = AgentTheme.danger

    private fun bottomBorder() = InsetDrawable(
        GradientDrawable().apply {
            setColor(AgentTheme.background)
            setStroke(dp(2), colorAccent())
        },
        -dp(2), -dp(2), -dp(2), 0
    )
    private fun topBorder() = InsetDrawable(
        GradientDrawable().apply {
            setColor(AgentTheme.background)
            setStroke(dp(2), colorAccent())
        },
        -dp(2), 0, -dp(2), -dp(2)
    )
}

class PixelCanvasView(context: Context) : View(context) {
    companion object {
        var reduceMotion = false
        var speedScale = 1f
    }

    private data class Pixel(
        val x: Float, val y: Float, val colorIndex: Int,
        val speed: Float, val sizeStep: Float, val maxSize: Float,
        val delay: Float, val counterStep: Float,
        var size: Float = 0f, var counter: Float = 0f,
        var reverse: Boolean = false, var shimmer: Boolean = false,
    )

    private val density = resources.displayMetrics.density
    private val pixels = ArrayList<Pixel>()
    private val paints = intArrayOf(Color.WHITE, 0xffd9d9d9.toInt(), 0xff9a9a9a.toInt())
        .map { value -> Paint().apply { color = value; style = Paint.Style.FILL } }
    private val paths = Array(3) { Path() }
    private var lastFrame = 0L
    var running = true
        set(value) {
            field = value
            lastFrame = 0L
            if (value) invalidate()
        }

    init {
        alpha = 0.90f
        isClickable = false
        isFocusable = false
        AccessibilityHelper.decorative(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pixels.clear()
        val random = java.util.Random(0x504958454cL)
        val logicalWidth = w / density
        val logicalHeight = h / density
        var x = 0f
        while (x < logicalWidth) {
            var y = 0f
            while (y < logicalHeight) {
                val dx = x - logicalWidth / 2f
                val dy = y - logicalHeight / 2f
                pixels.add(Pixel(
                    x, y, random.nextInt(3),
                    (0.1f + random.nextFloat() * 0.8f) * 0.025f,
                    random.nextFloat() * 0.4f,
                    0.5f + random.nextFloat() * 1.5f,
                    kotlin.math.sqrt(dx * dx + dy * dy),
                    random.nextFloat() * 4f + (logicalWidth + logicalHeight) * 0.01f,
                ))
                y += 10f
            }
            x += 10f
        }
        lastFrame = 0L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = android.os.SystemClock.uptimeMillis()
        val frameScale = if (lastFrame == 0L) 1f else
            ((now - lastFrame) / (1000f / 60f)).coerceIn(0f, 4f)
        lastFrame = now
        val animate = running && !reduceMotion && speedScale > 0f
        val step = frameScale * speedScale
        paths.forEach { it.rewind() }
        for (pixel in pixels) {
            if (reduceMotion) {
                pixel.size = pixel.maxSize
            } else if (animate) {
                if (pixel.counter <= pixel.delay) {
                    pixel.counter += pixel.counterStep * step
                    continue
                }
                if (pixel.size >= pixel.maxSize) pixel.shimmer = true
                if (pixel.shimmer) {
                    if (pixel.size >= pixel.maxSize) pixel.reverse = true
                    else if (pixel.size <= 0.5f) pixel.reverse = false
                    pixel.size += (if (pixel.reverse) -pixel.speed else pixel.speed) * step
                } else {
                    pixel.size += pixel.sizeStep * step
                }
            }
            val size = pixel.size.coerceAtLeast(0f)
            if (size == 0f) continue
            val offset = 1f - size / 2f
            paths[pixel.colorIndex].addRect(
                pixel.x + offset, pixel.y + offset,
                pixel.x + offset + size, pixel.y + offset + size,
                Path.Direction.CW,
            )
        }
        val save = canvas.save()
        canvas.scale(density, density)
        for (index in paths.indices) canvas.drawPath(paths[index], paints[index])
        canvas.restoreToCount(save)
        if (animate && isShown && windowVisibility == VISIBLE) {
            postInvalidateDelayed(if (speedScale < 1f) 66L else 33L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        lastFrame = 0L
        if (visibility == VISIBLE) invalidate()
    }
}

class ScanlinesView(context: Context) : View(context) {

    init { AccessibilityHelper.decorative(this) }
    private val paint = Paint().apply { color = Color.BLACK; alpha = 72 }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = 3f * resources.displayMetrics.density
        val thick = resources.displayMetrics.density
        var y = 0f
        while (y < height) {
            canvas.drawRect(0f, y, width.toFloat(), y + thick, paint)
            y += step
        }
    }
}

class GlobeView(context: Context) : View(context) {

    init { contentDescription = "Rotating globe. Drag to spin it. The amber marker is this phone's location." }
    private var angle = 0.0
    private var rotationOffset = 0.0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ff9a1a")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ffd21a")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ff2a1a")
        style = Paint.Style.FILL
    }

    private val cityDots = listOf(35.0 to 139.0, -33.0 to 151.0, 51.0 to 0.0, 40.0 to -74.0)

    private var youLat: Double? = null
    private var youLon: Double? = null
    private val youPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ffd21a")
        style = Paint.Style.FILL
    }

    companion object { var reduceMotion = false; var speedScale = 1f }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (reduceMotion) { handler.postDelayed(this, 500); return }
            angle += 0.004 * speedScale
            invalidate()
            handler.postDelayed(this, 50)
        }
    }
    init { handler.post(tick) }
    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
    fun addRotation(delta: Double) { rotationOffset += delta }
    fun rotationDegrees(): Double {
        val deg = (angle + rotationOffset) * 180.0 / Math.PI % 360.0
        return if (deg < 0) deg + 360.0 else deg
    }

    fun setYouMarker(lat: Double, lon: Double) {
        youLat = lat
        youLon = lon
        invalidate()
    }
    fun centreOn(longitudeDeg: Double) {
        rotationOffset = -Math.toRadians(longitudeDeg) - angle
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.color = AgentTheme.accent
        val totalAngle = angle + rotationOffset
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.38f

        val tilt = 0.45
        val cosT = cos(tilt)
        val sinT = sin(tilt)

        fun project(latDeg: Double, lonDeg: Double): Triple<Float, Float, Double> {
            val la = Math.toRadians(latDeg)
            val lo = Math.toRadians(lonDeg) + totalAngle
            val x = cos(la) * sin(lo)
            val y = sin(la)
            val z = cos(la) * cos(lo)
            val sx = cx + (x * r).toFloat()
            val sy = cy - ((y * cosT - z * sinT) * r).toFloat()
            val depth = y * sinT + z * cosT
            return Triple(sx, sy, depth)
        }

        fun polyline(points: List<Triple<Float, Float, Double>>) {
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                paint.alpha = if (a.third + b.third > 0) 255 else 51
                canvas.drawLine(a.first, a.second, b.first, b.second, paint)
            }
        }
        paint.alpha = 255
        canvas.drawCircle(cx, cy, r, paint)

        for (lat in -80..80 step 20) {
            polyline((0..360 step 8).map { lon -> project(lat.toDouble(), lon.toDouble()) })
        }
        for (lon in 0 until 360 step 20) {
            polyline((-90..90 step 8).map { lat -> project(lat.toDouble(), lon.toDouble()) })
        }
        paint.alpha = 255

        canvas.save()
        canvas.rotate(Math.toDegrees(-0.35).toFloat(), cx, cy)
        canvas.drawOval(cx - r * 1.25f, cy - r * 0.2f, cx + r * 1.25f, cy + r * 0.2f, yellowPaint)
        canvas.restore()
        canvas.save()
        canvas.rotate(Math.toDegrees(0.5).toFloat(), cx, cy)
        canvas.drawOval(cx - r * 1.1f, cy - r * 0.12f, cx + r * 1.1f, cy + r * 0.12f, yellowPaint)
        canvas.restore()

        cityDots.forEach { (la, lo) ->
            val p = project(la, lo)
            if (p.third > 0) canvas.drawRect(p.first - 4, p.second - 4, p.first + 4, p.second + 4, redPaint)
        }

        val yLat = youLat
        val yLon = youLon
        if (yLat != null && yLon != null) {
            val p = project(yLat, yLon)
            if (p.third > 0.05) {
                canvas.drawRect(p.first - 6, p.second - 6, p.first + 6, p.second + 6, youPaint)
                canvas.drawOval(p.first - 11, p.second - 11, p.first + 11, p.second + 11, yellowPaint)
            }
        }
    }
}

class StripedBarView(context: Context) : View(context) {
    private var value = 40
    private var hot = false
    private val trackPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 2f }

    private val trackFillPaint = Paint().apply { color = Color.parseColor("#001a3a") }
    private val barPaint = Paint().apply { color = Color.parseColor("#2f7bff") }
    private val hotPaint = Paint().apply { color = Color.parseColor("#ff2a1a") }

    fun setValue(v: Int) {
        value = v.coerceIn(0, 100)
        contentDescription = "Level $value out of 100"
        invalidate()
    }
    fun setHot(isHot: Boolean) { hot = isHot; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var tx = 0f
        val tStep = 7f * resources.displayMetrics.density
        val tStripe = 5f * resources.displayMetrics.density
        while (tx < width) {
            canvas.drawRect(tx, 0f, minOf(tx + tStripe, width.toFloat()), height.toFloat(), trackFillPaint)
            tx += tStep
        }
        trackPaint.color = AgentTheme.muted
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), trackPaint)
        val w = width * value / 100f
        val fillPaint = if (hot) hotPaint else barPaint
        var x = 0f
        val step = 7f * resources.displayMetrics.density
        val stripe = 5f * resources.displayMetrics.density
        while (x < w) {
            canvas.drawRect(x, 0f, minOf(x + stripe, w), height.toFloat(), fillPaint)
            x += step
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            (16 * resources.displayMetrics.density).toInt()
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }
}

class MagiNodeLayout(context: Context, private val mode: Int) : LinearLayout(context) {
    private val clipPath = Path()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        when (mode) {
            0 -> {
                clipPath.moveTo(0f, 0f)
                clipPath.lineTo(w.toFloat(), 0f)
                clipPath.lineTo(w.toFloat(), h * 0.7f)
                clipPath.lineTo(w * 0.82f, h.toFloat())
                clipPath.lineTo(w * 0.18f, h.toFloat())
                clipPath.lineTo(0f, h * 0.7f)
            }
            1 -> {
                clipPath.moveTo(0f, 0f)
                clipPath.lineTo(w * 0.82f, 0f)
                clipPath.lineTo(w.toFloat(), h * 0.18f)
                clipPath.lineTo(w.toFloat(), h.toFloat())
                clipPath.lineTo(0f, h.toFloat())
            }
            else -> {
                clipPath.moveTo(w * 0.18f, 0f)
                clipPath.lineTo(w.toFloat(), 0f)
                clipPath.lineTo(w.toFloat(), h.toFloat())
                clipPath.lineTo(0f, h.toFloat())
                clipPath.lineTo(0f, h * 0.18f)
            }
        }
        clipPath.close()
    }
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clipPath)
        super.draw(canvas)
        canvas.restore()
    }
}

class ClippedTileLayout(context: Context) : LinearLayout(context) {
    private val clipPath = Path()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cut = 14f * resources.displayMetrics.density
        clipPath.reset()
        clipPath.moveTo(0f, 0f)
        clipPath.lineTo(w - cut, 0f)
        clipPath.lineTo(w.toFloat(), cut)
        clipPath.lineTo(w.toFloat(), h.toFloat())
        clipPath.lineTo(0f, h.toFloat())
        clipPath.close()
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clipPath)
        super.draw(canvas)
        canvas.restore()
    }
}

class PsychographView(context: Context, private val syncProvider: () -> Int) : View(context) {

    init { contentDescription = "Sync waves. Two lines showing how in sync MAGI is." }
    private var t = 0.0
    private val pinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ff3a90"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4a8bff"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ff9a1a"); textSize = 11f * resources.displayMetrics.density
    }

    companion object { var reduceMotion = false; var speedScale = 1f }
    private val wavePath = Path()

    private val waves = listOf(pinkPaint to 0.0, bluePaint to 0.5)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (reduceMotion) { handler.postDelayed(this, 500); return }
            t += 0.03 * speedScale
            invalidate()
            handler.postDelayed(this, 50)
        }
    }
    init { handler.post(tick) }
    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        textPaint.color = AgentTheme.accent
        val f = 1.0 + syncProvider() / 25.0
        val w = width.toFloat()
        val h = height.toFloat()
        waves.forEach { (paint, phase) ->
            wavePath.reset()
            var x = 0f
            while (x <= w) {
                val y = h / 2 + (sin(x / w * Math.PI * 2 + t * f + phase) * 70 * sin(x / 40 + t * 0.5)).toFloat()
                if (x == 0f) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                x += 4f
            }
            canvas.drawPath(wavePath, paint)
        }
        val stamp = "0:14:" + String.format(Locale.getDefault(), "%04d", ((t * 100).toInt() % 10000))
        canvas.drawText(stamp, w - 110f * resources.displayMetrics.density, 14f * resources.displayMetrics.density, textPaint)
    }
}

class EEGView(context: Context, private val ampProvider: () -> FloatArray = { FloatArray(6) { 1f } }) : View(context) {

    init { contentDescription = "Brain wave traces. Six channels: temporal, amygdala, hippocampus, parietal, motor, sensory." }
    private val channels = listOf("TEMPORAL", "AMYGDALA", "HIPPOCAMPUS", "PARIETAL", "MOTOR", "SENSORY")

    private val histories = List(6) { ArrayDeque<Float>() }
    private var t = 0.0
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3dff7a"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ff9a1a"); textSize = 9f * resources.displayMetrics.density
    }
    private val redPaint = Paint().apply { color = Color.parseColor("#ff2a1a"); alpha = 230 }

    companion object { var reduceMotion = false; var speedScale = 1f }
    private val tracePath = Path()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (reduceMotion) { handler.postDelayed(this, 500); return }
            t += 0.03 * speedScale
            val colW = width / 6f
            val amps = ampProvider()
            for (i in channels.indices) {
                val noise = if (i == 4) 0.1f else 1.1f
                val amp = (if (i == 4) 1.4f else 1.0f) * amps.getOrElse(i) { 1f }
                val v = (sin(t * (i + 2) * 0.9) * 0.5 + (Math.random() - 0.5) * noise).toFloat() * colW * 0.38f * amp
                histories[i].addLast(v)
                if (histories[i].size > 160) histories[i].removeFirst()
            }
            invalidate()
            handler.postDelayed(this, 50)
        }
    }
    init { handler.post(tick) }
    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        labelPaint.color = AgentTheme.accent
        canvas.drawColor(Color.BLACK)
        val colW = width / 6f
        for (i in channels.indices) {
            val cx = i * colW + colW / 2
            tracePath.reset()

            histories[i].reversed().forEachIndexed { k, v ->
                val x = cx + v
                val y = 26f * resources.displayMetrics.density + k * resources.displayMetrics.density
                if (k == 0) tracePath.moveTo(x, y) else tracePath.lineTo(x, y)
            }
            canvas.drawPath(tracePath, tracePaint)
            canvas.save()
            canvas.translate(i * colW + 8f * resources.displayMetrics.density, 24f * resources.displayMetrics.density)
            canvas.rotate(90f)
            canvas.drawText(channels[i], 0f, 0f, labelPaint)
            canvas.restore()
        }
        val scanY = 26f * resources.displayMetrics.density + ((t * 30).toInt() % 260) * resources.displayMetrics.density
        canvas.drawRect(0f, scanY, width.toFloat(), scanY + 2f * resources.displayMetrics.density, redPaint)
    }
}
