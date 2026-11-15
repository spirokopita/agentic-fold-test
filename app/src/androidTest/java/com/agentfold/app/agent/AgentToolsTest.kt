package com.agentfold.app.agent

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AgentToolsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun tools(cancelled: AtomicBoolean = AtomicBoolean(false)) = AgentTools(context, ToolScope(null, false), cancelled)
    private fun rejects(action: () -> Unit) { try { action(); fail("Denied tool accepted") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }

    @Test fun maliciousToolNamesAndExtraArgumentsCannotEscapeRegistry() {
        rejects { tools().execute("send_email", JSONObject().put("recipient", "attacker")) }
        rejects { tools().execute("read_selected_text", JSONObject().put("uri", "file:///secrets")) }
        rejects { tools().execute("read_selected_text", JSONObject()) }
    }
    @Test fun reportCannotTraverseOrOverwriteSource() {
        rejects { tools().execute("write_report", JSONObject().put("name", "../source.kt").put("content", "overwrite")) }
        rejects { tools().execute("write_report", JSONObject().put("name", "report.md").put("content", "x").put("path", "/secrets")) }
    }
    @Test fun outputIsReadBackAndHasRealHash() {
        var readbackObserved = false
        val tools = AgentTools(context, ToolScope(null, false), AtomicBoolean(false)) { readbackObserved = true }
        val result = tools.execute("write_report", JSONObject().put("name", "test-report.md").put("content", "Verified test report"))
        val file = tools.output!!
        try {
            assertTrue(result.getBoolean("verified")); assertEquals("Verified test report", file.readText())
            assertTrue("The live VERIFY phase must correspond to actual readback", readbackObserved)
            assertEquals(64, result.getString("sha256").length)
            rejects { tools.execute("write_report", JSONObject().put("name", "other.md").put("content", "duplicate")) }
        } finally { file.delete() }
    }
    @Test fun cancellationBlocksToolDispatch() {
        try {
            tools(AtomicBoolean(true)).execute("write_report", JSONObject().put("name", "cancelled.md").put("content", "no"))
            fail("STOP was ignored")
        } catch (_: java.util.concurrent.CancellationException) { }
    }
    @Test fun actualAgentLoopDispatchesToolThenReadsVerifiedResult() {
        val runtime = AgentRuntime.get(context)
        val finished = CountDownLatch(1)
        val provider = object : ModelProvider {
            var calls = 0
            override fun complete(messages: JSONArray, tools: JSONArray, cancelled: AtomicBoolean): ModelReply {
                assertEquals(ActivityStage.MODEL, runtime.activity.stage)
                calls++
                if (calls == 1) {
                    return ModelReply(JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
                        .put("tool_calls", JSONArray().put(JSONObject().put("id", "call_1").put("type", "function")
                            .put("function", JSONObject().put("name", "write_report")
                                .put("arguments", JSONObject().put("name", "runtime-test.md").put("content", "Real tool loop output").toString())))), 100)
                }
                assertTrue(JSONObject(messages.getJSONObject(messages.length() - 1).getString("content")).getBoolean("verified"))
                assertTrue(runtime.activity.verifiedReport)
                assertEquals(ToolOutcome.ACCEPTED, runtime.activity.toolOutcome)
                return ModelReply(JSONObject().put("role", "assistant").put("content", "Report verified."), 100)
            }
            override fun cancel() { finished.countDown() }
        }
        assertTrue(runtime.start("Write a test report", ToolScope(null, false), provider))
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        // cancel() is called in finally before running is cleared, so inspect result rather than racing that flag.
        assertEquals("Report verified.", runtime.answer)
        assertTrue(runtime.status.startsWith("Completed"))
        assertEquals(ActivityStage.COMPLETED, runtime.activity.stage)
        runtime.output?.delete()
    }
}
