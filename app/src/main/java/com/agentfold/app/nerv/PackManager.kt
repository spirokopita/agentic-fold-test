package com.agentfold.app.nerv

// MAGI EVOLVER — Stage 1 (self-save) + phyphox sensors + record/scan + cyberdeck, 25 Nov 2026.
// What changed in this file and why:
// - New built-in pack SENSORS: phyphox_list + phyphox_read + phyphox_record
//   + phyphox_scan. List the 40 bundled experiments, take one live reading
//   for one experiment, record the sensors once, and scan the recording.
//   Recordings stay on-device in filesDir and are deleted after the scan.
//   No new permission, enabled by default like the other read-only packs
//   (PORTS, FOLDERS).
// - New built-in pack CYBERDECK: white-hat network testing for Spiros's
//   OWN private network only — wifi_scan, lan_scan, port_scan,
//   wifi_password_check, router_check, wps_check. Every tool works only
//   on the WiFi network the phone is currently connected to; nothing
//   targets another network and nothing attempts a login. The password
//   check is offline only and never stores or sends the password.
//   Enabled by default, like ports/folders.
// Nothing else changed: same Pack data class, same load/unload rules,
// same 20 custom packs cap.

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PackManager(context: Context) {
    data class Pack(
        val id: String,
        val name: String,
        val description: String,
        val tools: List<String>,
        val implemented: Boolean,
        var enabled: Boolean,
        val custom: Boolean = false
    )

    companion object {
        const val MAX_CUSTOM_PACKS = 20
        const val MAX_TOOLS_PER_PACK = 12
        const val MAX_NAME_CHARS = 24
        const val MAX_DESC_CHARS = 120
        private const val CUSTOM_JSON_KEY = "custom_packs_json"
    }

    private val prefs = context.getSharedPreferences("nerv_packs", Context.MODE_PRIVATE)

    val packs: List<Pack> = listOf(
        Pack("core", "CORE", "calc, time, device, notes — local tools, no network.",
            listOf("calc", "time", "device", "notes"), implemented = true, enabled = true),
        Pack("nerv-tools", "NERV TOOLS", "fetch, dice — local tools, fetch uses the network.",
            listOf("fetch", "dice"), implemented = true, enabled = true),
        Pack("phone-control", "PHONE CONTROL", "phone_observe/tap/scroll/type/back/home — only via AgentBridge, TAKE CONTROL + approval per action.",
            listOf("phone_observe", "phone_tap", "phone_scroll", "phone_type", "phone_back", "phone_home"),
            implemented = true, enabled = false),
        Pack("files", "FILES", "read_selected_text, read_selected_pdf, write_report — only inside the bounded AgentRuntime loop.",
            listOf("read_selected_text", "read_selected_pdf", "write_report"),
            implemented = true, enabled = false),
        Pack("voice", "VOICE", "Voice chat — VoiceChatService start/stop + push-to-talk into MAGI CHAT. Needs mic permission and a provider configured.",
            listOf("voice_chat"), implemented = true, enabled = false),
        Pack("ports", "PORTS", "Test Ollama, provider and custom ports — TCP connect, no model call, no cost.",
            listOf("port_test", "port_add"), implemented = true, enabled = true),
        Pack("folders", "FOLDERS", "Browse and tidy the app's own files (reports, notes, chat). Never outside filesDir, delete needs confirm.",
            listOf("folder_list", "folder_create", "folder_rename", "folder_delete"), implemented = true, enabled = true),
        // EVOLVER: phyphox sensor lab. List the bundled experiments, take
        // one live reading for one experiment, record the sensors once and
        // scan the recording. Recordings stay on-device and are deleted
        // after the scan. No new permission — the sensors used need none.
        // Experiments that need the microphone, camera, GPS or Bluetooth
        // cannot be read by a tool call; the tool says so.
        Pack("sensors", "SENSORS", "phyphox experiments — list the 40 bundled experiments, read the phone's live sensor values for one, record the sensors once and scan the recording. Recordings stay on-device and are deleted after the scan; no new permission.",
            listOf("phyphox_list", "phyphox_read", "phyphox_record", "phyphox_scan"), implemented = true, enabled = true),
        // EVOLVER: CYBERDECK / QUICKHACKS — white-hat testing of Spiros's
        // OWN private network only. Every tool works only on the WiFi
        // network the phone is currently connected to; nothing targets
        // another network, nothing attempts a login, and the password
        // check is offline only — it never stores or sends the password.
        Pack("cyberdeck", "CYBERDECK", "White-hat network testing for your own private network only — scan nearby WiFi, list devices and open ports on the network you are connected to, check your router and your WiFi password strength. Never targets another network, never tries a login, never sends a password anywhere.",
            listOf("wifi_scan", "lan_scan", "port_scan", "wifi_password_check", "router_check", "wps_check"), implemented = true, enabled = true),
        Pack("calendar", "CALENDAR", "Read the phone's calendar (needs permission). Adding an event always needs Spiros's approval in a dialog — it is never a tool.",
            listOf("calendar"), implemented = true, enabled = true),
        Pack("contacts", "CONTACTS", "Look up a contact's number before a call or message (needs permission). Read-only; a lookup result is never saved or sent anywhere.",
            listOf("contact"), implemented = true, enabled = true)
    ).onEach { pack ->

        pack.enabled = pack.implemented && prefs.getBoolean("pack_${pack.id}", pack.enabled)
    }

    val customPacks: MutableList<Pack> = loadCustomPacks().toMutableList()

    fun allPacks(): List<Pack> = packs + customPacks

    fun availableTools(): List<String> = packs.flatMap { it.tools }.distinct()

    fun load(id: String): Boolean {
        val pack = allPacks().firstOrNull { it.id == id } ?: return false
        if (!pack.implemented) return false
        pack.enabled = true
        prefs.edit().putBoolean("pack_$id", true).apply()
        if (pack.custom) saveCustomPacks()
        return true
    }

    fun unload(id: String): Boolean {
        val pack = allPacks().firstOrNull { it.id == id } ?: return false
        pack.enabled = false
        prefs.edit().putBoolean("pack_$id", false).apply()
        if (pack.custom) saveCustomPacks()
        return true
    }

    fun toggle(id: String): Boolean {
        val pack = allPacks().firstOrNull { it.id == id } ?: return false
        return if (pack.enabled) unload(id) else load(id)
    }

    fun addCustomPack(nameRaw: String, descriptionRaw: String, toolsRaw: List<String>): String? {
        val name = nameRaw.trim()
        if (name.isEmpty()) return "Give the pack a name."
        if (name.length > MAX_NAME_CHARS) return "Name is too long — $MAX_NAME_CHARS characters at most."
        val id = packIdFromName(name)
            ?: return "The name needs at least one letter or number."
        if (allPacks().any { it.id == id }) return "A pack called \"$name\" already exists."
        if (allPacks().any { it.name.equals(name, ignoreCase = true) }) return "A pack called \"$name\" already exists."
        if (customPacks.size >= MAX_CUSTOM_PACKS) return "Too many custom packs — delete one first (at most $MAX_CUSTOM_PACKS)."
        val description = descriptionRaw.trim().take(MAX_DESC_CHARS)
        val known = availableTools().toSet()
        val tools = toolsRaw.map { it.trim().lowercase() }.distinct().filter { it.isNotEmpty() }
        if (tools.isEmpty()) return "Pick at least one tool for the pack."
        if (tools.size > MAX_TOOLS_PER_PACK) return "Too many tools — $MAX_TOOLS_PER_PACK at most."
        val unknown = tools.firstOrNull { it !in known }
        if (unknown != null) return "Unknown tool \"$unknown\" — a pack can only bundle tools NERV already has."
        val pack = Pack(id, name.uppercase(), description.ifBlank { "Custom pack — ${tools.joinToString(", ")}." },
            tools, implemented = true, enabled = true, custom = true)
        customPacks.add(pack)
        saveCustomPacks()
        prefs.edit().putBoolean("pack_$id", true).apply()
        return null
    }

    fun deleteCustomPack(id: String): Boolean {
        val pack = customPacks.firstOrNull { it.id == id } ?: return false
        customPacks.remove(pack)
        saveCustomPacks()
        prefs.edit().remove("pack_$id").apply()
        return true
    }

    fun restoreCustomPacks(restored: List<Pack>) {
        val known = availableTools().toSet()
        customPacks.clear()
        restored.filter { it.custom && it.id.isNotBlank() && it.name.isNotBlank() }
            .filter { pack -> packs.none { it.id == pack.id } }
            .map { pack ->
                pack.copy(
                    name = pack.name.take(MAX_NAME_CHARS),
                    description = pack.description.take(MAX_DESC_CHARS),
                    tools = pack.tools.map { it.trim().lowercase() }.distinct().filter { it in known }
                )
            }
            .filter { it.tools.isNotEmpty() }
            .distinctBy { it.id }
            .take(MAX_CUSTOM_PACKS)
            .forEach { customPacks.add(it) }
        saveCustomPacks()
        customPacks.forEach { prefs.edit().putBoolean("pack_${it.id}", it.enabled).apply() }
    }

    fun enabledPacks(): List<Pack> = allPacks().filter { it.enabled }
    fun enabledToolNames(): Set<String> = enabledPacks().flatMap { it.tools }.toSet()
    fun isToolEnabled(name: String): Boolean = enabledToolNames().contains(name)
    fun packForTool(name: String): Pack? = allPacks().firstOrNull { it.tools.contains(name) }
    fun packsContainingTool(name: String): List<Pack> = allPacks().filter { it.tools.contains(name) }

    private fun packIdFromName(name: String): String? {
        val id = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(MAX_NAME_CHARS)
            .trimEnd('-')
        return id.ifBlank { null }
    }

    private fun loadCustomPacks(): List<Pack> {
        val raw = prefs.getString(CUSTOM_JSON_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = mutableListOf<Pack>()
            val known = availableTools().toSet()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val name = obj.optString("name", "").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                if (packs.any { it.id == id }) continue
                val tools = mutableListOf<String>()
                val toolsJson = obj.optJSONArray("tools") ?: continue
                for (j in 0 until toolsJson.length()) {
                    val tool = toolsJson.optString(j, "").trim().lowercase()
                    if (tool in known && tool !in tools) tools.add(tool)
                }
                if (tools.isEmpty()) continue
                out.add(Pack(
                    id = id,
                    name = name,
                    description = obj.optString("desc", "").trim().take(MAX_DESC_CHARS),
                    tools = tools,
                    implemented = true,
                    enabled = obj.optBoolean("enabled", true),
                    custom = true
                ))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomPacks() {
        val array = JSONArray()
        customPacks.forEach { pack ->
            val toolsJson = JSONArray()
            pack.tools.forEach { toolsJson.put(it) }
            array.put(JSONObject()
                .put("id", pack.id)
                .put("name", pack.name)
                .put("desc", pack.description)
                .put("tools", toolsJson)
                .put("enabled", pack.enabled))
        }
        prefs.edit().putString(CUSTOM_JSON_KEY, array.toString()).apply()
    }
}
