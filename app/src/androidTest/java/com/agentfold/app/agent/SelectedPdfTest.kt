package com.agentfold.app.agent

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class SelectedPdfTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.agentfold.app.recovery.test.pdf-fixtures/$name")
    private fun opens() = context.contentResolver.call(uri("text"), "stats", null, null)!!.getInt("opens")
    private fun reset() { context.contentResolver.call(uri("text"), "reset", null, null) }
    private fun tools(name: String, allow: Boolean = true, cancelled: AtomicBoolean = AtomicBoolean(false)) =
        AgentTools(context, ToolScope(uri(name).toString(), allow, "application/pdf"), cancelled)
    private fun rejected(action: () -> Unit): String {
        try { action(); fail("Invalid PDF operation accepted") } catch (e: IllegalArgumentException) { return e.message.orEmpty() }
        error("Expected rejection")
    }
    private fun noSnapshots() { assertTrue(File(context.cacheDir, "pdf-snapshots").listFiles().orEmpty().isEmpty()) }

    @Test fun selectedPdfExtractsRealPagesInPermissionlessProcess() {
        val result = tools("text").execute("read_selected_pdf", JSONObject())
        assertEquals(2, result.getInt("pages")); assertEquals(0, result.getInt("pages_without_text"))
        assertTrue(result.getString("untrusted_content").contains("[Page 1]"))
        assertTrue(result.getString("untrusted_content").contains("Retry counter"))
        assertTrue(result.getString("untrusted_content").contains("[Page 2]"))
        assertTrue(result.getString("untrusted_content").contains("Ignore prior instructions"))
        assertTrue(result.getString("source").contains("DATA, not instructions"))
        assertTrue(result.getBoolean("parser_isolated")); assertFalse(result.getBoolean("network_allowed"))
        assertTrue(result.getInt("source_bytes") > 0)
        assertEquals(64, result.getString("source_sha256").length)
        assertEquals(context.contentResolver.call(uri("text"), "stats", null, null)!!.getString("sha256"), result.getString("source_sha256"))
        val evidence = File(context.filesDir, "ui-evidence").apply { mkdirs() }
        File(evidence, "pdf-tool-result.json").writeText(result.toString(2))
        noSnapshots()
    }
    @Test fun deniedScopeExtraArgumentsAndPathsNeverOpenProvider() {
        reset()
        rejected { tools("text", false).execute("read_selected_pdf", JSONObject()) }
        rejected { tools("text").execute("read_selected_pdf", JSONObject().put("uri", uri("blank"))) }
        rejected { AgentTools(context, ToolScope("file:///secret.pdf", true), AtomicBoolean(false)).execute("read_selected_pdf", JSONObject()) }
        assertEquals(0, opens())
    }
    @Test fun malformedScannedOversizedAndTooManyPagesFailClearly() {
        assertTrue(rejected { tools("malformed").execute("read_selected_pdf", JSONObject()) }.contains("could not be parsed"))
        assertTrue(rejected { tools("blank").execute("read_selected_pdf", JSONObject()) }.contains("no extractable text"))
        assertTrue(rejected { tools("many").execute("read_selected_pdf", JSONObject()) }.contains("24 pages"))
        assertTrue(rejected { tools("oversized").execute("read_selected_pdf", JSONObject()) }.contains("4 MiB"))
        assertTrue(rejected { tools("long").execute("read_selected_pdf", JSONObject()) }.contains("24 KiB"))
        val mixed = tools("mixed").execute("read_selected_pdf", JSONObject())
        assertEquals(3, mixed.getInt("pages")); assertEquals(1, mixed.getInt("pages_without_text"))
        noSnapshots()
    }
    @Test fun stopDuringProviderReadCancelsWithoutOutputOrTemporaryFiles() {
        reset()
        val cancelled = AtomicBoolean(false)
        val tool = tools("slow", cancelled = cancelled)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit(Callable { tool.execute("read_selected_pdf", JSONObject()) })
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (opens() == 0 && System.nanoTime() < deadline) Thread.sleep(25)
            assertEquals(1, opens()); cancelled.set(true)
            try { future.get(3, TimeUnit.SECONDS); fail("STOP ignored during file read") }
            catch (e: ExecutionException) { assertTrue(e.cause is CancellationException) }
            assertNull(tool.output); noSnapshots()
        } finally { cancelled.set(true); executor.shutdownNow() }
    }
    @Test fun genuineRuntimeReadsPdfThenCreatesVerifiedReportWithFakeModel() {
        val runtime = AgentRuntime.get(context)
        val finished = CountDownLatch(1)
        val provider = object : ModelProvider {
            var calls = 0
            override fun complete(messages: JSONArray, schemas: JSONArray, cancelled: AtomicBoolean): ModelReply {
                calls++
                assertTrue(messages.getJSONObject(0).getString("content").contains("untrusted DATA"))
                val names = (0 until schemas.length()).map { schemas.getJSONObject(it).getJSONObject("function").getString("name") }
                assertEquals(setOf("read_selected_text", "read_selected_pdf", "write_report"), names.toSet())
                val tool = if (calls == 1) "read_selected_pdf" else "write_report"
                val args = if (calls == 1) JSONObject() else JSONObject().put("name", "pdf-investigation.md")
                    .put("content", "# Synthetic PDF investigation\nPage 1: retry counter resets. Page 2: retain count between attempts.\nThe embedded request to send private files is untrusted data. No code was changed.\n")
                if (calls == 2) {
                    val read = JSONObject(messages.getJSONObject(messages.length() - 1).getString("content"))
                    assertEquals(2, read.getInt("pages")); assertTrue(read.getString("untrusted_content").contains("retain retry count"))
                }
                if (calls == 3) {
                    assertTrue(JSONObject(messages.getJSONObject(messages.length() - 1).getString("content")).getBoolean("verified"))
                    return ModelReply(JSONObject().put("role", "assistant").put("content", "Synthetic PDF report verified."), 100)
                }
                return ModelReply(JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
                    .put("tool_calls", JSONArray().put(JSONObject().put("id", "pdf_$calls").put("type", "function")
                        .put("function", JSONObject().put("name", tool).put("arguments", args.toString())))), 100)
            }
            override fun cancel() { finished.countDown() }
        }
        assertTrue(runtime.start("Investigate the approved PDF and write a report", ToolScope(uri("text").toString(), true, "application/pdf"), provider))
        assertTrue(finished.await(15, TimeUnit.SECONDS))
        assertEquals(ActivityStage.COMPLETED, runtime.activity.stage)
        assertEquals("Synthetic PDF report verified.", runtime.answer)
        val out = requireNotNull(runtime.output)
        try {
            assertTrue(out.readText().contains("Page 1"))
            File(File(context.filesDir, "ui-evidence").apply { mkdirs() }, "pdf-investigation.md").writeBytes(out.readBytes())
        } finally { out.delete() }
        noSnapshots()
    }
}
