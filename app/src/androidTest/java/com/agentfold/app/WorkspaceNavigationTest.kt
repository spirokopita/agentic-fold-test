package com.agentfold.app

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentfold.app.agent.AgentRuntime
import com.agentfold.app.ui.AdaptiveWorkspace
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorkspaceNavigationTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun select(activity: MainActivity, panel: AdaptiveWorkspace.Panel) {
        views(activity.window.decorView).filterIsInstance<Button>()
            .single { it.contentDescription?.startsWith("${panel.label} workspace tab") == true }.performClick()
    }

    private fun assertAppWindow(automation: UiAutomation, packageName: String) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var observed: String? = null
        do {
            val node = automation.rootInActiveWindow
            observed = node?.packageName?.toString()
            @Suppress("DEPRECATION") node?.recycle()
            if (observed == packageName) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertEquals("Capture must show this app without a covering dialog", packageName, observed)
    }

    @Test fun everyWorkspaceKeepsStopVisibleAndOffersRealControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val dir = File(instrumentation.targetContext.filesDir, "ui-evidence").apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (panel in AdaptiveWorkspace.Panel.values()) {
                scenario.onActivity { activity ->
                    select(activity, panel)
                    val children = views(activity.window.decorView)
                    val buttons = children.filterIsInstance<Button>()
                    assertEquals(panel, children.filterIsInstance<AdaptiveWorkspace>().single().selection)
                    assertTrue(buttons.first { it.text.startsWith("STOP") }.isShown)
                    assertTrue(buttons.single { it.contentDescription?.startsWith("${panel.label} workspace tab") == true }.isSelected)
                    when (panel) {
                        AdaptiveWorkspace.Panel.TASK -> assertTrue(activity.findViewById<EditText>(R.id.objective_input).isShown)
                        AdaptiveWorkspace.Panel.FILES -> assertTrue(buttons.single { it.text == "Choose a file" }.isShown)
                        AdaptiveWorkspace.Panel.CONTROL -> assertTrue(buttons.single { it.text == "2 · TAKE CONTROL" }.isShown)
                        AdaptiveWorkspace.Panel.HISTORY -> assertTrue(buttons.single { it.text == "Inspect task history" }.isShown)
                        else -> Unit
                    }
                    assertFalse(AgentRuntime.get(activity).running)
                    assertFalse(AgentAccessibilityService.instance?.controlActive ?: false)
                }
                if (panel != AdaptiveWorkspace.Panel.ACTIVITY) {
                    instrumentation.waitForIdleSync()
                    assertAppWindow(automation, instrumentation.targetContext.packageName)
                    requireNotNull(automation.takeScreenshot()).let { image ->
                        try {
                            File(dir, "workspace-${panel.name.lowercase()}.png").outputStream().use {
                                image.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        } finally { image.recycle() }
                    }
                }
            }
        }
    }

    @Test fun selectedWorkspaceAndHiddenDraftSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                select(activity, AdaptiveWorkspace.Panel.TASK)
                activity.findViewById<EditText>(R.id.objective_input).setText("Keep my unsubmitted workspace draft")
                activity.findViewById<EditText>(R.id.x_coordinate).setText("38")
                activity.findViewById<EditText>(R.id.y_coordinate).setText("72")
                assertTrue(activity.findViewById<EditText>(R.id.objective_input).requestFocus())
                select(activity, AdaptiveWorkspace.Panel.FILES)
                assertFalse("Leaving the editor must release its focus", activity.findViewById<EditText>(R.id.objective_input).hasFocus())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val workspace = views(activity.window.decorView).filterIsInstance<AdaptiveWorkspace>().single()
                assertEquals(AdaptiveWorkspace.Panel.FILES, workspace.selection)
                assertEquals("Keep my unsubmitted workspace draft", activity.findViewById<EditText>(R.id.objective_input).text.toString())
                assertEquals("38", activity.findViewById<EditText>(R.id.x_coordinate).text.toString())
                assertEquals("72", activity.findViewById<EditText>(R.id.y_coordinate).text.toString())
                select(activity, AdaptiveWorkspace.Panel.TASK)
                assertTrue(activity.findViewById<EditText>(R.id.objective_input).isShown)
                assertFalse(AgentRuntime.get(activity).running)
                assertFalse(AgentAccessibilityService.instance?.controlActive ?: false)
            }
        }
    }
}
