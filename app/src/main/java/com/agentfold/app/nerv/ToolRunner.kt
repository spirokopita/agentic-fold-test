package com.agentfold.app.nerv

import android.content.Context
import android.os.BatteryManager
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale

class ToolRunner(
    private val context: Context,
    private val packManager: PackManager,
    private val noteStore: NoteStore,

    private val calendarLink: CalendarLink? = null,

    private val contactLookup: ContactLookup? = null
) {
    data class ToolCall(val time: String, val name: String, val args: String, val result: String, val ok: Boolean)

    private val journalFile = File(context.filesDir, "nerv-tool-journal.txt")
    private val journal = mutableListOf<ToolCall>()
    fun journal(): List<ToolCall> = journal.toList()

    fun toolSystemPrompt(): String {
        val available = listOf("calc", "time", "device", "notes", "fetch", "dice", "calendar", "contact")
            .filter { packManager.isToolEnabled(it) }
        return if (available.isEmpty()) "" else
            "You can use tools. To call one, reply with ONLY one line in this form: TOOL: name {json}. " +
            "You will get the result and can continue. Tools: " +
            "calc {\"expr\":\"2*(3+4)\"}; time {}; device {}; " +
            "notes {\"op\":\"save\",\"text\":\"...\"} or notes {\"op\":\"read\"} " +
            "(add \"folder\":\"MAGI\" to save or read one folder, \"search\":\"words\" to search notes); " +
            "fetch {\"url\":\"https://...\"}; dice {\"sides\":6}; " +
            "calendar {\"days\":2} (lists the next few days of the phone's calendar, read-only — " +
            "to add an event, end your answer with CALENDAR EVENT: <title> and the user approves it first); " +
            "contact {\"name\":\"...\"} (looks up a contact's number before a call or message — " +
            "use it before saying you will call or message someone; never repeat a number in your answer). " +
            "Use a tool only when it helps. Available now: ${available.joinToString(", ")}."
    }

    fun run(name: String, argsJson: String): String {
        val cleanName = name.lowercase().trim()

        val containing = packManager.packsContainingTool(cleanName)
        if (containing.isNotEmpty() && !packManager.isToolEnabled(cleanName)) {
            val which = containing.joinToString(", ") { "'${it.id}'" }
            val verb = if (containing.size == 1) "is" else "are"
            return log(cleanName, argsJson, "error: tool '$cleanName' is in $which, which $verb unloaded. Load one in SETUP first.", ok = false)
        }
        val result = try {
            val args = try { JSONObject(argsJson) } catch (_: Exception) { JSONObject() }
            when (cleanName) {
                "calc" -> runCalc(args.optString("expr", ""))
                "time" -> Date().toString()
                "device" -> runDevice()
                "notes" -> runNotes(args.optString("op", "read"), args.optString("text", ""), args.optString("folder", ""), args.optString("search", ""))
                "fetch" -> runFetch(args.optString("url", ""))
                "dice" -> runDice(args.optInt("sides", 6))
                "calendar" -> runCalendar(args.optInt("days", 2))
                "contact" -> runContact(args.optString("name", ""))
                "phone_observe", "phone_tap", "phone_scroll", "phone_type", "phone_back", "phone_home" ->
                    "Phone tools only run through AgentBridge after approval, inside a TAKE CONTROL session. " +
                    "Use AgentBridge.startApprovedTask() — this tool is never run directly."
                "read_selected_text", "read_selected_pdf", "write_report" ->
                    "File tools only run inside the bounded AgentRuntime loop. " +
                    "Use AgentBridge.startApprovedTask() — this tool is never run directly."
                else -> "error: unknown tool '$cleanName'"
            }
        } catch (e: Exception) {
            "error: ${e.message ?: "tool failed"}"
        }

        if (cleanName == "contact") {
            log(cleanName, argsJson, "(contact lookup — result not logged, privacy)", ok = !result.startsWith("error:"))
            return result
        }
        return log(cleanName, argsJson, result, ok = !result.startsWith("error:"))
    }

    private fun log(name: String, args: String, result: String, ok: Boolean): String {
        val entry = ToolCall(Date().toString(), name, args.take(200), result.take(300), ok)
        journal.add(entry)
        if (journal.size > 100) journal.removeAt(0)
        try {
            journalFile.appendText("${entry.time} | $name | ${entry.args} | ${if (ok) "OK" else "FAIL"} | ${entry.result}\n")
        } catch (_: Exception) {   }
        return result
    }

    private fun runCalc(exprRaw: String): String {
        val expr = exprRaw.replace("^", "**")
        require(expr.matches(Regex("^[\\d\\s+\\-*/%().,eE*]+$"))) { "bad expression" }
        require(expr.isNotBlank()) { "empty expression" }
        val value = CalcParser(expr).parse()
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    private class CalcParser(private val s: String) {
        private var pos = 0
        fun parse(): Double {
            val v = parseAdd()
            skipWs()
            require(pos == s.length) { "bad expression" }
            return v
        }
        private fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        private fun parseAdd(): Double {
            var v = parseMul()
            while (true) {
                skipWs()
                when {
                    pos < s.length && s[pos] == '+' -> { pos++; v += parseMul() }
                    pos < s.length && s[pos] == '-' -> { pos++; v -= parseMul() }
                    else -> return v
                }
            }
        }
        private fun parseMul(): Double {
            var v = parsePow()
            while (true) {
                skipWs()
                when {
                    pos < s.length && s[pos] == '*' && pos + 1 < s.length && s[pos + 1] == '*' -> { pos += 2; v = Math.pow(v, parsePow()) }
                    pos < s.length && s[pos] == '*' -> { pos++; v *= parsePow() }
                    pos < s.length && s[pos] == '/' -> { pos++; v /= parsePow() }
                    pos < s.length && s[pos] == '%' -> { pos++; v %= parsePow() }
                    else -> return v
                }
            }
        }
        private fun parsePow(): Double {
            skipWs()
            if (pos < s.length && s[pos] == '-') { pos++; return -parsePow() }
            return parseAtom()
        }
        private fun parseAtom(): Double {
            skipWs()
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = parseAdd()
                skipWs()
                require(pos < s.length && s[pos] == ')') { "missing bracket" }
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E')) pos++
            require(pos > start) { "bad expression" }
            return s.substring(start, pos).toDouble()
        }
    }

    private fun runDevice(): String {
        val battery = try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level >= 0) "$level%" else "n/a"
        } catch (_: Exception) { "n/a" }
        val dm = context.resources.displayMetrics
        return JSONObject()
            .put("battery", battery)
            .put("screen", "${dm.widthPixels}x${dm.heightPixels}")
            .put("lang", Locale.getDefault().language)
            .toString()
    }

    private fun runNotes(op: String, text: String, folder: String, search: String): String {
        return when (op.lowercase()) {
            "save" -> {
                require(text.isNotBlank()) { "nothing to save" }
                val error = noteStore.add(folder, "", text)
                if (error != null) "error: $error"
                else "saved${if (folder.isBlank()) "" else " in ${folder.trim().uppercase(Locale.getDefault())}"}"
            }
            else -> {
                val found = if (search.isNotBlank()) {
                    noteStore.search(search).mapNotNull { hit ->
                        noteStore.all().firstOrNull { it.id == hit.noteId }
                    }
                } else {
                    noteStore.notesIn(folder)
                }
                if (found.isEmpty()) "(empty)"
                else found.joinToString("\n---\n") { "[${it.folder}] ${it.title}: ${it.body}" }.take(4000)
            }
        }
    }

    private fun runFetch(urlRaw: String): String {
        val url = URL(urlRaw)
        require(url.protocol == "http" || url.protocol == "https") { "only http/https" }
        require(url.userInfo == null) { "URL cannot contain credentials" }
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val code = conn.responseCode
            require(code in 200..299) { "HTTP $code" }
            return conn.inputStream.bufferedReader().use { it.readText() }.take(1500)
        } finally {
            conn.disconnect()
        }
    }

    private fun runDice(sides: Int): String {
        val n = sides.coerceIn(2, 100)
        return (1 + (Math.random() * n).toInt()).toString()
    }

    private fun runCalendar(daysRaw: Int): String {
        val link = calendarLink ?: return "error: calendar is not wired up in this build."
        if (!link.hasReadPermission()) {
            return "error: calendar permission is off — the user needs to allow it in SETUP > CALENDAR first."
        }
        val days = daysRaw.coerceIn(1, 7)
        val now = System.currentTimeMillis()
        val events = link.upcoming(now, now + days * 24L * 60 * 60 * 1000)
        if (events.isEmpty()) return "(no events in the next $days day${if (days == 1) "" else "s"})"
        return events.joinToString("\n") { CalendarLink.lineFor(it) }.take(2000)
    }

    private fun runContact(nameRaw: String): String {
        val lookup = contactLookup ?: return "error: contacts are not wired up in this build."
        val name = nameRaw.trim()
        if (name.isEmpty()) return "error: give a name to look up, e.g. contact {\"name\":\"Maria\"}."
        if (!lookup.hasPermission()) {
            return "error: contacts permission is off — the user needs to allow it in SETUP > CONTACTS first."
        }
        val matches = lookup.lookup(name)
        if (matches.isEmpty()) return "(no contact matches \"$name\")"
        return matches.joinToString("\n") { ContactLookup.lineFor(it) }.take(1000)
    }
}
