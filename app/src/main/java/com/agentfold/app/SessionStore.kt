package com.agentfold.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import android.os.Handler
import android.os.Looper

/** Local, bounded, inspectable event log. No network access or secret collection. */
object SessionStore {
    private const val PREFS = "agent_fold_session"
    private const val LOG = "recent_events"
    private const val MAX_CHARS = 7000
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    fun subscribe(listener: () -> Unit) { listeners.add(listener) }
    fun unsubscribe(listener: () -> Unit) { listeners.remove(listener) }
    fun notifyChanged() { main.post { listeners.forEach { it() } } }

    @Synchronized fun write(context: Context, event: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val old = prefs.getString(LOG, "") ?: ""
        val combined = ("$old\n$now  $event").takeLast(MAX_CHARS).trim()
        prefs.edit().putString(LOG, combined).apply()
        notifyChanged()
    }

    fun events(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LOG, "No actions recorded yet.") ?: "No actions recorded yet."

    fun setFile(context: Context, name: String, uri: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("file_name", name).putString("file_uri", uri).apply()

    fun file(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("file_name", "No file selected") ?: "No file selected"
    fun fileUri(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("file_uri", null)

    fun setOutput(context: Context, name: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("output_name", name).apply()

    fun output(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("output_name", "No output saved") ?: "No output saved"
}

