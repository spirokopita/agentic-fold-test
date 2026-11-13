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

    @Test fun everyStatePairKeepsVisibleGeometryDuringTransition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (from in VisualState.entries) for (to in VisualState.entries) {
                if (from == to) continue
                onMain(scenario) { it.visualController.preview(from) }
                Thread.sleep(1050)
                onMain(scenario) { it.visualController.preview(to) }
                Thread.sleep(220)
                onMain(scenario) { activity ->
                    val view = requireNotNull(find(activity.window.decorView))
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    var visibleSamples = 0
                    for (y in 0 until bitmap.height step 24) for (x in 0 until bitmap.width step 24) {
                        if (bitmap.getPixel(x, y) != Color.BLACK) visibleSamples++
                    }
                    assertTrue("blank transition ${from.name} -> ${to.name}", visibleSamples > 20)
                    assertFalse(view.contentDescription.toString().contains("unavailable"))
                    if (to.ordinal == (from.ordinal + 1) % VisualState.entries.size) {
                        val output = File(activity.getExternalFilesDir(null), "neural-transitions").apply { mkdirs() }
                        File(output, "${from.name.lowercase()}_to_${to.name.lowercase()}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    bitmap.recycle()
                }
            }
            for (state in VisualState.entries) {
                onMain(scenario) { it.visualController.preview(state) }
                Thread.sleep(70)
            }
            Thread.sleep(1100)
            onMain(scenario) { activity ->
                assertEquals(VisualState.PEER_TAKEN_OVER, activity.visualController.snapshot.state)
                assertTrue(requireNotNull(find(activity.window.decorView)).contentDescription.toString()
                    .contains(VisualState.PEER_TAKEN_OVER.label))
            }
        }
    }

    @Test fun exerciseAllStatesWithoutCapture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (state in VisualState.entries) {
                onMain(scenario) { it.visualController.preview(state) }
                Thread.sleep(1300)
                onMain(scenario) { activity ->
                    val view = requireNotNull(find(activity.window.decorView))
                    assertTrue(view.width > 0 && view.height > 0)
                    assertFalse(view.contentDescription.toString().contains("unavailable"))
                    assertTrue(view.contentDescription.toString().contains("simulation"))
                }
            }
            onMain(scenario) { it.visualController.endPreview() }
        }
    }
}
