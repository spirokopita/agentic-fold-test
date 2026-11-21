package com.agentfold.app.nerv

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportExporter(
    context: Context,
    private val chatStore: ChatStore,
    private val phoneTaskStore: PhoneTaskStore
) {

    enum class Kind(val label: String, val filePrefix: String) {
        CHAT("CHAT TRANSCRIPT", "nerv-chat"),
        DEBATE("MAGI DEBATE LOG", "nerv-debate"),
        TASKS("PHONE TASK REPORT", "nerv-tasks")
    }

    data class Report(val file: File, val name: String, val size: Long)

    companion object {
        private const val MAX_BYTES = 256 * 1024
        private const val MAX_TEXT_CHARS = 20000
    }

    val reportsDir: File = File(context.filesDir, "reports").apply { mkdirs() }

    fun listReports(): List<Report> {
        val files = reportsDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".txt") && insideReports(it) }
            .sortedByDescending { it.lastModified() }
            .map { Report(it, it.name, it.length()) }
    }

    fun countFor(kind: Kind): Int = when (kind) {
        Kind.CHAT -> chatStore.entries().count { !it.deleted }
        Kind.DEBATE -> chatStore.entries().count { !it.deleted && it.role == ChatStore.Role.AI && it.log.isNotEmpty() }
        Kind.TASKS -> phoneTaskStore.all().size
    }

    fun export(kind: Kind): Report? {
        if (countFor(kind) == 0) return null
        val text = when (kind) {
            Kind.CHAT -> buildChatText()
            Kind.DEBATE -> buildDebateText()
            Kind.TASKS -> buildTasksText()
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val baseName = "${kind.filePrefix}-$stamp.txt"
        val file = uniqueFile(baseName)
        return try {
            file.writeText(text, Charsets.UTF_8)
            Report(file, file.name, file.length())
        } catch (_: Exception) {
            null
        }
    }

    fun readReport(file: File): String? {
        if (!insideReports(file) || !file.isFile || file.length() > 65536) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteReport(file: File): Boolean {
        if (!insideReports(file) || !file.isFile) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun insideReports(file: File): Boolean {
        return try {
            val dir = reportsDir.canonicalPath
            val path = file.canonicalPath
            path == dir || path.startsWith(dir + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    private fun uniqueFile(baseName: String): File {
        var candidate = File(reportsDir, baseName)
        var n = 2
        while (candidate.exists()) {
            val stem = baseName.removeSuffix(".txt")
            candidate = File(reportsDir, "$stem-$n.txt")
            n++
        }
        return candidate
    }

    private fun header(kind: Kind, lines: Int): String {
        val now = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        return "NERV REPORT — ${kind.label}\n" +
            "Exported $now, on this phone only.\n" +
            "$lines item${if (lines == 1) "" else "s"} in this report.\n" +
            "NERV never sends a report anywhere itself — sharing is your tap.\n" +
            "=".repeat(48) + "\n\n"
    }

    private fun cap(text: String): String {
        if (text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) return text
        val cut = text.take(MAX_BYTES / 2)
        return cut + "\n\n[REPORT CUT HERE — over the 256 KB limit. Export again after clearing old chats or tasks.]\n"
    }

    private fun buildChatText(): String {
        val entries = chatStore.entries().filter { !it.deleted }
        val out = StringBuilder(header(Kind.CHAT, entries.size))
        entries.forEach { e ->
            val who = when (e.role) {
                ChatStore.Role.YOU -> "YOU"
                ChatStore.Role.AI -> if (e.sender.isNotBlank()) "MAGI (${e.sender})" else "MAGI"
                ChatStore.Role.SYSTEM -> if (e.sender.isNotBlank()) e.sender else "SYSTEM"
            }
            out.append("[${e.time}] $who:\n")
            out.append(e.text.take(MAX_TEXT_CHARS)).append("\n")
            if (e.votes.isNotEmpty()) {
                val votes = e.votes.joinToString(" · ") { (name, yes) -> "$name ${if (yes) "APPROVE" else "DENY"}" }
                out.append("Votes: $votes\n")
            }
            if (e.log.isNotEmpty()) {
                out.append("Deliberation:\n")
                e.log.forEach { line -> out.append("  $line\n") }
            }
            out.append("\n")
        }
        return cap(out.toString())
    }

    private fun buildDebateText(): String {
        val entries = chatStore.entries()
            .filter { !it.deleted && it.role == ChatStore.Role.AI && it.log.isNotEmpty() }
        val out = StringBuilder(header(Kind.DEBATE, entries.size))
        entries.forEach { e ->
            val who = if (e.sender.isNotBlank()) e.sender else "MAGI"
            out.append("[${e.time}] Debate led by $who:\n")
            if (e.votes.isNotEmpty()) {
                val votes = e.votes.joinToString(" · ") { (name, yes) -> "$name ${if (yes) "APPROVE" else "DENY"}" }
                out.append("Votes: $votes\n")
            }
            e.log.forEach { line -> out.append("  $line\n") }
            out.append("Answer: ").append(e.text.take(MAX_TEXT_CHARS)).append("\n\n")
        }
        return cap(out.toString())
    }

    private fun buildTasksText(): String {
        val tasks = phoneTaskStore.all()
        val out = StringBuilder(header(Kind.TASKS, tasks.size))
        tasks.forEach { t ->
            out.append("[${t.createdAt}] ${t.status} — ${t.objective.take(MAX_TEXT_CHARS)}\n")
            if (t.question.isNotBlank()) out.append("MAGI asked: ${t.question.take(500)}\n")
            if (t.result.isNotBlank()) {
                val label = if (t.status == PhoneTaskStore.TaskStatus.FAILED) "Failed: " else "Result: "
                out.append(label).append(t.result.take(MAX_TEXT_CHARS)).append("\n")
            }
            out.append("\n")
        }
        return cap(out.toString())
    }
}
