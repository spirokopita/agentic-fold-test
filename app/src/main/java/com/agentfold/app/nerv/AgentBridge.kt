package com.agentfold.app.nerv

import android.content.Context
import com.agentfold.app.AgentAccessibilityService
import com.agentfold.app.agent.AgentRuntime
import com.agentfold.app.agent.CompatibleProvider
import com.agentfold.app.agent.CredentialStore
import com.agentfold.app.agent.ProviderConfig
import com.agentfold.app.agent.ToolScope

class AgentBridge(private val context: Context) {

    enum class TaskRequest { NEEDS_PROVIDER, ALREADY_RUNNING, NEEDS_APPROVAL, STARTED }

    private val runtime get() = AgentRuntime.get(context)

    fun providerConfig(): ProviderConfig? = CredentialStore(context).read()
    fun providerConfigured(): Boolean = providerConfig() != null
    fun providerLabel(): String {
        val config = providerConfig() ?: return "Not configured"
        return "${config.endpoint} · ${config.model}"
    }

    fun saveProvider(config: ProviderConfig) = CredentialStore(context).save(config)

    fun forgetProvider() {
        runtime.cancel("Provider revoked")
        CredentialStore(context).clear()
    }

    fun requestTask(objective: String): TaskRequest {
        if (runtime.running) return TaskRequest.ALREADY_RUNNING
        if (objective.isBlank() || objective.length > 8000) return TaskRequest.NEEDS_APPROVAL
        if (providerConfig() == null) return TaskRequest.NEEDS_PROVIDER
        return TaskRequest.NEEDS_APPROVAL
    }

    fun startApprovedTask(objective: String): Boolean {
        val config = providerConfig() ?: return false
        if (runtime.running) return false
        return runtime.start(objective, ToolScope(null, false), CompatibleProvider(config))
    }

    fun stop(reason: String = "STOP") = runtime.cancel(reason)
    fun isRunning(): Boolean = runtime.running
    fun status(): String = runtime.status
    fun answer(): String = runtime.answer
    fun history(): String = runtime.history()

    fun startTakeControl(): String {
        val svc = AgentAccessibilityService.instance
            ?: return "Enable Agent Fold in Android Accessibility settings first."
        svc.startSession()
        return "TAKE CONTROL on — 5 minutes. Every tap, scroll or typing action still needs your approval. Press STOP at any time."
    }

    fun stopTakeControl(reason: String = "STOP: device control revoked") {
        AgentAccessibilityService.instance?.stopSession(reason)
    }

    fun controlActive(): Boolean = AgentAccessibilityService.instance?.controlActive == true

    fun observeOnce(): String =
        AgentAccessibilityService.instance?.observeOnce() ?: "Enable accessibility first"
}
