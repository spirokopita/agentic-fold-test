package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TimerStore(context: Context) {

    data class TimerEntry(
        val id: String,
        var name: String,
        var totalMillis: Long,
        var leftMillis: Long,
        var running: Boolean,
        var endAtMillis: Long,
        var finished: Boolean,
        val createdAt: Long
    ) {

        fun remainingMillis(now: Long): Long =
            if (running) maxOf(0L, endAtMillis - now) else leftMillis
    }

    data class Preset(val label: String, val millis: Long)

    companion object {
        const val MAX_TIMERS = 8
        const val MAX_NAME_CHARS = 24
        const val MIN_TOTAL_MILLIS = 1_000L
        const val MAX_TOTAL_MILLIS = 24L * 60 * 60 * 1000
        val PRESETS = listOf(
            Preset("1:00", 60_000L),
            Preset("5:00", 300_000L),
            Preset("10:00", 600_000L),
            Preset("15:00", 900_000L),
            Preset("30:00", 1_800_000L),
            Preset("1:00:00", 3_600_000L)
        )
    }

    private val prefs = context.getSharedPreferences("nerv_timers", Context.MODE_PRIVATE)
    private val timers = mutableListOf<TimerEntry>()

    init {
        load()

        if (timers.isEmpty()) add("ACTIVATION", 300_000L)
    }

    fun all(): List<TimerEntry> = timers.toList()

    fun anyRunning(): Boolean = timers.any { it.running }

    fun get(id: String): TimerEntry? = timers.firstOrNull { it.id == id }

    fun add(name: String, totalMillis: Long): TimerEntry? {
        if (timers.size >= MAX_TIMERS) return null
        val cleanName = name.trim().take(MAX_NAME_CHARS).ifBlank { "TIMER ${timers.size + 1}" }
        val total = totalMillis.coerceIn(MIN_TOTAL_MILLIS, MAX_TOTAL_MILLIS)
        val entry = TimerEntry(
            id = "t" + System.currentTimeMillis() + "_" + timers.size,
            name = cleanName,
            totalMillis = total,
            leftMillis = total,
            running = false,
            endAtMillis = 0L,
            finished = false,
            createdAt = System.currentTimeMillis()
        )
        timers.add(entry)
        save()
        return entry
    }

    fun remove(id: String): Boolean {
        val removed = timers.removeAll { it.id == id }
        if (removed) save()
        return removed
    }

    fun restore(restored: List<TimerEntry>) {
        timers.clear()
        val now = System.currentTimeMillis()
        restored.filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_TIMERS)
            .forEach { entry ->
                var running = entry.running
                var finished = entry.finished
                var left = entry.leftMillis.coerceIn(0L, MAX_TOTAL_MILLIS)
                var endAt = entry.endAtMillis
                if (running && !finished) {
                    if (endAt <= now) {
                        running = false
                        finished = true
                        left = 0L
                        endAt = 0L
                    } else {
                        left = endAt - now
                    }
                }
                timers.add(
                    TimerEntry(
                        id = entry.id,
                        name = entry.name.take(MAX_NAME_CHARS).ifBlank { "TIMER" },
                        totalMillis = entry.totalMillis.coerceIn(MIN_TOTAL_MILLIS, MAX_TOTAL_MILLIS),
                        leftMillis = left,
                        running = running,
                        endAtMillis = endAt,
                        finished = finished,
                        createdAt = entry.createdAt
                    )
                )
            }
        if (timers.isEmpty()) add("ACTIVATION", 300_000L)
        save()
    }

    fun start(id: String, now: Long): Boolean {
        val entry = get(id) ?: return false
        if (entry.finished) {
            entry.finished = false
            entry.leftMillis = entry.totalMillis
        }
        if (entry.leftMillis <= 0L) entry.leftMillis = entry.totalMillis
        entry.running = true
        entry.endAtMillis = now + entry.leftMillis
        save()
        return true
    }

    fun stop(id: String, now: Long): Boolean {
        val entry = get(id) ?: return false
        if (!entry.running) return false
        entry.leftMillis = entry.remainingMillis(now)
        entry.running = false
        entry.endAtMillis = 0L
        save()
        return true
    }

    fun reset(id: String, now: Long): Boolean {
        val entry = get(id) ?: return false
        entry.running = false
        entry.finished = false
        entry.leftMillis = entry.totalMillis
        entry.endAtMillis = 0L
        save()
        return true
    }

    fun addMinute(id: String, now: Long): Boolean {
        val entry = get(id) ?: return false
        entry.finished = false
        entry.leftMillis = (entry.remainingMillis(now) + 60_000L)
            .coerceAtMost(MAX_TOTAL_MILLIS)
        entry.totalMillis = (entry.totalMillis + 60_000L).coerceAtMost(MAX_TOTAL_MILLIS)
        if (entry.running) entry.endAtMillis = now + entry.leftMillis
        save()
        return true
    }

    fun tick(now: Long): List<TimerEntry> {
        val newlyFinished = mutableListOf<TimerEntry>()
        var changed = false
        for (entry in timers) {
            if (!entry.running || entry.finished) continue
            if (entry.endAtMillis - now <= 0L) {
                entry.running = false
                entry.finished = true
                entry.leftMillis = 0L
                entry.endAtMillis = 0L
                newlyFinished.add(entry)
                changed = true
            }
        }
        if (changed) save()
        return newlyFinished
    }

    private fun load() {
        timers.clear()
        val raw = prefs.getString("timers_json", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                val name = obj.optString("name", "TIMER").take(MAX_NAME_CHARS)
                val total = obj.optLong("totalMillis", 300_000L)
                    .coerceIn(MIN_TOTAL_MILLIS, MAX_TOTAL_MILLIS)
                var left = obj.optLong("leftMillis", total).coerceIn(0L, MAX_TOTAL_MILLIS)
                var running = obj.optBoolean("running", false)
                var endAt = obj.optLong("endAtMillis", 0L)
                var finished = obj.optBoolean("finished", false)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                if (running && !finished) {
                    val now = System.currentTimeMillis()
                    if (endAt <= now) {
                        running = false
                        finished = true
                        left = 0L
                        endAt = 0L
                    } else {
                        left = endAt - now
                    }
                }
                timers.add(TimerEntry(id, name, total, left, running, endAt, finished, createdAt))
            }
        } catch (e: Exception) {

            timers.clear()
        }
    }

    private fun save() {
        val array = JSONArray()
        for (entry in timers) {
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("totalMillis", entry.totalMillis)
                put("leftMillis", entry.leftMillis)
                put("running", entry.running)
                put("endAtMillis", entry.endAtMillis)
                put("finished", entry.finished)
                put("createdAt", entry.createdAt)
            })
        }
        prefs.edit().putString("timers_json", array.toString()).apply()
    }
}
