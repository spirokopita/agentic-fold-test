package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PackStore(
    context: Context,
    private val packManager: PackManager
) {

    data class SharedPackFile(
        val file: File,
        val name: String,
        val description: String,
        val tools: List<String>,
        val readable: Boolean
    )

    data class ParsedPack(
        val name: String,
        val description: String,
        val tools: List<String>
    )

    companion object {
        const val MAX_FILE_BYTES = 8 * 1024L
    }

    val packsDir: File = File(context.filesDir, "packs").apply { mkdirs() }

    fun sharePack(pack: PackManager.Pack): File? {
        val safeId = pack.id.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "pack" }
        val file = File(packsDir, "nerv-pack-$safeId.json")
        if (!insidePacks(file)) return null
        val toolsJson = JSONArray()
        pack.tools.forEach { toolsJson.put(it) }
        val json = JSONObject()
            .put("app", "NERV")
            .put("format", "nerv-pack")
            .put("version", 1)
            .put("id", pack.id)
            .put("name", pack.name)
            .put("desc", pack.description)
            .put("tools", toolsJson)
            .put("enabled", pack.enabled)
        return try {
            file.writeText(json.toString(), Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun listSharedPacks(): List<SharedPackFile> {
        val files = packsDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".json") && insidePacks(it) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val parsed = readPackFile(file).first
                if (parsed != null) {
                    SharedPackFile(file, parsed.name, parsed.description, parsed.tools, readable = true)
                } else {
                    SharedPackFile(file, file.name, "Cannot read this file — it is not a NERV pack.", emptyList(), readable = false)
                }
            }
    }

    fun readPackFile(file: File): Pair<ParsedPack?, String?> {
        if (!insidePacks(file) || !file.isFile) return null to "That file is outside the app's pack folder."
        if (file.length() > MAX_FILE_BYTES) return null to "That file is too big for a pack (over 8 KB)."
        val raw = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null to "Could not read that file."
        }
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("app", "") != "NERV") return null to "Not a NERV pack file."
            if (obj.optString("format", "") != "nerv-pack") return null to "Not a NERV pack file."
            if (obj.optInt("version", -1) != 1) return null to "That pack was made for a different NERV version."
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) return null to "The pack has no name."
            if (name.length > PackManager.MAX_NAME_CHARS) {
                return null to "The pack name is too long — ${PackManager.MAX_NAME_CHARS} characters at most."
            }
            val description = obj.optString("desc", "").trim().take(PackManager.MAX_DESC_CHARS)
            val toolsJson = obj.optJSONArray("tools")
                ?: return null to "The pack lists no tools."
            val known = packManager.availableTools().toSet()
            val tools = mutableListOf<String>()
            for (i in 0 until toolsJson.length()) {
                val tool = toolsJson.optString(i, "").trim().lowercase()
                if (tool.isEmpty()) continue
                if (tool !in known) return null to "Unknown tool \"$tool\" — a pack can only bundle tools NERV already has."
                if (tool !in tools) tools.add(tool)
            }
            if (tools.isEmpty()) return null to "The pack lists no tools NERV has."
            if (tools.size > PackManager.MAX_TOOLS_PER_PACK) {
                return null to "Too many tools — ${PackManager.MAX_TOOLS_PER_PACK} at most."
            }
            ParsedPack(name, description, tools) to null
        } catch (_: Exception) {
            null to "That file is not a pack — the JSON in it does not parse."
        }
    }

    fun deleteSharedPack(file: File): Boolean {
        if (!insidePacks(file) || !file.isFile) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun insidePacks(file: File): Boolean {
        return try {
            val dir = packsDir.canonicalPath
            val path = file.canonicalPath
            path == dir || path.startsWith(dir + File.separator)
        } catch (_: Exception) {
            false
        }
    }
}
