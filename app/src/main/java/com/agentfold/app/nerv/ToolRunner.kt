package com.agentfold.app.nerv

// MAGI EVOLVER — Stage 1 (self-save) + phyphox sensors + record/scan + cyberdeck, 25 Nov 2026.
// What changed in this file and why:
// - toolSystemPrompt() now offers MAGI the tools from ALL packs, not just
//   the 8 hardcoded ones — ports, folders, phyphox, phone-control, files,
//   voice, sensors and cyberdeck included. Tools from a pack that is
//   currently unloaded are marked in the prompt ("pack X is unloaded —
//   ask Spiros to load it in SETUP first") so MAGI asks instead of
//   calling and failing. Offering a tool never authorises its execution:
//   phone-control tools still only run through AgentBridge with TAKE
//   CONTROL + approval per action, file tools stay inside the bounded
//   AgentRuntime loop, voice still needs mic permission and a provider,
//   folder_delete never runs from a tool call, and cyberdeck tools only
//   ever touch the connected WiFi network. The "load one in SETUP first"
//   error in run() stays as the fallback when a tool from an unloaded
//   pack is actually called.
// - The original 8 tools (calc, time, device, notes, fetch, dice,
//   calendar, contact) keep their exact format descriptions and behaviour.
// - New run() cases for the tools that existed as pack names but had no
//   implementation: port_test / port_add (via PortManager), folder_list /
//   folder_create / folder_rename (via FolderManager, sandboxed to the
//   app's filesDir), phyphox_list / phyphox_read (via PhyphoxSensor),
//   phyphox_record / phyphox_scan (via SensorRecorder / SensorScan —
//   record once, scan afterwards), wifi_scan / lan_scan / port_scan /
//   wifi_password_check / router_check / wps_check (via CyberdeckTools —
//   white-hat, connected network only, never a login).
//   folder_delete and voice_chat answer with guidance only, by design.
// - sensorSection(): the short sensor section MagiEngine appends to
//   MAGI's prompt. It lives here because only this class knows whether
//   the SENSORS pack is loaded.
// - Password safety: wifi_password_check takes Spiros's own password as
//   an argument. The argument is NEVER written to the tool journal —
//   every log() call for that tool uses a fixed placeholder instead.
//   The password also never appears in a result or a saved skill.

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

    private val contactLookup: ContactLookup? = null,

    // EVOLVER: the shared sensor hub, built by NervActivity and passed in
    // so there is exactly one hub in the app. When null (tests, old
    // callers) ToolRunner builds its own.
    private val sensorHub: SensorHub? = null
) {
    data class ToolCall(val time: String, val name: String, val args: String, val result: String, val ok: Boolean)

    private val journalFile = File(context.filesDir, "nerv-tool-journal.txt")
    private val journal = mutableListOf<ToolCall>()
    fun journal(): List<ToolCall> = journal.toList()

    // EVOLVER: the managers behind the tools that used to exist as pack
    // names only. No I/O happens in their constructors.
    private val portManager = PortManager(context)
    private val folderManager = FolderManager(context)
    // EVOLVER: one shared sensor hub for the phyphox tool, the recorder
    // and the scan — one listener per sensor, one read feeding every
    // consumer. NervActivity builds it and passes it in; the fallback
    // here only exists so ToolRunner still works standalone.
    private val hub: SensorHub = sensorHub ?: SensorHub(context)
    private val phyphoxSensor = PhyphoxSensor(context, hub)
    private val sensorRecorder = SensorRecorder(context, hub)
    private val sensorScan = SensorScan(context, phyphoxSensor)
    // EVOLVER: white-hat network testing — connected network only, never
    // a login, never sends a password anywhere.
    private val cyberdeckTools = CyberdeckTools(context)

    // EVOLVER: every tool MAGI can be offered, in prompt order, with the
    // exact text that describes it. The first 8 entries are byte-identical
    // to the old prompt — same format, same words, same order. Phone tools
    // share one entry and file tools share one, so the prompt does not
    // repeat the same "never call directly" warning six times.
    private data class ToolHelp(val tools: List<String>, val text: String)

    private val toolHelp: List<ToolHelp> = listOf(
        ToolHelp(listOf("calc"), "calc {\"expr\":\"2*(3+4)\"}"),
        ToolHelp(listOf("time"), "time {}"),
        ToolHelp(listOf("device"), "device {}"),
        ToolHelp(listOf("notes"), "notes {\"op\":\"save\",\"text\":\"...\"} or notes {\"op\":\"read\"} (add \"folder\":\"MAGI\" to save or read one folder, \"search\":\"words\" to search notes)"),
        ToolHelp(listOf("fetch"), "fetch {\"url\":\"https://...\"}"),
        ToolHelp(listOf("dice"), "dice {\"sides\":6}"),
        ToolHelp(listOf("calendar"), "calendar {\"days\":2} (lists the next few days of the phone's calendar, read-only — to add an event, end your answer with CALENDAR EVENT: <title> and the user approves it first)"),
        ToolHelp(listOf("contact"), "contact {\"name\":\"...\"} (looks up a contact's number before a call or message — use it before saying you will call or message someone; never repeat a number in your answer)"),
        ToolHelp(listOf("port_test"), "port_test {\"endpoint\":\"ollama\"} (tests whether a saved endpoint answers — TCP connect only, no model call, no cost; leave the endpoint out to test them all)"),
        ToolHelp(listOf("port_add"), "port_add {\"label\":\"...\",\"host\":\"...\",\"port\":11434,\"scheme\":\"http\"} (saves a new endpoint to test later)"),
        ToolHelp(listOf("folder_list"), "folder_list {\"path\":\"\"} (lists the app's own files — reports, notes, chat; path is a folder inside the app's files, empty means the top)"),
        ToolHelp(listOf("folder_create"), "folder_create {\"path\":\"\",\"name\":\"...\"} (creates a folder inside the app's files)"),
        ToolHelp(listOf("folder_rename"), "folder_rename {\"path\":\"...\",\"name\":\"...\"} (renames a file or folder inside the app's files)"),
        ToolHelp(listOf("folder_delete"), "folder_delete {\"path\":\"...\"} (deleting never runs from a tool call — say what to delete and let Spiros approve it first)"),
        ToolHelp(listOf("phyphox_list"), "phyphox_list {} (lists the 40 bundled phyphox experiments and which sensor each one uses)"),
        ToolHelp(listOf("phyphox_read"), "phyphox_read {\"experiment\":\"accelerometer\"} (one live reading from the phone's sensors for that experiment — a single snapshot right now, never a recording; if it says there is no data, say so and never invent a reading)"),
        ToolHelp(listOf("phyphox_record"), "phyphox_record {\"seconds\":10} (records all passive sensors + microphone levels once, for 10 seconds by default — the phone has to stay where the reading matters while it records; say phyphox_scan afterwards to run the mass scan)"),
        ToolHelp(listOf("phyphox_scan"), "phyphox_scan {} (replays the latest recording through every experiment that can be computed from recorded data and returns one mass-scan report; the recording is deleted after the scan; camera and output experiments are not covered in v1)"),
        ToolHelp(listOf("wifi_scan"), "wifi_scan {} (lists nearby WiFi networks — SSID, signal, channel, security — and flags WPS; needs location permission, which the app already asks for; observation only, it never connects to any network)"),
        ToolHelp(listOf("lan_scan"), "lan_scan {} (lists the devices on the WiFi network this phone is connected to — IP, MAC, vendor guess, open ports; only ever the connected network)"),
        ToolHelp(listOf("port_scan"), "port_scan {\"host\":\"192.168.1.5\"} (scans the common ports on one host on the connected network and reports what answers; refuses any host outside the connected network)"),
        ToolHelp(listOf("wifi_password_check"), "wifi_password_check {\"password\":\"...\"} (checks YOUR OWN WiFi password strength offline — length, character mix, common-password list; the password is never sent anywhere and never stored — never repeat it in your answer)"),
        ToolHelp(listOf("router_check"), "router_check {} (checks the connected network's gateway only — HTTP or HTTPS admin page, default ports; warns about default credentials; never tries to log in)"),
        ToolHelp(listOf("wps_check"), "wps_check {} (reports whether WPS is enabled on the connected network and why it is a risk; never brute-forces a PIN — the phone cannot do that without root and an external adapter)"),
        ToolHelp(
            listOf("phone_observe", "phone_tap", "phone_scroll", "phone_type", "phone_back", "phone_home"),
            "phone_observe / phone_tap / phone_scroll / phone_type / phone_back / phone_home (phone control only runs through AgentBridge after approval, inside a TAKE CONTROL session — never call these directly; propose a PHONE TASK instead)"
        ),
        ToolHelp(
            listOf("read_selected_text", "read_selected_pdf", "write_report"),
            "read_selected_text / read_selected_pdf / write_report (file tools only run inside the bounded AgentRuntime loop — never call these directly; propose a PHONE TASK instead)"
        ),
        ToolHelp(listOf("voice_chat"), "voice_chat (voice starts only from a tap on TALK — never from a tool call)")
    )

    fun toolSystemPrompt(): String {
        val parts = toolHelp.map { entry -> entry.text + unloadedNote(entry.tools) }
        val loaded = toolHelp.flatMap { it.tools }.filter { packManager.isToolEnabled(it) }
        val unloaded = toolHelp.flatMap { it.tools }.filter { !packManager.isToolEnabled(it) }
        val loadedLine = if (loaded.isEmpty()) {
            "No tools are loaded right now — ask Spiros to load a pack in SETUP first."
        } else {
            "Loaded now: ${loaded.joinToString(", ")}."
        }
        val unloadedLine = if (unloaded.isEmpty()) "" else {
            val named = unloaded.map { tool ->
                val pack = packManager.packsContainingTool(tool).firstOrNull()?.name ?: "?"
                "$tool ($pack)"
            }
            " Not loaded — ask Spiros to load the pack in SETUP first: ${named.joinToString(", ")}."
        }
        return "You can use tools. To call one, reply with ONLY one line in this form: TOOL: name {json}. " +
            "You will get the result and can continue. Tools: " +
            parts.joinToString("; ") + ". " +
            "Use a tool only when it helps. $loadedLine$unloadedLine"
    }

    // EVOLVER: the note appended to a tool's description when its pack is
    // unloaded, so MAGI asks Spiros to load it instead of calling it and
    // failing. When every tool in the entry is unloaded, the pack is
    // named; otherwise only the unloaded tools are named.
    private fun unloadedNote(tools: List<String>): String {
        val unloaded = tools.filter { !packManager.isToolEnabled(it) }
        if (unloaded.isEmpty()) return ""
        return if (unloaded.size == tools.size) {
            val packs = unloaded.flatMap { packManager.packsContainingTool(it) }.map { it.name }.distinct()
            " (not loaded now — pack ${packs.joinToString(" / ")} is unloaded; ask Spiros to load it in SETUP first)"
        } else {
            " (not loaded now: ${unloaded.joinToString(", ")} — ask Spiros to load the pack in SETUP first)"
        }
    }

    // EVOLVER: the short sensor section MagiEngine appends to MAGI's
    // prompt. It lives here because only this class knows whether the
    // SENSORS pack is loaded.
    fun sensorSection(): String {
        return if (packManager.isToolEnabled("phyphox_list")) {
            "\n\nSENSORS — this phone has a phyphox sensor lab (TOOLS > EXPERIMENTS, 40 experiments). " +
                "phyphox_list shows them; phyphox_read takes one live reading from the phone's sensors for one experiment. " +
                "A reading is a single snapshot right now, never a recording and never a past measurement. " +
                "If the tool says there is no data, or the phone has no such sensor, say so — never invent a reading."
        } else {
            "\n\nSENSORS — the phyphox sensor lab exists on this phone (TOOLS > EXPERIMENTS), but the SENSORS pack is " +
                "unloaded, so the phyphox tools are not available. Ask Spiros to load SENSORS in SETUP if a question needs a sensor reading."
        }
    }

    fun run(name: String, argsJson: String): String {
        val cleanName = name.lowercase().trim()
        // EVOLVER: wifi_password_check takes Spiros's own password as an
        // argument. The argument is NEVER written to the tool journal —
        // every log() call below uses this placeholder for that tool.
        val safeArgs = if (cleanName == "wifi_password_check") "(password check — password not logged)" else argsJson

        val containing = packManager.packsContainingTool(cleanName)
        if (containing.isNotEmpty() && !packManager.isToolEnabled(cleanName)) {
            val which = containing.joinToString(", ") { "'${it.id}'" }
            val verb = if (containing.size == 1) "is" else "are"
            return log(cleanName, safeArgs, "error: tool '$cleanName' is in $which, which $verb unloaded. Load one in SETUP first.", ok = false)
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
                // EVOLVER: tools that existed as pack names only now run
                // for real. port_test / port_add use PortManager (same TCP
                // check as SETUP > PORTS); folder_* use FolderManager,
                // sandboxed to the app's filesDir; phyphox_* use
                // PhyphoxSensor (read-only, one live snapshot).
                "port_test" -> runPortTest(args.optString("endpoint", ""))
                "port_add" -> runPortAdd(
                    args.optString("label", ""), args.optString("host", ""),
                    args.optInt("port", 0), args.optString("scheme", "http")
                )
                "folder_list" -> runFolderList(args.optString("path", ""))
                "folder_create" -> runFolderCreate(args.optString("path", ""), args.optString("name", ""))
                "folder_rename" -> runFolderRename(args.optString("path", ""), args.optString("name", ""))
                // EVOLVER: deleting never runs from a tool call — the pack
                // itself says "delete needs confirm". MAGI says what to
                // delete and Spiros approves it in the app.
                "folder_delete" -> "Deleting a file needs Spiros's approval — say which file and why, and he approves it in the app. Nothing was deleted."
                "phyphox_list" -> runPhyphoxList()
                "phyphox_read" -> phyphoxSensor.readLatest(args.optString("experiment", ""))
                // EVOLVER: record once, scan afterwards. phyphox_record
                // blocks for the recording (default 10 s); phyphox_scan
                // replays it and deletes the recording after.
                "phyphox_record" -> runPhyphoxRecord(args.optInt("seconds", 10))
                "phyphox_scan" -> runPhyphoxScan()
                // EVOLVER: CYBERDECK / QUICKHACKS — white-hat only, the
                // connected WiFi network only, never a login. The password
                // check is offline and its argument is never logged (see
                // safeArgs above).
                "wifi_scan" -> cyberdeckTools.wifiScan()
                "lan_scan" -> cyberdeckTools.lanScan()
                "port_scan" -> cyberdeckTools.portScan(args.optString("host", ""))
                "wifi_password_check" -> cyberdeckTools.wifiPasswordCheck(args.optString("password", ""))
                "router_check" -> cyberdeckTools.routerCheck()
                "wps_check" -> cyberdeckTools.wpsCheck()
                // EVOLVER: voice starts only from a tap on TALK — it never
                // starts from a tool call. Listening stays user-initiated.
                "voice_chat" -> "Voice starts only from a tap on TALK — it never starts from a tool call."
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
            log(cleanName, safeArgs, "(contact lookup — result not logged, privacy)", ok = !result.startsWith("error:"))
            return result
        }
        if (cleanName == "wifi_password_check") {
            // EVOLVER: the password never reaches the journal — neither
            // the arguments nor the result (the tool never echoes it).
            log(cleanName, safeArgs, "(password check — password not logged, privacy)", ok = !result.startsWith("error:"))
            return result
        }
        return log(cleanName, safeArgs, result, ok = !result.startsWith("error:"))
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

    // EVOLVER: port tools, via PortManager. Same TCP-connect check as
    // SETUP > PORTS — no model call, no cost, no data sent.
    private fun runPortTest(endpointRaw: String): String {
        val wanted = endpointRaw.trim()
        val ends = portManager.endpoints()
        val targets = if (wanted.isEmpty()) ends
            else ends.filter { it.id.equals(wanted, ignoreCase = true) || it.label.equals(wanted, ignoreCase = true) }
        if (targets.isEmpty()) {
            return "error: no saved endpoint called \"$wanted\" — known endpoints: ${ends.joinToString(", ") { it.label }}"
        }
        return targets.joinToString("\n") { end ->
            val check = portManager.test(end)
            "${end.label} (${end.host}:${end.port}): ${check.state} — ${check.detail}"
        }.take(2000)
    }

    private fun runPortAdd(label: String, host: String, port: Int, scheme: String): String {
        val error = portManager.addCustom(label, host, port, scheme)
        if (error != null) return "error: $error"
        return "saved endpoint \"$label\" ($host:$port)"
    }

    // EVOLVER: folder tools, via FolderManager. Every path is resolved
    // under the app's filesDir and checked with insideRoot() — nothing
    // outside the app's own files can be listed, created or renamed.
    private fun folderFile(pathRaw: String): File? {
        val clean = pathRaw.trim().trimStart('/').trimEnd('/')
        if (clean.contains("..")) return null
        val file = if (clean.isEmpty()) folderManager.root else File(folderManager.root, clean)
        return if (folderManager.insideRoot(file)) file else null
    }

    private fun runFolderList(pathRaw: String): String {
        val dir = folderFile(pathRaw) ?: return "error: that path is outside the app's files."
        val items = folderManager.list(dir)
        if (items.isEmpty()) return "(empty)"
        return items.joinToString("\n") { item ->
            "${if (item.isDir) "DIR" else "FILE"} ${item.name}" +
                if (item.isDir) "" else " — ${folderManager.formatSize(item.size)}"
        }.take(2000)
    }

    private fun runFolderCreate(pathRaw: String, name: String): String {
        val parent = folderFile(pathRaw) ?: return "error: that path is outside the app's files."
        val error = folderManager.createFolder(parent, name)
        if (error != null) return "error: $error"
        return "created folder \"$name\""
    }

    private fun runFolderRename(pathRaw: String, newName: String): String {
        val file = folderFile(pathRaw) ?: return "error: that path is outside the app's files."
        val error = folderManager.rename(file, newName)
        if (error != null) return "error: $error"
        return "renamed to \"$newName\""
    }

    // EVOLVER: phyphox experiment list, via PhyphoxSensor. One line per
    // experiment: id, title, category and the sensor it uses (or that it
    // uses no phone sensor).
    private fun runPhyphoxList(): String {
        val experiments = phyphoxSensor.listExperiments()
        if (experiments.isEmpty()) return "error: no phyphox experiments found on this phone."
        return experiments.joinToString("\n") { info ->
            val sensor = if (info.sensorTypes.isEmpty()) "no phone sensor — open in TOOLS > EXPERIMENTS"
                else info.sensorTypes.joinToString(", ")
            "${info.id} — ${info.title} (${info.category}; sensor: $sensor)"
        }.take(4000)
    }

    // EVOLVER: record once, then scan. phyphox_record blocks for the
    // recording (default 10 s, at most 60 s); phyphox_scan replays the
    // latest recording and deletes it after.
    private fun runPhyphoxRecord(secondsRaw: Int): String {
        val seconds = secondsRaw.coerceIn(1, 60)
        val result = sensorRecorder.record(seconds * 1000L)
        if (result.error != null) return result.error
        val recording = result.recording
            ?: return "error: the recording did not produce a file."
        return "Recorded ${seconds}s: ${recording.samples} samples " +
            "(${recording.sensors.joinToString(", ")}) saved to ${recording.file.name}. " +
            "Say phyphox_scan to run the mass scan on it."
    }

    private fun runPhyphoxScan(): String {
        val report = sensorScan.scanLatest()
            ?: return "error: no recording found — record one first with phyphox_record."
        return report.reportText
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
