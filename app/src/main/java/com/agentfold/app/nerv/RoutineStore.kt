package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RoutineStore(context: Context) {

    data class Routine(
        val id: String,
        val name: String,
        val time: String,
        val prompt: String,
        val days: String,
        var enabled: Boolean,
        var approved: Boolean,
        var lastRunDate: String
    )

    companion object {
        private const val PREFS = "nerv_routines"
        private const val KEY_JSON = "routines_json"
        const val MAX_ROUTINES = 10
        const val MAX_NAME_CHARS = 40
        const val MAX_PROMPT_CHARS = 500
        val DAY_CHOICES = listOf("DAILY", "WEEKDAYS", "WEEKENDS")
        private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        fun validTime(time: String): Boolean = TIME_PATTERN.matches(time.trim())
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val routines = load().toMutableList()

    fun all(): List<Routine> = routines.toList()

    fun get(id: String): Routine? = routines.firstOrNull { it.id == id }

    fun todayDate(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

    fun add(nameRaw: String, timeRaw: String, promptRaw: String, daysRaw: String): String? {
        val name = nameRaw.trim().take(MAX_NAME_CHARS)
        if (name.isEmpty()) return "Give the routine a name."
        val time = timeRaw.trim()
        if (!validTime(time)) return "Time must be HH:mm, 24-hour — e.g. 07:30."
        val prompt = promptRaw.trim()
        if (prompt.isEmpty()) return "Write what MAGI should do."
        if (prompt.length > MAX_PROMPT_CHARS) return "That is too long — $MAX_PROMPT_CHARS characters at most."
        val days = daysRaw.trim().uppercase(Locale.ROOT)
        if (days !in DAY_CHOICES) return "Pick DAILY, WEEKDAYS or WEEKENDS."
        if (routines.size >= MAX_ROUTINES) return "Too many routines — delete one first (at most $MAX_ROUTINES)."
        val id = "routine-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
        routines.add(0, Routine(id, name, time, prompt, days, enabled = true, approved = false, lastRunDate = ""))
        save()
        return null
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val routine = get(id) ?: return false
        routine.enabled = enabled
        save()
        return true
    }

    fun setApproved(id: String): Boolean {
        val routine = get(id) ?: return false
        routine.approved = true
        save()
        return true
    }

    fun delete(id: String): Boolean {
        val removed = routines.removeAll { it.id == id }
        if (removed) save()
        return removed
    }

    fun restore(restored: List<Routine>) {
        routines.clear()
        restored.filter { it.id.isNotBlank() && it.name.isNotBlank() && validTime(it.time) &&
            it.prompt.isNotBlank() && it.days in DAY_CHOICES }
            .distinctBy { it.id }
            .take(MAX_ROUTINES)
            .forEach { routines.add(it) }
        save()
    }

    fun markRun(id: String, date: String) {
        val routine = get(id) ?: return
        routine.lastRunDate = date
        save()
    }

    fun due(now: Long = System.currentTimeMillis()): List<Routine> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val timeNow = String.format(
            Locale.getDefault(), "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
        val today = todayDate(now)
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return routines.filter { routine ->
            routine.enabled && routine.approved &&
                routine.time == timeNow && routine.lastRunDate != today &&
                when (routine.days) {
                    "WEEKDAYS" -> day in Calendar.MONDAY..Calendar.FRIDAY
                    "WEEKENDS" -> day == Calendar.SATURDAY || day == Calendar.SUNDAY
                    else -> true
                }
        }
    }

    private fun load(): List<Routine> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val time = obj.optString("time", "")
                val prompt = obj.optString("prompt", "")
                val days = obj.optString("days", "DAILY")
                if (id.isBlank() || name.isBlank() || !validTime(time) ||
                    prompt.isBlank() || days !in DAY_CHOICES
                ) null
                else Routine(
                    id, name, time, prompt, days,
                    enabled = obj.optBoolean("enabled", true),
                    approved = obj.optBoolean("approved", false),
                    lastRunDate = obj.optString("lastRun", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save() {
        val array = JSONArray()
        routines.forEach { routine ->
            array.put(
                JSONObject()
                    .put("id", routine.id)
                    .put("name", routine.name)
                    .put("time", routine.time)
                    .put("prompt", routine.prompt)
                    .put("days", routine.days)
                    .put("enabled", routine.enabled)
                    .put("approved", routine.approved)
                    .put("lastRun", routine.lastRunDate)
            )
        }
        prefs.edit().putString(KEY_JSON, array.toString()).apply()
    }
}
