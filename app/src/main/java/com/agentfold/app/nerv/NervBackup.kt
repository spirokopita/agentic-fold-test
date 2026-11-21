package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NervBackup(
    context: Context,
    private val chatStore: ChatStore,
    private val noteStore: NoteStore,
    private val packManager: PackManager,
    private val taughtSkillStore: TaughtSkillStore,
    private val displaySettings: DisplaySettings,
    private val magiEngine: MagiEngine,
    private val routineStore: RoutineStore,
    private val timerStore: TimerStore,
    private val chatFolderStore: ChatFolderStore,

    private val batterySaver: BatterySaver,

    private val wakeWord: WakeWord? = null
) {

    data class Summary(
        val chats: Int,
        val notes: Int,
        val customPacks: Int,
        val taughtSkills: Int,
        val routines: Int,
        val timers: Int
    )

    data class Outcome(val ok: Boolean, val error: String?, val summary: Summary?)

    companion object {
        const val FORMAT_VERSION = 1
        private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024

        private val FORBIDDEN_KEYS = listOf("key", "keys", "apikey", "api_key", "pin", "pinhash", "pin_hash", "password", "secret", "salt")
    }

    val backupsDir: File = File(context.filesDir, "backups").apply { mkdirs() }

    val syncDir: File = File(context.filesDir, "sync").apply { mkdirs() }

    fun summaryNow(): Summary = Summary(
        chats = chatStore.entries().size,
        notes = noteStore.all().size,
        customPacks = packManager.customPacks.size,
        taughtSkills = taughtSkillStore.all().size,
        routines = routineStore.all().size,
        timers = timerStore.all().size
    )

    fun listBackups(): List<File> {
        val files = backupsDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".json") && insideBackups(it) }
            .sortedByDescending { it.lastModified() }
    }

    fun readBackup(file: File): String? {
        if (!insideBackups(file) || !file.isFile || file.length() > 512 * 1024) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteBackup(file: File): Boolean {
        if (!insideBackups(file) || !file.isFile) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun export(): File? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val baseName = "nerv-backup-$stamp.json"
        var file = File(backupsDir, baseName)
        var n = 2
        while (file.exists()) {
            file = File(backupsDir, "nerv-backup-$stamp-$n.json")
            n++
        }
        return try {
            file.writeText(buildJson().toString(), Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun exportSync(): File? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val baseName = "nerv-sync-$stamp.json"
        var file = File(syncDir, baseName)
        var n = 2
        while (file.exists()) {
            file = File(syncDir, "nerv-sync-$stamp-$n.json")
            n++
        }
        return try {
            file.writeText(buildJson().toString(), Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun listSyncFiles(): List<File> {
        val files = syncDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".json") && insideSync(it) }
            .sortedByDescending { it.lastModified() }
    }

    fun deleteSyncFile(file: File): Boolean {
        if (!insideSync(file) || !file.isFile) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun readSyncFile(file: File): String? {
        if (!insideSync(file) || !file.isFile || file.length() > 512 * 1024) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun validateSync(file: File): Outcome {
        val parsed = parse(file) ?: return Outcome(
            false,
            "That file is not a NERV sync file — it does not parse, is too big, or is not in the backups or sync folder.",
            null
        )
        return Outcome(true, null, parsed.summary)
    }

    fun merge(file: File): Outcome {
        val parsed = parse(file) ?: return Outcome(
            false,
            "That file is not a NERV sync file — it does not parse, is too big, or is not in the backups or sync folder.",
            null
        )
        return try {
            applySettings(parsed.settingsJson)
            applyNodes(parsed.nodesJson)
            mergeChats(parsed.chats)
            parsed.chatFolders.forEach { chatFolderStore.addFolder(it) }
            mergeNotes(parsed.notes, parsed.noteFolders)
            mergeCustomPacks(parsed.customPacks, parsed.enabledPacks)
            mergeTaughtSkills(parsed.taughtSkills)
            mergeRoutines(parsed.routines)
            mergeTimers(parsed.timers)
            Outcome(true, null, summaryNow())
        } catch (_: Exception) {
            Outcome(false, "The merge stopped part-way — the file looked valid but could not be applied. Some of this phone's data may already be merged in; export a fresh sync file before merging again.", summaryNow())
        }
    }

    private fun mergeChats(incoming: List<ChatStore.Entry>) {
        val merged = linkedMapOf<String, ChatStore.Entry>()
        chatStore.entries().forEach { merged[chatKey(it)] = it }
        incoming.forEach { entry ->
            val key = chatKey(entry)
            val existing = merged[key]
            if (existing == null) {
                merged[key] = entry
            } else {

                existing.deleted = existing.deleted && entry.deleted
                existing.folder = entry.folder
            }
        }
        chatStore.restore(merged.values.toList())
    }

    private fun chatKey(e: ChatStore.Entry): String =
        "${e.role.name}\u0001${e.text}\u0001${e.time}\u0001${e.sender}"

    private fun mergeNotes(incoming: List<NoteStore.Note>, incomingFolders: List<String>) {
        val merged = linkedMapOf<String, NoteStore.Note>()
        noteStore.all().forEach { merged[it.id] = it }
        incoming.forEach { note ->
            val existing = merged[note.id]
            if (existing == null || noteTime(note.updatedAt) > noteTime(existing.updatedAt)) {
                merged[note.id] = note
            }
        }
        val folders = (noteStore.allFolders() + incomingFolders).distinct()
        noteStore.restore(merged.values.toList(), folders)
    }

    private fun noteTime(stamp: String): Long {
        return try {
            SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).parse(stamp)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun mergeCustomPacks(incoming: List<PackManager.Pack>, incomingEnabled: Map<String, Boolean>) {
        val merged = linkedMapOf<String, PackManager.Pack>()
        packManager.customPacks.forEach { merged[it.id] = it }
        incoming.forEach { merged[it.id] = it }
        packManager.restoreCustomPacks(merged.values.toList())

        val localEnabled = packManager.allPacks().associate { it.id to it.enabled }
        (localEnabled.keys + incomingEnabled.keys).forEach { id ->
            val enabled = incomingEnabled[id] ?: localEnabled[id] ?: return@forEach
            if (enabled) packManager.load(id) else packManager.unload(id)
        }
    }

    private fun mergeTaughtSkills(incoming: List<TaughtSkillStore.TaughtSkill>) {
        val merged = linkedMapOf<String, TaughtSkillStore.TaughtSkill>()
        taughtSkillStore.all().forEach { merged[it.id] = it }
        incoming.forEach { merged[it.id] = it }
        taughtSkillStore.restore(merged.values.toList())
    }

    private fun mergeRoutines(incoming: List<RoutineStore.Routine>) {
        val merged = linkedMapOf<String, RoutineStore.Routine>()
        routineStore.all().forEach { merged[it.id] = it }
        incoming.forEach { merged[it.id] = it }
        routineStore.restore(merged.values.toList())
    }

    private fun mergeTimers(incoming: List<TimerStore.TimerEntry>) {
        val merged = linkedMapOf<String, TimerStore.TimerEntry>()
        timerStore.all().forEach { merged[it.id] = it }
        incoming.forEach { merged[it.id] = it }
        timerStore.restore(merged.values.toList())
    }

    fun validate(file: File): Outcome {
        val parsed = parse(file) ?: return Outcome(
            false,
            "That file is not a NERV backup — it does not parse, is too big, or is not in the backups or sync folder.",
            null
        )
        return Outcome(true, null, parsed.summary)
    }

    fun restore(file: File): Outcome {
        val parsed = parse(file) ?: return Outcome(
            false,
            "That file is not a NERV backup — it does not parse, is too big, or is not in the backups or sync folder.",
            null
        )
        return try {
            applySettings(parsed.settingsJson)
            applyNodes(parsed.nodesJson)
            chatStore.restore(parsed.chats)
            parsed.chatFolders.forEach { chatFolderStore.addFolder(it) }
            noteStore.restore(parsed.notes, parsed.noteFolders)
            packManager.restoreCustomPacks(parsed.customPacks)
            parsed.enabledPacks.forEach { (id, enabled) ->
                if (enabled) packManager.load(id) else packManager.unload(id)
            }
            taughtSkillStore.restore(parsed.taughtSkills)
            routineStore.restore(parsed.routines)
            timerStore.restore(parsed.timers)
            Outcome(true, null, parsed.summary)
        } catch (_: Exception) {
            Outcome(false, "The restore stopped part-way — the file looked valid but could not be applied. Your current data may be partly replaced; export a fresh backup before restoring again.", parsed.summary)
        }
    }

    private fun buildJson(): JSONObject {
        val root = JSONObject()
            .put("app", "NERV")
            .put("format", FORMAT_VERSION)
            .put(
                "exportedAt",
                SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
            )

        val chats = JSONArray()
        chatStore.entries().forEach { e ->
            val o = JSONObject()
                .put("role", e.role.name)
                .put("text", e.text)
                .put("time", e.time)
                .put("sender", e.sender)
                .put("deleted", e.deleted)
                .put("folder", e.folder)
            val votes = JSONArray()
            e.votes.forEach { (name, yes) -> votes.put(JSONArray().put(name).put(yes)) }
            o.put("votes", votes)
            val log = JSONArray()
            e.log.forEach { log.put(it) }
            o.put("log", log)
            chats.put(o)
        }
        root.put("chats", chats)
        val chatFolders = JSONArray()
        chatFolderStore.allFolders().forEach { chatFolders.put(it) }
        root.put("chatFolders", chatFolders)

        val notes = JSONArray()
        noteStore.all().forEach { note ->
            notes.put(
                JSONObject()
                    .put("id", note.id)
                    .put("folder", note.folder)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
            )
        }
        root.put("notes", notes)
        val noteFolders = JSONArray()
        noteStore.allFolders().forEach { noteFolders.put(it) }
        root.put("noteFolders", noteFolders)

        val packs = JSONObject()
        val custom = JSONArray()
        packManager.customPacks.forEach { pack ->
            val tools = JSONArray()
            pack.tools.forEach { tools.put(it) }
            custom.put(
                JSONObject()
                    .put("id", pack.id)
                    .put("name", pack.name)
                    .put("desc", pack.description)
                    .put("tools", tools)
                    .put("enabled", pack.enabled)
            )
        }
        packs.put("custom", custom)
        val enabled = JSONObject()
        packManager.allPacks().forEach { enabled.put(it.id, it.enabled) }
        packs.put("enabled", enabled)
        root.put("packs", packs)

        val skills = JSONArray()
        taughtSkillStore.all().forEach { skill ->
            val tools = JSONArray()
            skill.tools.forEach { tools.put(it) }
            skills.put(
                JSONObject()
                    .put("id", skill.id)
                    .put("name", skill.name)
                    .put("instruction", skill.instruction)
                    .put("tools", tools)
                    .put("enabled", skill.enabled)
                    .put("taughtAt", skill.taughtAt)
            )
        }
        root.put("taughtSkills", skills)

        val routines = JSONArray()
        routineStore.all().forEach { r ->
            routines.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("time", r.time)
                    .put("prompt", r.prompt)
                    .put("days", r.days)
                    .put("enabled", r.enabled)
                    .put("approved", r.approved)
                    .put("lastRun", r.lastRunDate)
            )
        }
        root.put("routines", routines)

        val timers = JSONArray()
        timerStore.all().forEach { t ->
            timers.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("totalMillis", t.totalMillis)
                    .put("leftMillis", t.leftMillis)
                    .put("running", t.running)
                    .put("endAtMillis", t.endAtMillis)
                    .put("finished", t.finished)
                    .put("createdAt", t.createdAt)
            )
        }
        root.put("timers", timers)

        root.put(
            "settings",
            JSONObject()
                .put("palette", displaySettings.palette)
                .put("customAccent", displaySettings.customAccent)
                .put("scanlines", displaySettings.scanlines)
                .put("flicker", displaySettings.flicker)
                .put("sound", displaySettings.sound)
                .put("textSizeSp", displaySettings.textSizeSp)
                .put("highContrast", displaySettings.highContrast)
                .put("textBoost", displaySettings.textBoost)
                .put("reduceMotion", displaySettings.reduceMotion)

                .put("batterySaverManual", batterySaver.manual)
                .put("batterySaverAuto", batterySaver.autoEnabled)
                .put("batterySaverThreshold", batterySaver.autoThreshold)

                .put("wakeWordEnabled", wakeWord?.enabled ?: false)
        )

        val nodes = JSONArray()
        magiEngine.nodes.forEach { node ->
            nodes.put(
                JSONObject()
                    .put("id", node.id.name)
                    .put("provider", node.provider.name)
                    .put("model", node.model)
                    .put("endpoint", node.endpoint)
                    .put("enabled", node.enabled)
            )
        }
        root.put("nodes", nodes)
        return root
    }

    private class Parsed(
        val summary: Summary,
        val chats: List<ChatStore.Entry>,
        val chatFolders: List<String>,
        val notes: List<NoteStore.Note>,
        val noteFolders: List<String>,
        val customPacks: List<PackManager.Pack>,
        val enabledPacks: Map<String, Boolean>,
        val taughtSkills: List<TaughtSkillStore.TaughtSkill>,
        val routines: List<RoutineStore.Routine>,
        val timers: List<TimerStore.TimerEntry>,
        val settingsJson: JSONObject,
        val nodesJson: JSONArray
    )

    private fun parse(file: File): Parsed? {

        if (!insideKnownDir(file) || !file.isFile || file.length() > MAX_BACKUP_BYTES) return null
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }
        if (root.optString("app", "") != "NERV") return null
        if (root.optInt("format", 0) != FORMAT_VERSION) return null

        val topKeys = root.keys().asSequence().map { it.lowercase(Locale.getDefault()) }.toList()
        if (topKeys.any { it in FORBIDDEN_KEYS }) return null
        val chatsJson = root.optJSONArray("chats") ?: return null
        val chatFoldersJson = root.optJSONArray("chatFolders") ?: return null
        val notesJson = root.optJSONArray("notes") ?: return null
        val noteFoldersJson = root.optJSONArray("noteFolders") ?: return null
        val packsJson = root.optJSONObject("packs") ?: return null
        val customJson = packsJson.optJSONArray("custom") ?: return null
        val enabledJson = packsJson.optJSONObject("enabled") ?: return null
        val skillsJson = root.optJSONArray("taughtSkills") ?: return null
        val routinesJson = root.optJSONArray("routines") ?: return null
        val timersJson = root.optJSONArray("timers") ?: return null
        val settingsJson = root.optJSONObject("settings") ?: return null
        val nodesJson = root.optJSONArray("nodes") ?: return null

        val chats = mutableListOf<ChatStore.Entry>()
        for (i in 0 until chatsJson.length()) {
            val o = chatsJson.optJSONObject(i) ?: continue
            val role = runCatching { ChatStore.Role.valueOf(o.optString("role", "")) }.getOrNull() ?: continue
            val votes = mutableListOf<Pair<String, Boolean>>()
            o.optJSONArray("votes")?.let { v ->
                for (j in 0 until v.length()) {
                    val pair = v.optJSONArray(j) ?: continue
                    if (pair.length() >= 2) votes.add(pair.optString(0, "") to pair.optBoolean(1, false))
                }
            }
            val log = mutableListOf<String>()
            o.optJSONArray("log")?.let { l ->
                for (j in 0 until l.length()) log.add(l.optString(j, ""))
            }
            chats.add(
                ChatStore.Entry(
                    role = role,
                    text = o.optString("text", ""),
                    time = o.optString("time", ""),
                    sender = o.optString("sender", ""),
                    votes = votes,
                    log = log,
                    deleted = o.optBoolean("deleted", false),
                    folder = o.optString("folder", "GENERAL").trim().ifBlank { "GENERAL" }
                )
            )
        }
        val chatFolders = mutableListOf<String>()
        for (i in 0 until chatFoldersJson.length()) {
            val name = chatFoldersJson.optString(i, "").trim()
            if (name.isNotEmpty()) chatFolders.add(name)
        }
        val notes = mutableListOf<NoteStore.Note>()
        for (i in 0 until notesJson.length()) {
            val o = notesJson.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val body = o.optString("body", "").trim()
            if (id.isEmpty() || body.isEmpty()) continue
            notes.add(
                NoteStore.Note(
                    id = id,
                    folder = o.optString("folder", "GENERAL").trim().ifBlank { "GENERAL" },
                    title = o.optString("title", "").trim().ifBlank { NoteStore.titleFromBody(body) },
                    body = body,
                    createdAt = o.optString("createdAt", ""),
                    updatedAt = o.optString("updatedAt", "")
                )
            )
        }
        val noteFolders = mutableListOf<String>()
        for (i in 0 until noteFoldersJson.length()) {
            val name = noteFoldersJson.optString(i, "").trim()
            if (name.isNotEmpty()) noteFolders.add(name)
        }
        val knownTools = packManager.availableTools().toSet()
        val customPacks = mutableListOf<PackManager.Pack>()
        for (i in 0 until customJson.length()) {
            val o = customJson.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val name = o.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) continue
            val tools = mutableListOf<String>()
            o.optJSONArray("tools")?.let { t ->
                for (j in 0 until t.length()) {
                    val tool = t.optString(j, "").trim().lowercase(Locale.getDefault())
                    if (tool in knownTools && tool !in tools) tools.add(tool)
                }
            }
            if (tools.isEmpty()) continue
            customPacks.add(
                PackManager.Pack(
                    id = id,
                    name = name,
                    description = o.optString("desc", "").trim(),
                    tools = tools,
                    implemented = true,
                    enabled = o.optBoolean("enabled", true),
                    custom = true
                )
            )
        }
        val enabledPacks = mutableMapOf<String, Boolean>()
        enabledJson.keys().forEach { key ->
            enabledPacks[key] = enabledJson.optBoolean(key, false)
        }
        val taughtSkills = mutableListOf<TaughtSkillStore.TaughtSkill>()
        for (i in 0 until skillsJson.length()) {
            val o = skillsJson.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val name = o.optString("name", "").trim()
            val instruction = o.optString("instruction", "").trim()
            if (id.isEmpty() || name.isEmpty() || instruction.isEmpty()) continue
            val tools = mutableListOf<String>()
            o.optJSONArray("tools")?.let { t ->
                for (j in 0 until t.length()) {
                    val tool = t.optString(j, "").trim().lowercase(Locale.getDefault())
                    if (tool in knownTools && tool !in tools) tools.add(tool)
                }
            }
            taughtSkills.add(
                TaughtSkillStore.TaughtSkill(
                    id = id,
                    name = name,
                    instruction = instruction,
                    tools = tools,
                    enabled = o.optBoolean("enabled", true),
                    taughtAt = o.optString("taughtAt", "")
                )
            )
        }
        val routines = mutableListOf<RoutineStore.Routine>()
        for (i in 0 until routinesJson.length()) {
            val o = routinesJson.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val name = o.optString("name", "").trim()
            val time = o.optString("time", "").trim()
            val prompt = o.optString("prompt", "").trim()
            val days = o.optString("days", "DAILY").trim().uppercase(Locale.getDefault())
            if (id.isEmpty() || name.isEmpty() || !RoutineStore.validTime(time) ||
                prompt.isEmpty() || days !in RoutineStore.DAY_CHOICES
            ) continue
            routines.add(
                RoutineStore.Routine(
                    id = id,
                    name = name,
                    time = time,
                    prompt = prompt,
                    days = days,
                    enabled = o.optBoolean("enabled", true),
                    approved = o.optBoolean("approved", false),
                    lastRunDate = o.optString("lastRun", "")
                )
            )
        }
        val timers = mutableListOf<TimerStore.TimerEntry>()
        for (i in 0 until timersJson.length()) {
            val o = timersJson.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            if (id.isEmpty()) continue
            timers.add(
                TimerStore.TimerEntry(
                    id = id,
                    name = o.optString("name", "TIMER"),
                    totalMillis = o.optLong("totalMillis", 300_000L),
                    leftMillis = o.optLong("leftMillis", 0L),
                    running = o.optBoolean("running", false),
                    endAtMillis = o.optLong("endAtMillis", 0L),
                    finished = o.optBoolean("finished", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        val summary = Summary(
            chats = chats.size,
            notes = notes.size,
            customPacks = customPacks.size,
            taughtSkills = taughtSkills.size,
            routines = routines.size,
            timers = timers.size
        )
        return Parsed(
            summary, chats, chatFolders, notes, noteFolders,
            customPacks, enabledPacks, taughtSkills, routines, timers,
            settingsJson, nodesJson
        )
    }

    private fun applySettings(settings: JSONObject) {
        val palette = settings.optString("palette", displaySettings.palette)
        if (palette in DisplaySettings.PALETTES) displaySettings.palette = palette

        if (settings.has("customAccent")) displaySettings.customAccent = settings.optInt("customAccent", 0)
        displaySettings.scanlines = settings.optBoolean("scanlines", displaySettings.scanlines)
        displaySettings.flicker = settings.optBoolean("flicker", displaySettings.flicker)
        displaySettings.sound = settings.optBoolean("sound", displaySettings.sound)
        displaySettings.textSizeSp = settings.optInt("textSizeSp", displaySettings.textSizeSp)
        displaySettings.highContrast = settings.optBoolean("highContrast", displaySettings.highContrast)

        if (settings.has("textBoost")) displaySettings.textBoost = settings.optBoolean("textBoost", displaySettings.textBoost)
        displaySettings.reduceMotion = settings.optBoolean("reduceMotion", displaySettings.reduceMotion)

        if (settings.has("batterySaverManual")) batterySaver.manual = settings.optBoolean("batterySaverManual", batterySaver.manual)
        if (settings.has("batterySaverAuto")) batterySaver.autoEnabled = settings.optBoolean("batterySaverAuto", batterySaver.autoEnabled)
        if (settings.has("batterySaverThreshold")) batterySaver.autoThreshold = settings.optInt("batterySaverThreshold", batterySaver.autoThreshold)

        if (settings.has("wakeWordEnabled") && wakeWord != null) wakeWord.enabled = settings.optBoolean("wakeWordEnabled", wakeWord.enabled)
    }

    private fun applyNodes(nodesJson: JSONArray) {
        for (i in 0 until nodesJson.length()) {
            val o = nodesJson.optJSONObject(i) ?: continue
            val id = runCatching { MagiEngine.NodeId.valueOf(o.optString("id", "")) }.getOrNull() ?: continue
            val node = magiEngine.nodes.firstOrNull { it.id == id } ?: continue
            val provider = runCatching { MagiEngine.ProviderKind.valueOf(o.optString("provider", "")) }.getOrNull()
            if (provider != null) node.provider = provider
            node.model = o.optString("model", node.model).trim()
            val endpoint = o.optString("endpoint", node.endpoint).trim()
            if (endpoint.isNotEmpty()) node.endpoint = endpoint
            node.enabled = o.optBoolean("enabled", node.enabled)
            magiEngine.saveNode(node)
        }
    }

    private fun insideBackups(file: File): Boolean {
        return try {
            val dir = backupsDir.canonicalPath
            val path = file.canonicalPath
            path == dir || path.startsWith(dir + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    private fun insideSync(file: File): Boolean {
        return try {
            val dir = syncDir.canonicalPath
            val path = file.canonicalPath
            path == dir || path.startsWith(dir + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    private fun insideKnownDir(file: File): Boolean = insideBackups(file) || insideSync(file)
}
