package com.agentfold.app.nerv

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class MagiVoice(private val context: Context) {

    companion object {
        private const val PREFS = "nerv_magi_voice"
        private const val KEY_ENABLED = "voice_replies_on"
        private const val MAX_SPEAK_CHARS = 700

        fun pitchFor(node: MagiEngine.NodeId): Float = when (node) {
            MagiEngine.NodeId.MELCHIOR -> 0.85f
            MagiEngine.NodeId.BALTHASAR -> 1.00f
            MagiEngine.NodeId.CASPER -> 1.25f
        }

        fun rateFor(node: MagiEngine.NodeId): Float = when (node) {
            MagiEngine.NodeId.MELCHIOR -> 0.90f
            MagiEngine.NodeId.BALTHASAR -> 1.00f
            MagiEngine.NodeId.CASPER -> 1.08f
        }

        fun voiceLabel(node: MagiEngine.NodeId): String = when (node) {
            MagiEngine.NodeId.MELCHIOR -> "LOW + SLOW"
            MagiEngine.NodeId.BALTHASAR -> "PLAIN"
            MagiEngine.NodeId.CASPER -> "HIGH + QUICK"
        }

        fun sampleLine(node: MagiEngine.NodeId): String = when (node) {
            MagiEngine.NodeId.MELCHIOR -> "Melchior, node one. Analytic and rigorous — I check the evidence first."
            MagiEngine.NodeId.BALTHASAR -> "Balthasar, node two. Practical and cautious — I do the smallest safe step first."
            MagiEngine.NodeId.CASPER -> "Casper, node three. Intuitive and human — I look at the human side first."
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val pending = mutableListOf<Triple<MagiEngine.NodeId, String, Boolean>>()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (!value) stop()
        }

    fun speak(node: MagiEngine.NodeId, text: String, flush: Boolean = true) {
        val clean = sanitize(text)
        if (clean.isBlank()) return
        if (tts == null) {
            pending.add(Triple(node, clean, flush))
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    val queued = pending.toList()
                    pending.clear()
                    queued.forEach { (queuedNode, queuedText, queuedFlush) ->
                        speakNow(queuedNode, queuedText, queuedFlush)
                    }
                } else {
                    pending.clear()
                }
            }
        } else if (ttsReady) {
            speakNow(node, clean, flush)
        } else {
            pending.add(Triple(node, clean, flush))
        }
    }

    fun testVoice(node: MagiEngine.NodeId) {
        speak(node, sampleLine(node))
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
    }

    private fun speakNow(node: MagiEngine.NodeId, text: String, flush: Boolean) {
        try {
            tts?.setPitch(pitchFor(node))
            tts?.setSpeechRate(rateFor(node))
            tts?.language = Locale.getDefault()
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, mode, null, "nerv-magi-${node.name}-${System.currentTimeMillis()}")
        } catch (_: Exception) {

        }
    }

    private fun sanitize(text: String): String {
        val kept = text.lines().filter { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase(Locale.getDefault())
            !trimmed.startsWith("PHONE TASK:") &&
                !trimmed.startsWith("CALENDAR EVENT:") &&
                !lower.contains("api key") &&
                !lower.contains("pin:") &&
                !lower.contains("pin code")
        }
        return kept.joinToString("\n").trim().take(MAX_SPEAK_CHARS)
    }
}
