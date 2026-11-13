package com.agentfold.app

import android.content.pm.ActivityInfo
import android.widget.Button
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
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
