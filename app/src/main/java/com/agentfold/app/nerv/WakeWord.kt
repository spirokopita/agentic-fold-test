package com.agentfold.app.nerv

import android.content.Context

class WakeWord(context: Context) {

    companion object {

        val WAKE_PHRASES = listOf("hey magi", "hey maggie", "ok magi", "hey maji")
    }

    private val prefs = context.getSharedPreferences("nerv_wake_word", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    fun startsWithWakeWord(text: String): Boolean {
        val clean = text.trim().lowercase()
        return WAKE_PHRASES.any { clean == it || clean.startsWith("$it ") || clean.startsWith("$it,") || clean.startsWith("$it?") }
    }

    fun stripWakeWord(text: String): String {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        for (phrase in WAKE_PHRASES) {
            if (lower == phrase) return ""
            for (sep in listOf("$phrase ", "$phrase,", "$phrase?")) {
                if (lower.startsWith(sep)) return trimmed.substring(sep.length).trim()
            }
        }
        return trimmed
    }

    fun statusLine(listening: Boolean): String = when {
        listening -> "● LISTENING — speak now, tap STOP to end. Nothing is recorded in the background."
        enabled -> "HEY MAGI: ON — tap HEY MAGI below and say \"Hey MAGI, …\". Nothing listens until you tap."
        else -> "HEY MAGI: OFF — nothing is listening. Switch it on to use voice commands."
    }
}
