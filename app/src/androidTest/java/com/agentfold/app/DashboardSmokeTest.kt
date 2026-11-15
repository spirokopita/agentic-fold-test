package com.agentfold.app

import android.content.pm.ActivityInfo
import android.widget.Button
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentfold.app.ui.ActivityNetworkView
import com.agentfold.app.ui.neural.NeuralDialView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun neural(view: View): NeuralDialView? {
        if (view is NeuralDialView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) neural(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun legacy(view: View): ActivityNetworkView? {
        if (view is ActivityNetworkView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) legacy(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun dashboardStartsWithVisibleStopAndNoImplicitControl() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val stop = buttons(activity.window.decorView).first { it.text.toString().startsWith("STOP") }
                assertTrue(stop.isShown)
                stop.performClick()
                assertFalse(AgentAccessibilityService.instance?.controlActive ?: false)
            }
        }
    }
    @Test fun stopImmediatelyExitsDevelopmentPreview() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.visualController.preview(com.agentfold.app.ui.neural.VisualState.INTERNAL_THINKING)
                assertTrue(activity.visualController.snapshot.preview)
                buttons(activity.window.decorView).first { it.text.toString().startsWith("STOP") }.performClick()
                assertFalse(activity.visualController.snapshot.preview)
                assertEquals(com.agentfold.app.ui.neural.VisualState.THINKING_FIELD,
                    activity.visualController.snapshot.state)
                assertEquals(0f, activity.visualController.snapshot.activity)
            }
        }
    }
    @Test fun processStopSignalExitsDevelopmentPreview() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.visualController.preview(com.agentfold.app.ui.neural.VisualState.CONFLICT) }
            VisualStopSignal.signal()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertFalse(it.visualController.snapshot.preview)
                assertEquals(com.agentfold.app.ui.neural.VisualState.THINKING_FIELD,
                    it.visualController.snapshot.state)
            }
        }
    }
    @Test fun rendererFailureAutomaticallyRestoresLegacyActivityView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val neural = requireNotNull(neural(activity.window.decorView))
                assertEquals(View.VISIBLE, neural.visibility)
                val failure = NeuralDialView::class.java.getDeclaredMethod("handleRendererFailure")
                failure.isAccessible = true
                failure.invoke(neural)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, requireNotNull(neural(activity.window.decorView)).visibility)
                assertEquals(View.VISIBLE, requireNotNull(legacy(activity.window.decorView)).visibility)
                assertTrue(buttons(activity.window.decorView).any {
                    it.text.toString().startsWith("STOP") && it.isShown
                })
            }
        }
    }
    @Test fun recreationDoesNotGrantControl() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { assertFalse(AgentAccessibilityService.instance?.controlActive ?: false) }
        }
    }
    @Test fun recreationPreservesUnsubmittedInputs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<EditText>(R.id.objective_input).setText("Investigate my selected crash report")
                it.findViewById<EditText>(R.id.x_coordinate).setText("38")
                it.findViewById<EditText>(R.id.y_coordinate).setText("72")
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals("Investigate my selected crash report", it.findViewById<EditText>(R.id.objective_input).text.toString())
                assertEquals("38", it.findViewById<EditText>(R.id.x_coordinate).text.toString())
                assertEquals("72", it.findViewById<EditText>(R.id.y_coordinate).text.toString())
                assertFalse(AgentAccessibilityService.instance?.controlActive ?: false)
            }
        }
    }
    @Test fun rotationPreservesStopPath() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(buttons(activity.window.decorView).any { it.text.toString().startsWith("STOP") && it.isShown })
            }
        }
    }
}
