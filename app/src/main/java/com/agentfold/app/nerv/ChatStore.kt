package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatStore(context: Context) {
    enum class Role { YOU, AI, SYSTEM }

    data class Entry(
        val role: Role,
        val text: String,
        val time: String,
        val sender: String = "",
        val votes: List<Pair<String, Boolean>> = emptyList(),
        val log: List<String> = emptyList(),
        var deleted: Boolean = false,

        var folder: String = "GENERAL"
    )

    private val file = File(context.filesDir, "nerv-chat.json")
    private val entries = mutableListOf<Entry>()

    init { load() }

    fun entries(): List<Entry> = entries.toList()

    fun history(): List<Pair<String, String>> = entries
        .filter { !it.deleted && it.role != Role.SYSTEM }
        .map { (if (it.role == Role.YOU) "user" else "assistant") to it.text }

    fun add(entry: Entry) {
        entries.add(entry)
        while (entries.size > 40) entries.removeAt(0)
        save()
    }

    fun toggleDeleted(index: Int) {
        entries.getOrNull(index)?.let { it.deleted = !it.deleted; save() }
    }

    fun setFolder(index: Int, folderRaw: String) {
        val folder = folderRaw.trim().uppercase(java.util.Locale.getDefault())
            .ifBlank { "GENERAL" }.takeIf { it != "ALL" } ?: "GENERAL"
        entries.getOrNull(index)?.let { it.folder = folder; save() }
    }

    fun entriesIn(folder: String): List<Entry> {
        val clean = folder.trim().uppercase(java.util.Locale.getDefault())
        if (clean.isEmpty() || clean == "ALL") return entries()
        return entries.filter { it.folder == clean }
    }

    fun countIn(folder: String): Int = entriesIn(folder).size

    fun clear() { entries.clear(); save() }

    fun restore(restored: List<Entry>) {
        entries.clear()
        entries.addAll(restored.takeLast(40))
        save()
    }

    fun lastAiLog(): List<String> = entries.lastOrNull { it.role == Role.AI }?.log ?: emptyList()

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val votes = mutableListOf<Pair<String, Boolean>>()
                o.optJSONArray("votes")?.let { v ->
                    for (j in 0 until v.length()) {
                        val pair = v.getJSONArray(j)
                        votes.add(pair.getString(0) to pair.getBoolean(1))
                    }
                }
                val log = mutableListOf<String>()
                o.optJSONArray("log")?.let { l ->
                    for (j in 0 until l.length()) log.add(l.getString(j))
                }
                entries.add(
                    Entry(
                        role = Role.valueOf(o.optString("role", "SYSTEM")),
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
            while (entries.size > 40) entries.removeAt(0)
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
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
                arr.put(o)
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {   }
    }
}
