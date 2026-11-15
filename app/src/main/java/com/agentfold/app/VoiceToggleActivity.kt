package com.agentfold.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.agentfold.app.agent.CredentialStore

/** Widget launches a visible Activity first so the microphone service is started with user intent. */
class VoiceToggleActivity : Activity() {
    companion object { private const val MIC_REQUEST = 311 }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VoiceChatService.isActive) {
            stopService(Intent(this, VoiceChatService::class.java))
            finish()
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
        } else confirmStart()
    }
    private fun confirmStart() {
        if (CredentialStore(this).read() == null) {
            Toast.makeText(this, "Set up an AI provider in Agent Fold first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Start voice chat?")
            .setMessage("While enabled, Agent Fold listens for speech using the device speech-recognition provider, then sends recognised words to your configured AI provider. It can speak replies. Android shows a microphone indicator and a STOP notification. Phone-control tools require a separate five-minute TAKE CONTROL session and approval for every action. During a separately approved TAKE CONTROL session, the AI may invoke screen observation tools and transmit some visible app labels to your configured model provider.\n\nTap the widget again to stop.")
            .setNegativeButton("Not now") { _, _ -> finish() }
            .setPositiveButton("Start voice") { _, _ ->
                try { startForegroundService(Intent(this, VoiceChatService::class.java)) }
                catch (_: Exception) { Toast.makeText(this, "Android blocked the microphone service", Toast.LENGTH_LONG).show() }
                finish()
            }.setOnCancelListener { finish() }.show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) confirmStart()
        else finish()
    }
}
