package com.agentfold.app.nerv

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CalendarLink(private val context: Context) {

    data class CalEvent(
        val id: Long,
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val location: String,
        val allDay: Boolean
    )

    companion object {

        const val MAX_EVENTS = 50

        fun formatTime(ms: Long): String =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

        fun formatDay(ms: Long): String =
            SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

        fun lineFor(event: CalEvent): String {
            val day = if (isToday(event.startMs)) "" else "${formatDay(event.startMs)} "
            val time = if (event.allDay) "ALL DAY" else "${formatTime(event.startMs)}–${formatTime(event.endMs)}"
            val place = if (event.location.isBlank()) "" else " (${event.location})"
            return "$day$time ${event.title}$place"
        }

        fun isToday(ms: Long): Boolean {
            val now = Calendar.getInstance()
            val at = Calendar.getInstance().apply { timeInMillis = ms }
            return now.get(Calendar.YEAR) == at.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == at.get(Calendar.DAY_OF_YEAR)
        }

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun parseDateTime(dateRaw: String, timeRaw: String): Long? {
            val clean = "${dateRaw.trim()} ${timeRaw.trim()}"
            return try {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }.parse(clean)?.time
            } catch (_: Exception) { null }
        }

        fun todayDateString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun today(): List<CalEvent> {
        val start = startOfToday()
        val end = start + 24L * 60 * 60 * 1000
        return upcoming(start, end)
    }

    fun nextEvent(): CalEvent? {
        val now = System.currentTimeMillis()
        return upcoming(now, now + 7L * 24 * 60 * 60 * 1000).firstOrNull()
    }

    fun upcoming(fromMs: Long, toMs: Long): List<CalEvent> {
        if (!hasReadPermission()) return emptyList()
        val out = mutableListOf<CalEvent>()
        try {
            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
            val args = arrayOf(fromMs.toString(), toMs.toString())
            val sort = "${CalendarContract.Events.DTSTART} ASC"
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val locCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val allDayCol = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                while (cursor.moveToNext() && out.size < MAX_EVENTS) {
                    val start = if (startCol >= 0) cursor.getLong(startCol) else 0L
                    if (start <= 0L) continue
                    val end = if (endCol >= 0) cursor.getLong(endCol) else start
                    out.add(
                        CalEvent(
                            id = if (idCol >= 0) cursor.getLong(idCol) else -1L,
                            title = if (titleCol >= 0) cursor.getString(titleCol).orEmpty().ifBlank { "(no title)" } else "(no title)",
                            startMs = start,
                            endMs = if (end > start) end else start + 60L * 60 * 1000,
                            location = if (locCol >= 0) cursor.getString(locCol).orEmpty() else "",
                            allDay = if (allDayCol >= 0) cursor.getInt(allDayCol) == 1 else false
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    fun addEvent(titleRaw: String, startMs: Long, durationMinutes: Int, locationRaw: String = ""): String? {
        val title = titleRaw.trim()
        if (title.isEmpty()) return "The event needs a title."
        if (startMs <= 0L) return "That date and time did not parse — use yyyy-MM-dd and HH:mm."
        val duration = durationMinutes.coerceIn(5, 24 * 60)
        if (!hasWritePermission()) return "Calendar write permission is off — allow it first."
        val calendarId = primaryCalendarId() ?: return "No calendar found on this phone to add the event to."
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title.take(120))
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, startMs + duration * 60L * 1000)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (locationRaw.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, locationRaw.trim().take(120))
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri == null) "The calendar refused the event — try again." else null
        } catch (_: SecurityException) {
            "Calendar write permission is off — allow it first."
        } catch (e: Exception) {
            "Could not add the event: ${e.message ?: "unknown error"}"
        }
    }

    private fun primaryCalendarId(): Long? {
        return try {
            var first: Long? = null
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    if (first == null) first = id
                    if (primaryCol >= 0 && cursor.getInt(primaryCol) == 1) return id
                }
            }
            first
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun calendarContext(): String {
        val events = today()
        if (events.isEmpty()) return ""
        return "TODAY IN CALENDAR:\n" + events.joinToString("\n") { "· ${lineFor(it)}" }
    }

    fun briefingLines(): List<String> {
        if (!hasReadPermission()) return listOf("Calendar permission is off — allow it in SETUP > CALENDAR to see today's events here.")
        val events = today()
        if (events.isEmpty()) return listOf("Nothing in the calendar today.")
        return events.map { "· ${lineFor(it)}" }
    }
}
