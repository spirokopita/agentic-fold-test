package com.agentfold.app.agent

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.os.Process
import org.json.JSONObject
import java.util.concurrent.Executors

/** Receives a read-only snapshot FD, never a URI, path, credential or model instruction. */
class PdfTextService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what != 1) return@Handler true
        val descriptor = if (Build.VERSION.SDK_INT >= 35)
            message.data.getParcelable("snapshot", ParcelFileDescriptor::class.java) else null
        val reply = message.replyTo
        if (descriptor == null || reply == null) { descriptor?.close(); return@Handler true }
        worker.execute {
            val result = try {
                if (Build.VERSION.SDK_INT < 35) throw DocumentReadException("PDF text extraction requires Android 15 or newer")
                val text = StringBuilder()
                var textBytes = 0
                var emptyPages = 0
                var pages = 0
                descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        pages = renderer.pageCount
                        if (pages !in 1..24) throw DocumentReadException("PDF supports 1–24 pages; choose a smaller document")
                        for (index in 0 until pages) {
                            renderer.openPage(index).use { page ->
                                val heading = "[Page ${index + 1}]\n"
                                textBytes += heading.toByteArray(Charsets.UTF_8).size
                                text.append(heading)
                                var hasText = false
                                for (block in page.textContents) {
                                    val content = block.text
                                    textBytes += content.toByteArray(Charsets.UTF_8).size + 1
                                    if (textBytes > 24 * 1024) throw DocumentReadException("PDF text exceeds 24 KiB; choose a shorter document")
                                    if (content.isNotBlank()) hasText = true
                                    text.append(content).append('\n')
                                }
                                if (!hasText) emptyPages++
                            }
                        }
                    }
                }
                if (emptyPages == pages) throw DocumentReadException("PDF has no extractable text; scanned pages need OCR, which is not available")
                JSONObject().put("untrusted_content", text.toString()).put("pages", pages)
                    .put("pages_without_text", emptyPages).put("text_bytes", textBytes)
                    .put("parser_isolated", Process.myUid() != applicationInfo.uid)
                    .put("network_allowed", checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
            } catch (e: DocumentReadException) {
                JSONObject().put("error", e.message)
            } catch (_: SecurityException) {
                JSONObject().put("error", "Encrypted or protected PDF is unsupported; choose an accessible copy")
            } catch (_: Exception) {
                JSONObject().put("error", "PDF could not be parsed; choose a valid text-based PDF")
            } finally { descriptor.close() }
            try { reply.send(Message.obtain().apply { what = 2; data = Bundle().apply { putString("result", result.toString()) } }) }
            catch (_: RemoteException) { /* Client cancelled or was destroyed; do not retain content. */ }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
        // This service runs only in its isolated process. End native parsing on unbind/STOP.
        Process.killProcess(Process.myPid())
    }
}

/** Only fixed operational errors are exposed to the model; never parser exception text or paths. */
class DocumentReadException(message: String) : IllegalArgumentException(message)
