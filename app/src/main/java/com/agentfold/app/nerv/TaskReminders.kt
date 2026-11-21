package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray

class TaskReminders(context: Context) {

    companion object {
        const val DEFAULT_THRESHOLD_MINUTES = 10

        const val TIMER_WARNING_MILLIS = 60_000L
        val THRESHOLD_CHOICES = listOf(5, 10, 15, 30, 60)
        private const val MAX_REMINDED = 20
    }

    private val prefs = context.getSharedPreferences("nerv_reminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var waitingThresholdMinutes: Int
        get() {
            val saved = prefs.getInt("waiting_minutes", DEFAULT_THRESHOLD_MINUTES)
            return if (saved in THRESHOLD_CHOICES) saved else DEFAULT_THRESHOLD_MINUTES
        }
        set(value) {
            prefs.edit().putInt(
                "waiting_minutes",
                if (value in THRESHOLD_CHOICES) value else DEFAULT_THRESHOLD_MINUTES
            ).apply()
        }

    fun cycleThreshold() {
        val current = waitingThresholdMinutes
        val nextIndex = (THRESHOLD_CHOICES.indexOf(current) + 1) % THRESHOLD_CHOICES.size
        waitingThresholdMinutes = THRESHOLD_CHOICES[nextIndex]
    }

    fun hasRemindedTask(id: String): Boolean = loadSet("reminded_tasks").contains(id)

    fun markTaskReminded(id: String) = saveSet("reminded_tasks", loadSet("reminded_tasks") + id)

    fun hasRemindedTimer(key: String): Boolean = loadSet("reminded_timers").contains(key)

    fun markTimerReminded(key: String) = saveSet("reminded_timers", loadSet("reminded_timers") + key)

    private fun loadSet(key: String): Set<String> {
        val raw = prefs.getString(key, null) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            val out = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val id = array.optString(i, "").trim()
                if (id.isNotEmpty()) out.add(id)
            }
            out
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveSet(key: String, ids: Set<String>) {
        val kept = ids.toList().takeLast(MAX_REMINDED)
        val array = JSONArray()
        kept.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
