package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MagiMemory(context: Context) {

    data class MemoryEntry(
        val kind: String,
        val timeMillis: Long,
        val title: String,
        val detail: String,
        val decision: String
    )

    companion object {
        private const val PREFS = "nerv_magi_memory"
        private const val KEY_JSON = "memories_json"
        const val MAX_MEMORIES = 50
        const val MAX_TITLE_CHARS = 80
        const val MAX_DETAIL_CHARS = 200
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memories = load().toMutableList()

    fun rememberDebate(question: String, result: MagiEngine.MagiResult) {
        val yes = result.votes.count { it.second }
        val decision = when {
            result.votes.isEmpty() -> "NO VOTE"
            result.localDemo -> if (yes >= 2) "APPROVED $yes/3 (LOCAL DEMO)" else "DENIED $yes/3 (LOCAL DEMO)"
            yes >= 2 -> "APPROVED $yes/3"
            else -> "DENIED $yes/3"
        }
        add("DEBATE", question.trim(), result.text.trim(), decision)
    }

    fun rememberDecision(titleRaw: String, detailRaw: String, decisionRaw: String) {
        add("DECISION", titleRaw.trim(), detailRaw.trim(), decisionRaw.trim())
    }

    private fun add(kind: String, titleRaw: String, detailRaw: String, decisionRaw: String) {
        val title = titleRaw.take(MAX_TITLE_CHARS).ifBlank { "(no title)" }
        val detail = detailRaw.take(MAX_DETAIL_CHARS)
        val decision = decisionRaw.take(MAX_TITLE_CHARS).ifBlank { "—" }
        memories.add(0, MemoryEntry(kind, System.currentTimeMillis(), title, detail, decision))
        while (memories.size > MAX_MEMORIES) memories.removeAt(memories.size - 1)
        save()
    }

    fun recent(count: Int): List<MemoryEntry> = memories.take(count)

    fun since(timeMillis: Long): List<MemoryEntry> =
        memories.filter { it.timeMillis >= timeMillis }

    fun search(queryRaw: String): List<MemoryEntry> {
        val words = queryRaw.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return memories.filter { entry ->
            val haystack = "${entry.title} ${entry.detail} ${entry.decision}".lowercase(Locale.ROOT)
            words.all { haystack.contains(it) }
        }
    }

    fun count(): Int = memories.size

    fun clear() {
        memories.clear()
        save()
    }

    fun timeLabel(entry: MemoryEntry): String =
        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(entry.timeMillis))

    fun memoryContext(count: Int = 5): String {
        val items = recent(count)
        if (items.isEmpty()) return ""
        return items.joinToString("\n") { entry ->
            "- ${timeLabel(entry)} ${entry.title} — ${entry.decision}: ${entry.detail}"
        }
    }

    private fun load(): List<MemoryEntry> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = obj.optString("kind", "")
                val title = obj.optString("title", "")
                val detail = obj.optString("detail", "")
                val decision = obj.optString("decision", "")
                val time = obj.optLong("time", 0L)
                if (kind.isBlank() || title.isBlank() || time <= 0L) null
                else MemoryEntry(kind, time, title, detail, decision)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save() {
        val array = JSONArray()
        memories.forEach { entry ->
            array.put(
                JSONObject()
                    .put("kind", entry.kind)
                    .put("time", entry.timeMillis)
                    .put("title", entry.title)
                    .put("detail", entry.detail)
                    .put("decision", entry.decision)
            )
        }
        prefs.edit().putString(KEY_JSON, array.toString()).apply()
    }
}
