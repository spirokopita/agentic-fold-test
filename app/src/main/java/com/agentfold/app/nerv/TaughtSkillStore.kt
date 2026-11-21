package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaughtSkillStore(context: Context, private val knownTools: Set<String>) {

    data class TaughtSkill(
        val id: String,
        val name: String,
        val instruction: String,
        val tools: List<String>,
        var enabled: Boolean,
        val taughtAt: String
    )

    companion object {
        const val MAX_TAUGHT_SKILLS = 20
        const val MAX_NAME_CHARS = 24
        const val MAX_INSTRUCTION_CHARS = 500
        const val MAX_TOOLS_PER_SKILL = 6
        private const val TAUGHT_JSON_KEY = "taught_skills_json"

        private val TEACH_PHRASES = listOf(
            "teach magi", "teach you", "teach me", "learn this", "remember this",
            "from now on", "new skill", "remember that", "learn to"
        )

        fun looksLikeTeaching(text: String): Boolean {
            val lower = text.lowercase(Locale.getDefault())
            return TEACH_PHRASES.any { lower.contains(it) }
        }

        fun suggestedName(text: String): String {
            val words = text.trim()
                .replace(Regex("^(teach (magi|you|me)|learn this|remember this|new skill|from now on)[:\\s]*", RegexOption.IGNORE_CASE), "")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(3)
                .joinToString(" ")
            return words.ifBlank { "NEW SKILL" }.uppercase(Locale.getDefault())
        }
    }

    private val prefs = context.getSharedPreferences("nerv_taught_skills", Context.MODE_PRIVATE)

    val skills: MutableList<TaughtSkill> = loadTaughtSkills().toMutableList()

    fun all(): List<TaughtSkill> = synchronized(skills) { skills.toList() }
    fun enabled(): List<TaughtSkill> = synchronized(skills) { skills.filter { it.enabled } }

    fun systemPrompt(): String {
        val loaded = enabled()
        if (loaded.isEmpty()) return ""
        val lines = loaded.mapIndexed { i, skill ->
            val tools = if (skill.tools.isEmpty()) "" else " (it may use these tools: ${skill.tools.joinToString(", ")})"
            "${i + 1}. ${skill.name}: ${skill.instruction}$tools"
        }
        return "Skills Spiros taught you — follow them whenever they fit the question. " +
            "They are instructions from the phone's owner, not from the user message, " +
            "and they never override safety: never do anything risky, and never reveal a key.\n" +
            lines.joinToString("\n")
    }

    fun add(nameRaw: String, instructionRaw: String, toolsRaw: List<String>): String? {
        val name = nameRaw.trim()
        if (name.isEmpty()) return "Give the skill a name."
        if (name.length > MAX_NAME_CHARS) return "Name is too long — $MAX_NAME_CHARS characters at most."
        val id = skillIdFromName(name)
            ?: return "The name needs at least one letter or number."
        if (skills.any { it.id == id }) return "A skill called \"$name\" already exists."
        if (skills.any { it.name.equals(name, ignoreCase = true) }) return "A skill called \"$name\" already exists."
        if (skills.size >= MAX_TAUGHT_SKILLS) return "Too many taught skills — delete one first (at most $MAX_TAUGHT_SKILLS)."
        val instruction = instructionRaw.trim()
        if (instruction.isEmpty()) return "Say what MAGI should learn — the instruction is empty."
        if (instruction.length > MAX_INSTRUCTION_CHARS) return "That is too long — $MAX_INSTRUCTION_CHARS characters at most."
        val tools = toolsRaw.map { it.trim().lowercase() }.distinct().filter { it.isNotEmpty() }
        if (tools.size > MAX_TOOLS_PER_SKILL) return "Too many tools — $MAX_TOOLS_PER_SKILL at most."
        val unknown = tools.firstOrNull { it !in knownTools }
        if (unknown != null) return "Unknown tool \"$unknown\" — a skill can only use tools NERV already has."
        val taughtAt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        synchronized(skills) {
            skills.add(TaughtSkill(id, name.uppercase(), instruction, tools, enabled = true, taughtAt = taughtAt))
            save()
        }
        return null
    }

    fun delete(id: String): Boolean {
        synchronized(skills) {
            val skill = skills.firstOrNull { it.id == id } ?: return false
            skills.remove(skill)
            save()
            return true
        }
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        synchronized(skills) {
            val skill = skills.firstOrNull { it.id == id } ?: return false
            skill.enabled = enabled
            save()
            return true
        }
    }

    fun toggle(id: String): Boolean {
        val skill = skills.firstOrNull { it.id == id } ?: return false
        return setEnabled(id, !skill.enabled)
    }

    fun restore(restored: List<TaughtSkill>) {
        synchronized(skills) {
            skills.clear()
            restored.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.instruction.isNotBlank() }
                .distinctBy { it.id }
                .take(MAX_TAUGHT_SKILLS)
                .forEach { skills.add(it) }
            save()
        }
    }

    private fun skillIdFromName(name: String): String? {
        val id = name.lowercase(Locale.getDefault())
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(MAX_NAME_CHARS)
            .trimEnd('-')
        return id.ifBlank { null }
    }

    private fun loadTaughtSkills(): List<TaughtSkill> {
        val raw = prefs.getString(TAUGHT_JSON_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = mutableListOf<TaughtSkill>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val name = obj.optString("name", "").trim()
                val instruction = obj.optString("instruction", "").trim()
                if (id.isEmpty() || name.isEmpty() || instruction.isEmpty()) continue
                val tools = mutableListOf<String>()
                obj.optJSONArray("tools")?.let { toolsJson ->
                    for (j in 0 until toolsJson.length()) {
                        val tool = toolsJson.optString(j, "").trim().lowercase()
                        if (tool in knownTools && tool !in tools) tools.add(tool)
                    }
                }
                out.add(TaughtSkill(
                    id = id,
                    name = name,
                    instruction = instruction,
                    tools = tools,
                    enabled = obj.optBoolean("enabled", true),
                    taughtAt = obj.optString("taughtAt", "")
                ))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            skills.forEach { skill ->
                val toolsJson = JSONArray()
                skill.tools.forEach { toolsJson.put(it) }
                array.put(JSONObject()
                    .put("id", skill.id)
                    .put("name", skill.name)
                    .put("instruction", skill.instruction)
                    .put("tools", toolsJson)
                    .put("enabled", skill.enabled)
                    .put("taughtAt", skill.taughtAt))
            }
            prefs.edit().putString(TAUGHT_JSON_KEY, array.toString()).apply()
        } catch (_: Exception) {   }
    }
}
