package com.agentfold.app.nerv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.agentfold.app.VoiceChatService
import com.agentfold.app.agent.CredentialStore
import java.util.Locale

class VoiceConnector(private val context: Context) {

    enum class VoiceState { OFF, SERVICE_RUNNING, LISTENING, SPEAKING, UNAVAILABLE }

    @Volatile
    var state: VoiceState = VoiceState.OFF
        private set

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false

    fun hasMicPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun providerConfigured(): Boolean = CredentialStore(context).read() != null

    fun serviceActive(): Boolean = VoiceChatService.isActive

    fun serviceBlockReason(): String? {
        if (!hasMicPermission()) return "Microphone permission is off — allow it in Android Settings first."
        if (!providerConfigured()) return "No provider configured — add your API key in SETUP first."
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return "Speech recognition is not available on this phone."
        return null
    }

    fun startService(): String {
        serviceBlockReason()?.let { return it }
        return try {
            context.startForegroundService(Intent(context, VoiceChatService::class.java))
            state = VoiceState.SERVICE_RUNNING
            "Voice service starting — it listens, runs the task, and speaks the reply. Say \"stop listening\" to stop it."
        } catch (e: Exception) {
            state = VoiceState.OFF
            "Could not start the voice service: ${e.message ?: "unknown error"}"
        }
    }

    fun stopService(): String {
        return try {
            context.stopService(Intent(context, VoiceChatService::class.java))
            state = VoiceState.OFF
            "Voice service stopped."
        } catch (e: Exception) {
            "Could not stop the voice service: ${e.message ?: "unknown error"}"
        }
    }

    fun listenOnce(onHeard: (String) -> Unit, onError: (String) -> Unit) {
        if (listening) {
            onError("Already listening.")
            return
        }
        if (!hasMicPermission()) {
            onError("Microphone permission is off — allow it in Android Settings first.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this phone.")
            return
        }
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { this.recognizer = it }
            listening = true
            state = VoiceState.LISTENING
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()?.take(1000).orEmpty()
                    finishListening()
                    if (heard.isBlank()) onError("Did not hear anything — try again.") else onHeard(heard)
                }

                override fun onError(error: Int) {
                    finishListening()
                    onError("Could not hear that (error $error) — try again.")
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (e: Exception) {
            finishListening()
            onError("Could not start listening: ${e.message ?: "unknown error"}")
        }
    }

    fun stopListening() {
        finishListening()
    }

    private fun finishListening() {
        listening = false
        if (state == VoiceState.LISTENING) state = if (serviceActive()) VoiceState.SERVICE_RUNNING else VoiceState.OFF
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    fun speak(text: String) {
        val clean = text.take(700).trim()
        if (clean.isBlank()) return
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    speakNow(clean)
                }
            }
        } else if (ttsReady) {
            speakNow(clean)
        }
    }

    private fun speakNow(text: String) {
        state = VoiceState.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nerv-${System.currentTimeMillis()}")
        main.postDelayed({ if (state == VoiceState.SPEAKING) state = if (serviceActive()) VoiceState.SERVICE_RUNNING else VoiceState.OFF }, 5000)
    }

    fun release() {
        finishListening()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
    }
}
