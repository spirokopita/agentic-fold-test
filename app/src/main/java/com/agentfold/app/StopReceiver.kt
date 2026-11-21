package com.agentfold.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, VoiceChatService::class.java))
        val service = AgentAccessibilityService.instance
        if (service != null) service.stopSession()
        else com.agentfold.app.agent.AgentRuntime.peek()?.cancel()
    }
}
