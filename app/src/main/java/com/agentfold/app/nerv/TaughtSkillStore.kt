package com.agentfold.app.nerv

// MAGI EVOLVER — Stage 1 (self-save), 25 Nov 2026.
// What changed in this file and why:
// - TaughtSkill gains origin ("TAUGHT" or "AUTO"), useCount and lastUsedAt.
//   Old saved skills have none of these fields and load as TAUGHT / 0 / "".
// - addAuto(): saves a skill MAGI learned itself (origin AUTO), capped at
//   MAX_AUTO_SKILLS (10). When full, the oldest auto skill that has never
//   been used is pruned first; if every auto skill has been used, the save
//   is refused instead of deleting a skill Spiros's MAGI actually uses.
// - markUsed(): records that the skills in MAGI's prompt were part of a
//   run that succeeded. This is what the prune rule uses.
// - systemPrompt() marks auto skills "[AUTO]" so MAGI can tell a learned
//   procedure from a taught one. Nothing else about taught skills changed:
//   same 20-skill cap, same 500-char instructions, same 6 tools per skill.

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
        val taughtAt: String,
        // EVOLVER: where the skill came from. "TAUGHT" = Spiros taught it in
        // CHAT; "AUTO" = MAGI saved it itself after a run that worked.
        // Old saved skills have no origin and load as TAUGHT.
        val origin: String = ORIGIN_TAUGHT,
        // EVOLVER: how often the skill was in MAGI's prompt for a run that
        // succeeded, and when that last happened. Drives auto pruning.
        var useCount: Int = 0,
        var lastUsedAt: String = ""
    )

    companion object {
        const val MAX_TAUGHT_SKILLS = 20
        const val MAX_NAME_CHARS = 24
        const val MAX_INSTRUCTION_CHARS = 500
        const val MAX_TOOLS_PER_SKILL = 6
        // EVOLVER: auto-saved skills are capped separately from the 20 total,
        // so self-save can never push out a skill Spiros taught by hand.
        const val MAX_AUTO_SKILLS = 10
        const val ORIGIN_TAUGHT = "TAUGHT"
        const val ORIGIN_AUTO = "AUTO"
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
            // EVOLVER: auto-saved skills are marked so MAGI knows they are
            // procedures it learned from runs that worked, not instructions
            // Spiros typed. Taught skills keep the exact old format.
            val tag = if (skill.origin == ORIGIN_AUTO) " [AUTO]" else ""
            "${i + 1}. ${skill.name}$tag: ${skill.instruction}$tools"
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

    // EVOLVER: skills MAGI saved itself. Same limits as a taught skill
    // (name, instruction, tools) plus the auto cap below.
    fun autoSkills(): List<TaughtSkill> = synchronized(skills) { skills.filter { it.origin == ORIGIN_AUTO } }

    // EVOLVER: save a skill MAGI learned from a run that worked. Called only
    // by MagiEvolver after it has checked the run (live answer, debate
    // approved, every tool call ok, no phone-control tools, no secrets,
    // not a duplicate). Returns an error string, or null when saved.
    // No approval prompt happens here by design — Spiros approved fully
    // automatic self-save. The skill is visible in SETUP > TAUGHT SKILLS
    // with an AUTO tag and can be unloaded or deleted exactly like a
    // taught skill.
    fun addAuto(nameRaw: String, instructionRaw: String, toolsRaw: List<String>): String? {
        val name = nameRaw.trim()
        if (name.isEmpty()) return "Give the skill a name."
        if (name.length > MAX_NAME_CHARS) return "Name is too long — $MAX_NAME_CHARS characters at most."
        val id = skillIdFromName(name)
            ?: return "The name needs at least one letter or number."
        if (skills.any { it.id == id }) return "A skill called \"$name\" already exists."
        if (skills.any { it.name.equals(name, ignoreCase = true) }) return "A skill called \"$name\" already exists."
        val instruction = instructionRaw.trim()
        if (instruction.isEmpty()) return "Say what MAGI should learn — the instruction is empty."
        if (instruction.length > MAX_INSTRUCTION_CHARS) return "That is too long — $MAX_INSTRUCTION_CHARS characters at most."
        val tools = toolsRaw.map { it.trim().lowercase() }.distinct().filter { it.isNotEmpty() }
        if (tools.size > MAX_TOOLS_PER_SKILL) return "Too many tools — $MAX_TOOLS_PER_SKILL at most."
        val unknown = tools.firstOrNull { it !in knownTools }
        if (unknown != null) return "Unknown tool \"$unknown\" — a skill can only use tools NERV already has."
        synchronized(skills) {
            if (skills.size >= MAX_TAUGHT_SKILLS) return "Too many taught skills — delete one first (at most $MAX_TAUGHT_SKILLS)."
            // Auto cap: when full, prune the oldest auto skill that has
            // never been used. A skill MAGI actually uses is never pruned
            // for a new one — if every auto skill has been used, refuse.
            if (autoSkills().size >= MAX_AUTO_SKILLS) {
                if (!pruneOldestUnusedAuto()) {
                    return "Auto skills are full and every one has been used — delete one in SETUP > TAUGHT SKILLS first."
                }
            }
            val taughtAt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
            skills.add(TaughtSkill(id, name.uppercase(), instruction, tools, enabled = true, taughtAt = taughtAt, origin = ORIGIN_AUTO))
            save()
        }
        return null
    }

    // EVOLVER: drop the oldest auto skill that has never been used (first in
    // list order = oldest saved). Returns false when there is none to drop.
    // Must be called holding the skills lock.
    private fun pruneOldestUnusedAuto(): Boolean {
        val victim = skills.firstOrNull { it.origin == ORIGIN_AUTO && it.useCount <= 0 } ?: return false
        skills.remove(victim)
        return true
    }

    // EVOLVER: record that these skills were in MAGI's prompt for a run that
    // succeeded (live answer, debate approved). Called after every such run,
    // whether or not anything was auto-saved. This is what the prune rule
    // above reads: a skill with useCount 0 has never helped a run yet.
    fun markUsed(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        var changed = false
        synchronized(skills) {
            skills.forEach { skill ->
                if (skill.id in ids) {
                    skill.useCount += 1
                    skill.lastUsedAt = now
                    changed = true
                }
            }
            if (changed) save()
        }
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
                    taughtAt = obj.optString("taughtAt", ""),
                    // EVOLVER: old saves have none of these — they load as a
                    // taught skill that has never been counted as used.
                    origin = obj.optString("origin", ORIGIN_TAUGHT).takeIf { it == ORIGIN_AUTO } ?: ORIGIN_TAUGHT,
                    useCount = obj.optInt("useCount", 0).coerceAtLeast(0),
                    lastUsedAt = obj.optString("lastUsedAt", "")
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
                    .put("taughtAt", skill.taughtAt)
                    // EVOLVER: origin and usage survive a save/restore, so
                    // an auto skill stays AUTO after a backup restore.
                    .put("origin", skill.origin)
                    .put("useCount", skill.useCount)
                    .put("lastUsedAt", skill.lastUsedAt))
            }
            prefs.edit().putString(TAUGHT_JSON_KEY, array.toString()).apply()
        } catch (_: Exception) {   }
    }
}
