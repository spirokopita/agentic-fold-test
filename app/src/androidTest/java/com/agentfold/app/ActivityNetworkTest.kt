package com.agentfold.app

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentfold.app.agent.ActivityStage
import com.agentfold.app.agent.AgentRuntime
import com.agentfold.app.agent.ModelProvider
import com.agentfold.app.agent.ModelReply
import com.agentfold.app.agent.ToolScope
import com.agentfold.app.ui.ActivityNetworkView
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import android.graphics.Bitmap
import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect

@RunWith(AndroidJUnit4::class)
class ActivityNetworkTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun assertAppWindow(automation: UiAutomation, packageName: String) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var observed: String? = null
        do {
            val root = automation.rootInActiveWindow
            observed = root?.packageName?.toString()
            @Suppress("DEPRECATION") root?.recycle()
            if (observed == packageName) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertEquals("Evidence must show the app, without a system dialog covering it", packageName, observed)
    }

    private fun onLiveActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {

        InstrumentationRegistry.getInstrumentation().runOnMainSync { scenario.onActivity { action(it) } }
    }

    @Test fun liveModelActivityAndStopComeFromTheRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val runtime = AgentRuntime.get(instrumentation.targetContext)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val provider = object : ModelProvider {
            override fun complete(messages: JSONArray, tools: JSONArray, cancelled: AtomicBoolean): ModelReply {
                entered.countDown()
                check(release.await(60, TimeUnit.SECONDS))
                throw CancellationException()
            }
            override fun cancel() { release.countDown() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(runtime.start("UI test fixture: provider waiting", ToolScope(null, false), provider, "UI test provider"))
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))

                val readyDeadline = SystemClock.elapsedRealtime() + 10_000
                var observed = ""
                do {
                    onLiveActivity(scenario) { activity ->
                        observed = views(activity.window.decorView).filterIsInstance<ActivityNetworkView>()
                            .single().contentDescription.toString()
                    }
                    if (observed.contains("Task MODEL")) break
                    SystemClock.sleep(100)
                } while (SystemClock.elapsedRealtime() < readyDeadline)
                assertTrue("Expected active model telemetry: $observed", observed.contains("Task MODEL"))
                onLiveActivity(scenario) { activity ->
                    val children = views(activity.window.decorView)
                    children.filterIsInstance<Button>().single { it.contentDescription?.startsWith("Task workspace tab") == true }.performClick()
                    val graph = children.filterIsInstance<ActivityNetworkView>().single()
                    assertFalse(graph.isShown)
                    assertFalse(graph.animationsRunning)
                    assertEquals(ActivityStage.MODEL, runtime.activity.stage)
                    children.filterIsInstance<Button>().single { it.contentDescription?.startsWith("Files workspace tab") == true }.performClick()
                    assertTrue(runtime.running)
                    children.filterIsInstance<Button>().single { it.contentDescription?.startsWith("Activity workspace tab") == true }.performClick()
                }
                val visibleDeadline = SystemClock.elapsedRealtime() + 5_000
                var visible = false
                do {
                    onLiveActivity(scenario) { activity ->
                        val graph = views(activity.window.decorView).filterIsInstance<ActivityNetworkView>().single()
                        visible = graph.isShown && graph.animationsRunning
                    }
                    if (visible) break
                    SystemClock.sleep(100)
                } while (SystemClock.elapsedRealtime() < visibleDeadline)
                assertTrue("Visible active graph must resume animation", visible)
                val chartBounds = Rect()
                val requireMotion = InstrumentationRegistry.getArguments().getString("requireAnimations") == "true"
                onLiveActivity(scenario) { activity ->
                    val children = views(activity.window.decorView)
                    val network = children.filterIsInstance<ActivityNetworkView>().single()
                    assertTrue(network.contentDescription.toString().contains("Task MODEL"))
                    assertTrue(network.contentDescription.toString().contains("Manual device control off"))
                    val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (requireMotion) {
                        assertTrue("CI must enable animator duration scale", ValueAnimator.areAnimatorsEnabled())
                        assertFalse("CI motion test requires Battery Saver off", power.isPowerSaveMode)
                    }
                    if (ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode) assertTrue(network.animationsRunning)
                    assertTrue(network.getGlobalVisibleRect(chartBounds))
                }
                val dir = File(instrumentation.targetContext.filesDir, "ui-evidence").apply { mkdirs() }
                onLiveActivity(scenario) { activity ->
                    val children = views(activity.window.decorView)
                    children.filterIsInstance<Button>().single { it.contentDescription?.startsWith("Network activity view") == true }.performClick()
                    val network = children.filterIsInstance<ActivityNetworkView>().single()
                    assertEquals(ActivityNetworkView.Presentation.NETWORK, network.presentation)
                    assertEquals(ActivityStage.MODEL, runtime.activity.stage)
                    assertTrue(network.contentDescription.toString().contains("Manual device control off"))
                }
                SystemClock.sleep(100)
                assertAppWindow(automation, instrumentation.targetContext.packageName)
                requireNotNull(automation.takeScreenshot()).let { image ->
                    try { File(dir, "network-activity.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { image.recycle() }
                }
                onLiveActivity(scenario) { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>().single { it.contentDescription?.startsWith("Signal activity view") == true }.performClick()
                    assertEquals(ActivityStage.MODEL, runtime.activity.stage)
                }
                SystemClock.sleep(100)
                var previousChart: Bitmap? = null
                var changedPixels = false
                try {
                    for (index in 1..3) {
                        assertAppWindow(automation, instrumentation.targetContext.packageName)
                        val image = requireNotNull(automation.takeScreenshot())
                        try {
                            File(dir, "model-activity-0$index.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            val chart = Bitmap.createBitmap(image, chartBounds.left, chartBounds.top,
                                chartBounds.width(), chartBounds.height())
                            previousChart?.let { changedPixels = changedPixels || !it.sameAs(chart); it.recycle() }
                            previousChart = chart
                        } finally { image.recycle() }
                        SystemClock.sleep(350)
                    }
                    if (requireMotion) assertTrue("Active chart must render moving pixels, not only a running animator", changedPixels)
                } finally {
                    previousChart?.recycle()
                }
                onLiveActivity(scenario) { activity ->
                    val children = views(activity.window.decorView)
                    val network = children.filterIsInstance<ActivityNetworkView>().single()
                    children.filterIsInstance<Button>().first { it.text.startsWith("STOP") }.performClick()
                    assertEquals(ActivityStage.CANCELLED, runtime.activity.stage)
                    assertFalse(network.animationsRunning)
                }
                instrumentation.waitForIdleSync()
                onLiveActivity(scenario) { activity ->
                    assertTrue(views(activity.window.decorView).filterIsInstance<ActivityNetworkView>().single()
                        .contentDescription.toString().contains("Task STOPPED"))
                }
            } finally {
                runtime.cancel(); release.countDown()
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (runtime.running && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
                assertFalse("Task must finish cancellation", runtime.running)
            }
        }
    }

    @Test fun idleNetworkDoesNotAnimateOrGrantDeviceControl() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val network = views(activity.window.decorView).filterIsInstance<ActivityNetworkView>().single()
                assertFalse(network.animationsRunning)
                assertFalse(AgentAccessibilityService.instance?.controlActive ?: false)
                assertTrue(network.contentDescription.toString().contains("model-driven control unavailable"))
            }
        }
    }

    @Test fun activityTabSurvivesRecreationWithoutStartingWork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>()
                        .single { it.contentDescription?.startsWith("Network activity view") == true }.performClick()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val children = views(activity.window.decorView)
                    val graph = children.filterIsInstance<ActivityNetworkView>().single()
                    assertEquals(ActivityNetworkView.Presentation.NETWORK, graph.presentation)
                    assertTrue(children.filterIsInstance<Button>().single { it.contentDescription?.startsWith("Network activity view") == true }.isSelected)
                    assertFalse(graph.animationsRunning)
                    assertFalse(AgentRuntime.get(activity).running)
                    assertFalse(AgentAccessibilityService.instance?.controlActive ?: false)
                }
            } finally {
                scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>()
                        .single { it.contentDescription?.startsWith("Signal activity view") == true }.performClick()
                }
            }
        }
    }
}
