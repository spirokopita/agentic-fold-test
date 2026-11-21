package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteStore(context: Context) {

    data class Note(
        val id: String,
        var folder: String,
        var title: String,
        var body: String,
        val createdAt: String,
        var updatedAt: String
    )

    data class Hit(val noteId: String, val snippet: String)

    companion object {
        const val DEFAULT_FOLDER = "GENERAL"
        const val MAX_NOTES = 100
        const val MAX_FOLDERS = 20
        const val MAX_TITLE_CHARS = 48
        const val MAX_BODY_CHARS = 2000
        const val MAX_FOLDER_CHARS = 24
        private const val NOTES_JSON_KEY = "notes_json"
        private const val FOLDERS_JSON_KEY = "folders_json"
        private const val LEGACY_IMPORTED_KEY = "legacy_imported"

        fun titleFromBody(body: String): String {
            val words = body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(6)
            val title = words.joinToString(" ").take(MAX_TITLE_CHARS).trim()
            return title.ifBlank { "NOTE" }.uppercase(Locale.getDefault())
        }
    }

    private val prefs = context.getSharedPreferences("nerv_notes", Context.MODE_PRIVATE)
    private val legacyFile = File(context.filesDir, "nerv-notes.txt")
    private val lock = Any()

    val notes: MutableList<Note> = loadNotes().toMutableList()

    val folders: MutableList<String> = loadFolders().toMutableList()

    init {
        if (folders.isEmpty()) folders.add(DEFAULT_FOLDER)
        if (notes.any { it.folder !in folders }) {

            var changed = false
            notes.forEach { if (it.folder !in folders) { it.folder = DEFAULT_FOLDER; changed = true } }
            if (changed) save()
        }
        importLegacyFileOnce()
    }

    fun all(): List<Note> = synchronized(lock) { notes.toList() }

    fun allFolders(): List<String> = synchronized(lock) { folders.toList() }

    fun notesIn(folder: String): List<Note> = synchronized(lock) {
        val clean = folder.trim().uppercase(Locale.getDefault())
        if (clean.isEmpty() || clean == "ALL") notes.toList()
        else notes.filter { it.folder == clean }
    }

    fun countIn(folder: String): Int = notesIn(folder).size

    fun restore(restored: List<Note>, restoredFolders: List<String>) {
        synchronized(lock) {
            notes.clear()
            notes.addAll(restored.filter { it.body.isNotBlank() }.take(MAX_NOTES))
            folders.clear()
            folders.add(DEFAULT_FOLDER)
            restoredFolders.map { it.trim().uppercase(Locale.getDefault()) }
                .filter { it.isNotEmpty() && it != "ALL" && it != DEFAULT_FOLDER }
                .distinct().take(MAX_FOLDERS - 1).forEach { folders.add(it) }
            notes.forEach { if (it.folder !in folders) it.folder = DEFAULT_FOLDER }
            save()
        }
    }

    fun add(folderRaw: String, titleRaw: String, bodyRaw: String): String? {
        val body = bodyRaw.trim()
        if (body.isEmpty()) return "The note is empty — write something first."
        if (body.length > MAX_BODY_CHARS) return "That is too long — $MAX_BODY_CHARS characters at most."
        if (notes.size >= MAX_NOTES) return "Too many notes — delete one first (at most $MAX_NOTES)."
        val title = titleRaw.trim().take(MAX_TITLE_CHARS).ifBlank { titleFromBody(body) }
        val folder = folderFor(folderRaw)
        val now = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        val id = "note-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
        synchronized(lock) {
            folders.addIfMissing(folder)
            notes.add(0, Note(id, folder, title, body, now, now))
            save()
        }
        return null
    }

    fun update(id: String, folderRaw: String, titleRaw: String, bodyRaw: String): String? {
        val body = bodyRaw.trim()
        if (body.isEmpty()) return "The note is empty — write something first."
        if (body.length > MAX_BODY_CHARS) return "That is too long — $MAX_BODY_CHARS characters at most."
        val title = titleRaw.trim().take(MAX_TITLE_CHARS).ifBlank { titleFromBody(body) }
        val folder = folderFor(folderRaw)
        synchronized(lock) {
            val note = notes.firstOrNull { it.id == id } ?: return "That note is gone already."
            folders.addIfMissing(folder)
            note.folder = folder
            note.title = title
            note.body = body
            note.updatedAt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
            save()
        }
        return null
    }

    fun delete(id: String): Boolean {
        synchronized(lock) {
            val note = notes.firstOrNull { it.id == id } ?: return false
            notes.remove(note)
            save()
            return true
        }
    }

    fun move(id: String, folderRaw: String): Boolean {
        val folder = folderFor(folderRaw)
        synchronized(lock) {
            val note = notes.firstOrNull { it.id == id } ?: return false
            folders.addIfMissing(folder)
            note.folder = folder
            note.updatedAt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
            save()
            return true
        }
    }

    fun addFolder(nameRaw: String): String? {
        val name = cleanFolderName(nameRaw) ?: return "A folder name needs at least one letter or number."
        if (name == "ALL") return "\"ALL\" means every folder, so it cannot be a folder name."
        if (folders.any { it.equals(name, ignoreCase = true) }) return "A folder called \"$name\" already exists."
        if (folders.size >= MAX_FOLDERS) return "Too many folders — delete an empty one first (at most $MAX_FOLDERS)."
        synchronized(lock) {
            folders.add(name)
            save()
        }
        return null
    }

    fun renameFolder(oldRaw: String, newRaw: String): String? {
        val old = oldRaw.trim().uppercase(Locale.getDefault())
        val new = cleanFolderName(newRaw) ?: return "A folder name needs at least one letter or number."
        if (old == DEFAULT_FOLDER) return "GENERAL cannot be renamed — it is the default folder."
        if (new == "ALL") return "\"ALL\" means every folder, so it cannot be a folder name."
        synchronized(lock) {
            if (folders.none { it == old }) return "There is no folder called \"$old\"."
            if (old != new && folders.any { it.equals(new, ignoreCase = true) }) return "A folder called \"$new\" already exists."
            if (old == new) return null
            folders[folders.indexOf(old)] = new
            notes.forEach { if (it.folder == old) it.folder = new }
            save()
        }
        return null
    }

    fun deleteFolder(nameRaw: String): String? {
        val name = nameRaw.trim().uppercase(Locale.getDefault())
        if (name == DEFAULT_FOLDER) return "GENERAL cannot be deleted — it is the default folder."
        synchronized(lock) {
            if (folders.none { it == name }) return "There is no folder called \"$name\"."
            val held = notes.count { it.folder == name }
            if (held > 0) return "That folder still holds $held note${if (held == 1) "" else "s"} — move or delete them first."
            folders.remove(name)
            save()
        }
        return null
    }

    fun search(queryRaw: String): List<Hit> {
        val words = queryRaw.trim().lowercase(Locale.getDefault())
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return synchronized(lock) {
            notes.mapNotNull { note ->
                val haystack = "${note.title}\n${note.body}".lowercase(Locale.getDefault())
                if (!words.all { haystack.contains(it) }) return@mapNotNull null
                Hit(note.id, snippetFor(note, words.first()))
            }
        }
    }

    private fun snippetFor(note: Note, firstWord: String): String {
        val text = "${note.title}: ${note.body}".replace(Regex("\\s+"), " ").trim()
        val at = text.lowercase(Locale.getDefault()).indexOf(firstWord)
        if (at < 0) return text.take(48)
        val start = (at - 16).coerceAtLeast(0)
        val end = (at + firstWord.length + 24).coerceAtMost(text.length)
        val piece = text.substring(start, end)
        return (if (start > 0) "…" else "") + piece + (if (end < text.length) "…" else "")
    }

    private fun cleanFolderName(raw: String): String? {
        val name = raw.trim().uppercase(Locale.getDefault())
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else ' ' }
            .joinToString("").replace(Regex("\\s+"), " ").trim()
            .take(MAX_FOLDER_CHARS).trimEnd('-').trim()
        return name.ifBlank { null }
    }

    private fun folderFor(raw: String): String {
        val name = cleanFolderName(raw) ?: return DEFAULT_FOLDER
        return if (name in folders || folders.size < MAX_FOLDERS) name else DEFAULT_FOLDER
    }

    private fun MutableList<String>.addIfMissing(folder: String) {
        if (folder !in this && size < MAX_FOLDERS) add(folder)
    }

    private fun importLegacyFileOnce() {
        if (prefs.getBoolean(LEGACY_IMPORTED_KEY, false)) return
        prefs.edit().putBoolean(LEGACY_IMPORTED_KEY, true).apply()
        if (notes.isNotEmpty()) return
        val lines = try {
            if (!legacyFile.exists()) return
            legacyFile.readText().lines().map { it.trim() }.filter { it.isNotBlank() }
        } catch (_: Exception) { return }
        if (lines.isEmpty()) return
        val now = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        synchronized(lock) {
            lines.take(MAX_NOTES).forEachIndexed { i, line ->
                notes.add(Note(
                    id = "note-legacy-$i-${System.currentTimeMillis()}",
                    folder = DEFAULT_FOLDER,
                    title = titleFromBody(line),
                    body = line.take(MAX_BODY_CHARS),
                    createdAt = now,
                    updatedAt = now
                ))
            }
            save()
        }
    }

    private fun loadNotes(): List<Note> {
        val raw = prefs.getString(NOTES_JSON_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = mutableListOf<Note>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val title = obj.optString("title", "").trim()
                val body = obj.optString("body", "").trim()
                if (id.isEmpty() || body.isEmpty()) continue
                out.add(Note(
                    id = id,
                    folder = obj.optString("folder", DEFAULT_FOLDER).trim().ifBlank { DEFAULT_FOLDER },
                    title = title.ifBlank { titleFromBody(body) },
                    body = body,
                    createdAt = obj.optString("createdAt", ""),
                    updatedAt = obj.optString("updatedAt", "")
                ))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadFolders(): List<String> {
        val raw = prefs.getString(FOLDERS_JSON_KEY, null) ?: return listOf(DEFAULT_FOLDER)
        return try {
            val array = JSONArray(raw)
            val out = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val name = array.optString(i, "").trim().uppercase(Locale.getDefault())
                if (name.isNotEmpty() && name != "ALL" && name !in out) out.add(name)
            }
            if (DEFAULT_FOLDER !in out) out.add(0, DEFAULT_FOLDER)
            out
        } catch (_: Exception) {
            listOf(DEFAULT_FOLDER)
        }
    }

    private fun save() {
        try {
            val notesJson = JSONArray()
            notes.forEach { note ->
                notesJson.put(JSONObject()
                    .put("id", note.id)
                    .put("folder", note.folder)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt))
            }
            val foldersJson = JSONArray()
            folders.forEach { foldersJson.put(it) }
            prefs.edit()
                .putString(NOTES_JSON_KEY, notesJson.toString())
                .putString(FOLDERS_JSON_KEY, foldersJson.toString())
                .apply()
        } catch (_: Exception) {   }
    }
}
