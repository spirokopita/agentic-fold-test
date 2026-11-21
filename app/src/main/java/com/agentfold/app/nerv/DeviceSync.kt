package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceSync(
    context: Context,
    private val nervBackup: NervBackup
) {

    companion object {
        private const val PREFS = "nerv_sync"
        private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"
        private const val KEY_LAST_SYNC_NOTE = "last_sync_note"
        private const val MAX_SYNC_TEXT_CHARS = 2 * 1024 * 1024

        const val NO_SERVER_TEXT =
            "MANUAL SYNC FOR NOW — NO SERVER. There is no live sync yet: " +
            "two phones keeping each other up to date on their own needs a sync " +
            "server you own, and that does not exist yet. Until then you move the " +
            "file yourself — export it here, share it to your other phone, share " +
            "that text into NERV there, then IMPORT SYNC FILE. Nothing is stored " +
            "anywhere you do not own, and nothing is ever sent unless you tap SHARE."

        const val MERGE_RULE_TEXT =
            "HOW A SYNC MERGES: newest wins per item — a note edited on the other " +
            "phone replaces the older copy here, and the other way round. Nothing " +
            "is silently deleted: a message only counts as deleted when it is " +
            "deleted on BOTH phones, so a sync never wipes something off this phone " +
            "on its own. Your API keys and lock PIN are never in the file and are " +
            "never touched."

        fun looksLikeSyncText(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.length < 10 || trimmed.length > MAX_SYNC_TEXT_CHARS) return false
            if (!trimmed.startsWith("{")) return false
            return try {
                val root = JSONObject(trimmed)
                root.optString("app", "") == "NERV" &&
                    root.optInt("format", 0) == NervBackup.FORMAT_VERSION
            } catch (_: Exception) {
                false
            }
        }
    }

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val syncDir: File get() = nervBackup.syncDir

    fun export(): File? = nervBackup.exportSync()

    fun listFiles(): List<File> = nervBackup.listSyncFiles()

    fun delete(file: File): Boolean = nervBackup.deleteSyncFile(file)

    fun read(file: File): String? = nervBackup.readSyncFile(file)

    fun validate(file: File): NervBackup.Outcome = nervBackup.validateSync(file)

    fun merge(file: File): NervBackup.Outcome = nervBackup.merge(file)

    fun saveIncomingSyncText(text: String): File? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_SYNC_TEXT_CHARS) return null
        if (!looksLikeSyncText(trimmed)) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val baseName = "nerv-sync-received-$stamp.json"
        var file = File(syncDir, baseName)
        var n = 2
        while (file.exists()) {
            file = File(syncDir, "nerv-sync-received-$stamp-$n.json")
            n++
        }
        return try {
            file.writeText(trimmed, Charsets.UTF_8)

            if (!nervBackup.validateSync(file).ok) {
                try { file.delete() } catch (_: Exception) { }
                null
            } else {
                file
            }
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) { }
            null
        }
    }

    fun markSynced(summary: NervBackup.Summary) {
        val note = "${summary.chats} chats · ${summary.notes} notes · " +
            "${summary.customPacks} custom packs · ${summary.taughtSkills} taught skills · " +
            "${summary.routines} routines · ${summary.timers} timers"
        prefs.edit()
            .putLong(KEY_LAST_SYNC_MILLIS, System.currentTimeMillis())
            .putString(KEY_LAST_SYNC_NOTE, note)
            .apply()
    }

    fun lastSyncLabel(): String {
        val millis = prefs.getLong(KEY_LAST_SYNC_MILLIS, 0L)
        if (millis <= 0L) return "Never synced yet — export a sync file on one phone and import it on the other."
        val whenText = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))
        val note = prefs.getString(KEY_LAST_SYNC_NOTE, "").orEmpty()
        return if (note.isBlank()) "Last sync: $whenText."
        else "Last sync: $whenText — this phone then held $note."
    }
}
