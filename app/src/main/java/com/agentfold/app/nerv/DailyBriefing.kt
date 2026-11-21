package com.agentfold.app.nerv

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyBriefing(context: Context) {

    companion object {
        private const val PREFS = "nerv_briefing"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SHOWN = "last_shown_date"

        const val SHOW_FROM_HOUR = 6

        const val OVERNIGHT_FROM_HOUR = 20
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun shouldShowToday(now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        if (prefs.getString(KEY_LAST_SHOWN, null) == todayDate()) return false
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour >= SHOW_FROM_HOUR
    }

    fun markShownToday() {
        prefs.edit().putString(KEY_LAST_SHOWN, todayDate()).apply()
    }

    fun lastShownDate(): String = prefs.getString(KEY_LAST_SHOWN, null) ?: "never"

    fun build(
        phoneTaskStore: PhoneTaskStore,
        timerStore: TimerStore,
        noteStore: NoteStore,
        magiMemory: MagiMemory,

        calendarLines: List<String> = emptyList()
    ): String {
        val text = StringBuilder()
        val today = SimpleDateFormat("EEEE dd MMM yyyy", Locale.getDefault()).format(Date())
        text.append("NERV — DAILY BRIEFING · $today\n\n")

        val overnightFrom = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, OVERNIGHT_FROM_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val overnight = magiMemory.since(overnightFrom)
        text.append("OVERNIGHT DEBATES\n")
        if (overnight.isEmpty()) {
            text.append("No debates overnight.\n")
        } else {
            overnight.take(5).forEach { entry ->
                text.append("· ${magiMemory.timeLabel(entry)} — ${entry.title} (${entry.decision})\n")
            }
        }

        val tasks = phoneTaskStore.all()
        val done = tasks.filter { it.status == PhoneTaskStore.TaskStatus.DONE }
        val waiting = tasks.filter { it.status == PhoneTaskStore.TaskStatus.PROPOSED }
        text.append("\nFINISHED TASKS\n")
        if (done.isEmpty()) {
            text.append("No finished tasks.\n")
        } else {
            done.take(5).forEach { task ->
                text.append("· ${task.objective}\n")
            }
        }
        if (waiting.isNotEmpty()) {
            text.append("Waiting for your approval: ${waiting.size} task(s).\n")
        }

        val now = System.currentTimeMillis()
        val timers = timerStore.all()
        val finished = timers.filter { it.finished }
        val running = timers.filter { it.running }
        text.append("\nTIMERS\n")
        if (finished.isNotEmpty()) {
            finished.forEach { timer -> text.append("· ${timer.name} — FINISHED\n") }
        }
        if (running.isNotEmpty()) {
            running.forEach { timer ->
                text.append("· ${timer.name} — ${formatRemaining(timer.remainingMillis(now))} left\n")
            }
        }
        if (finished.isEmpty() && running.isEmpty()) {
            text.append("No timers running.\n")
        }

        text.append("\nRECENT NOTES\n")
        val notes = noteStore.all().take(3)
        if (notes.isEmpty()) {
            text.append("No notes yet.\n")
        } else {
            notes.forEach { note ->
                text.append("· [${note.folder}] ${note.title}\n")
            }
        }

        if (calendarLines.isNotEmpty()) {
            text.append("\nTODAY'S CALENDAR\n")
            calendarLines.forEach { line -> text.append("$line\n") }
        }
        return text.toString()
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}
