package com.agentfold.app.nerv

import android.content.Context

class SmartReplies(context: Context) {

    private val prefs = context.getSharedPreferences("nerv_smart_replies", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    companion object {

        fun suggestionsFor(replyRaw: String): List<String> {
            val reply = replyRaw.trim()
            if (reply.isEmpty()) return emptyList()
            val lower = reply.lowercase()
            val isQuestion = reply.endsWith("?") || lower.contains("?")
            val proposes = lower.contains("approve") || lower.contains("shall i") ||
                lower.contains("should i") || lower.contains("want me to") ||
                lower.contains("phone task")
            return when {
                proposes && isQuestion -> listOf("Approve", "Deny", "Ask MAGI why")
                proposes -> listOf("Approve", "Deny", "Tell me more")
                isQuestion -> listOf("Yes", "No", "Tell me more")
                lower.contains("finished") || lower.contains(" done") || lower.startsWith("done") ->
                    listOf("Thanks", "What next?", "Show me")
                lower.contains("timer") -> listOf("Thanks", "Start another timer", "What else?")
                else -> listOf("Tell me more", "Thanks", "What else can you do?")
            }
        }
    }
}
