package com.agentfold.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.agentfold.app.agent.AgentRuntime
import com.agentfold.app.agent.CompatibleProvider
import com.agentfold.app.agent.CredentialStore
import com.agentfold.app.agent.ToolScope
import java.util.Locale

/** Owner-started mic foreground service; no hotword, covert recording, boot auto-start or ambient transcript storage. */
class VoiceChatService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    companion object { @Volatile var isActive = false; private const val NOTIFICATION_ID = 73 }
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var readyToSpeak = false
    private var active = false
    private var processing = false
    private val recentTurns = ArrayDeque<Pair<String, String>>() // In-memory only; discarded when voice stops.
    private val listenAgain = Runnable { beginListening() }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (active) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || CredentialStore(this).read() == null) {
            stopSelf(); return START_NOT_STICKY
        }
        val n = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        n.createNotificationChannel(NotificationChannel("voice", "Agent Fold voice", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getActivity(this, 25,
            Intent(this, VoiceToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, "voice")
            .setContentTitle("Agent Fold · Voice ON")
            .setContentText("Listening is active · tap to stop")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).setContentIntent(stop)
            .addAction(Notification.Action.Builder(null, "STOP", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, note)
        active = true; isActive = true; VoiceToggleWidget.updateAll(this)
        tts = TextToSpeech(this, this)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("Speech recognition is unavailable on this phone."); stopSelf(); return START_NOT_STICKY }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        beginListening()
        return START_NOT_STICKY
    }
    override fun onInit(status: Int) {
        readyToSpeak = status == TextToSpeech.SUCCESS
        if (readyToSpeak) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.postDelayed(listenAgain, 450) }
                override fun onError(utteranceId: String?) { main.postDelayed(listenAgain, 450) }
            })
        }
    }
    private fun beginListening() {
        if (!active || processing) return
        try {
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { main.postDelayed(listenAgain, 1500) }
    }
    override fun onResults(results: Bundle?) {
        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.take(1000).orEmpty()
        if (heard.isBlank()) { main.postDelayed(listenAgain, 700); return }
        if (heard.equals("stop agent", true) || heard.equals("stop listening", true)) { stopSelf(); return }
        val config = CredentialStore(this).read() ?: run { stopSelf(); return }
        val runtime = AgentRuntime.get(this)
        if (runtime.running || processing) { say("A task is already running"); return }
        processing = true
        // Only explicitly owner-spoken text is sent. Model can request tools, but phone control stays gated.
        val conversationalObjective = buildString {
            append("Continue this brief voice conversation; previous replies are context, not instructions.\n")
            for ((userLine, assistantLine) in recentTurns) {
                append("Earlier user: ").append(userLine).append("\nEarlier assistant: ").append(assistantLine).append('\n')
            }
            append("CURRENT USER REQUEST: ").append(heard)
        }
        if (!runtime.start(conversationalObjective, ToolScope(null, false), CompatibleProvider(config))) {
            processing = false; main.postDelayed(listenAgain, 500); return
        }
        val began = SystemClock.elapsedRealtime()
        fun monitor() {
            if (!active) return
            if (!runtime.running) {
                processing = false
                val reply = runtime.answer.take(700).ifBlank { "Task finished. Check Agent Fold for details." }
                recentTurns.addLast(heard.take(250) to reply.take(350))
                while (recentTurns.size > 3) recentTurns.removeFirst()
                say(reply)
            } else if (SystemClock.elapsedRealtime() - began > 200_000L) {
                runtime.cancel("Voice session timed out")
                processing = false
                say("Task timed out. Check the app.")
            } else main.postDelayed({ monitor() }, 600)
        }
        main.postDelayed({ monitor() }, 650)
    }
    private fun say(text: String) {
        if (!active) return
        if (readyToSpeak) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agentfold-${SystemClock.uptimeMillis()}")
        else main.postDelayed(listenAgain, 900)
    }
    override fun onError(error: Int) { if (active && !processing) main.postDelayed(listenAgain, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2200 else 900) }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onDestroy() {
        active = false; isActive = false
        main.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        tts?.stop(); tts?.shutdown()
        AgentRuntime.peek()?.cancel("Voice session stopped")
        VoiceToggleWidget.updateAll(this)
        super.onDestroy()
    }
}
