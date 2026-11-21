package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import java.util.Locale

class ChatFolderStore(context: Context, private val chatStore: ChatStore? = null) {

    companion object {
        const val DEFAULT_FOLDER = "GENERAL"
        const val ALL_FOLDERS = "ALL"
        const val MAX_FOLDERS = 20
        const val MAX_FOLDER_CHARS = 24
        private const val FOLDERS_JSON_KEY = "folders_json"

        fun cleanFolderName(raw: String): String? {
            val name = raw.trim().uppercase(Locale.getDefault())
                .map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else ' ' }
                .joinToString("").replace(Regex("\\s+"), " ").trim()
                .take(MAX_FOLDER_CHARS).trimEnd('-').trim()
            return name.ifBlank { null }
        }
    }

    private val prefs = context.getSharedPreferences("nerv_chat_folders", Context.MODE_PRIVATE)
    private val lock = Any()

    val folders: MutableList<String> = loadFolders().toMutableList()

    init {
        if (folders.isEmpty()) folders.add(DEFAULT_FOLDER)
    }

    fun allFolders(): List<String> = synchronized(lock) { folders.toList() }

    fun countIn(folder: String): Int =
        chatStore?.entries()?.count { it.folder == folder.trim().uppercase(Locale.getDefault()) } ?: 0

    fun addFolder(nameRaw: String): String? {
        val name = cleanFolderName(nameRaw) ?: return "A folder name needs at least one letter or number."
        if (name == ALL_FOLDERS) return "\"ALL\" means every folder, so it cannot be a folder name."
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
        if (new == ALL_FOLDERS) return "\"ALL\" means every folder, so it cannot be a folder name."
        synchronized(lock) {
            if (folders.none { it == old }) return "There is no folder called \"$old\"."
            if (old != new && folders.any { it.equals(new, ignoreCase = true) }) return "A folder called \"$new\" already exists."
            if (old == new) return null
            folders[folders.indexOf(old)] = new
            chatStore?.entries()?.forEachIndexed { index, entry ->
                if (entry.folder == old) chatStore.setFolder(index, new)
            }
            save()
        }
        return null
    }

    fun deleteFolder(nameRaw: String): String? {
        val name = nameRaw.trim().uppercase(Locale.getDefault())
        if (name == DEFAULT_FOLDER) return "GENERAL cannot be deleted — it is the default folder."
        synchronized(lock) {
            if (folders.none { it == name }) return "There is no folder called \"$name\"."
            val held = countIn(name)
            if (held > 0) return "That folder still holds $held message${if (held == 1) "" else "s"} — move or delete them first."
            folders.remove(name)
            save()
        }
        return null
    }

    private fun loadFolders(): List<String> {
        val raw = prefs.getString(FOLDERS_JSON_KEY, null) ?: return listOf(DEFAULT_FOLDER)
        return try {
            val array = JSONArray(raw)
            val out = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val name = array.optString(i, "").trim().uppercase(Locale.getDefault())
                if (name.isNotEmpty() && name != ALL_FOLDERS && name !in out) out.add(name)
            }
            if (DEFAULT_FOLDER !in out) out.add(0, DEFAULT_FOLDER)
            out
        } catch (_: Exception) {
            listOf(DEFAULT_FOLDER)
        }
    }

    private fun save() {
        try {
            val foldersJson = JSONArray()
            folders.forEach { foldersJson.put(it) }
            prefs.edit().putString(FOLDERS_JSON_KEY, foldersJson.toString()).apply()
        } catch (_: Exception) {   }
    }
}
