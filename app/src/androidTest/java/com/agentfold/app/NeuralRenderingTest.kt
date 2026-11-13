package com.agentfold.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentfold.app.ui.neural.NeuralDialView
import com.agentfold.app.ui.neural.VisualState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NeuralRenderingTest {
    private fun find(view: View): NeuralDialView? {
        if (view is NeuralDialView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun onMain(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { scenario.onActivity { action(it) } }
    }
    @Test fun captureAllStatesAndKeepSimulationExplicit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (state in VisualState.entries) {
                onMain(scenario) { it.visualController.preview(state) }
                Thread.sleep(1300) // Let the real frame clock complete the one-second morph.
                onMain(scenario) { activity ->
                    val view = requireNotNull(find(activity.window.decorView))
                    assertTrue(view.width > 0 && view.height > 0)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    assertFalse(view.contentDescription.toString().contains("unavailable"))
                    assertTrue(view.contentDescription.toString().contains("simulation"))
                    if (state == VisualState.THINKING_FIELD) assertEquals(Color.BLACK, bitmap.getPixel(view.width / 2, view.height / 2))
                    val output = File(activity.getExternalFilesDir(null), "neural-evidence").apply { mkdirs() }
                    File(output, "${state.name.lowercase()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            onMain(scenario) { it.visualController.endPreview() }
            scenario.recreate()
            onMain(scenario) { assertFalse(it.visualController.snapshot.preview) }
        }
    }
}
