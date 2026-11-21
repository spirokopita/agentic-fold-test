package com.agentfold.app.nerv

import android.content.Context
import com.agentfold.app.AgentAccessibilityService

class AppConnector(
    private val context: Context,
    private val agentBridge: AgentBridge,
    private val magiEngine: MagiEngine,
    private val portManager: PortManager,
    private val voiceConnector: VoiceConnector
) {

    enum class LinkState { OK, RUNNING, OFFLINE, NEEDS_SETUP, ERROR }

    data class Link(val name: String, val state: LinkState, val detail: String)

    fun checkAll(): List<Link> {
        val links = mutableListOf<Link>()
        val runtimeRunning = agentBridge.isRunning()
        links.add(
            Link(
                "AGENT RUNTIME",
                if (runtimeRunning) LinkState.RUNNING else LinkState.OK,
                agentBridge.status()
            )
        )
        val svc = AgentAccessibilityService.instance
        links.add(
            when {
                svc == null -> Link("ACCESSIBILITY", LinkState.NEEDS_SETUP, "Not enabled — open Android Accessibility settings.")
                svc.controlActive -> Link("ACCESSIBILITY", LinkState.RUNNING, "TAKE CONTROL session is ON.")
                else -> Link("ACCESSIBILITY", LinkState.OK, "Service is on, TAKE CONTROL is off.")
            }
        )
        val config = agentBridge.providerConfig()
        links.add(
            if (config == null) Link("PROVIDER", LinkState.NEEDS_SETUP, "No API key configured — add one in SETUP.")
            else Link("PROVIDER", LinkState.OK, "${config.endpoint} · ${config.model}")
        )
        val enabledNodes = magiEngine.enabledNodes().size
        links.add(
            when (enabledNodes) {
                0 -> Link("MAGI", LinkState.NEEDS_SETUP, "No node switched on — switch one on in SETUP.")
                else -> Link("MAGI", LinkState.OK, "$enabledNodes of 3 nodes online.")
            }
        )
        links.add(
            if (voiceConnector.serviceActive()) Link("VOICE", LinkState.RUNNING, "Voice service is listening.")
            else Link("VOICE", LinkState.OFFLINE, "Voice service is off.")
        )
        return links
    }

    fun testProvider(onDone: (PortManager.PortCheck?) -> Unit) {
        val config = agentBridge.providerConfig()
        if (config == null) {
            onDone(null)
            return
        }
        Thread {
            val endpoint = try {
                val uri = java.net.URI(config.endpoint)
                val host = uri.host ?: run { onDone(null); return@Thread }
                val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
                PortManager.Endpoint("provider", "PROVIDER", host, port, uri.scheme ?: "https", builtin = true)
            } catch (_: Exception) {
                onDone(null)
                return@Thread
            }
            onDone(portManager.test(endpoint))
        }.start()
    }

    fun overallState(): LinkState {
        val states = checkAll().map { it.state }
        return when {
            states.any { it == LinkState.ERROR } -> LinkState.ERROR
            states.any { it == LinkState.NEEDS_SETUP } -> LinkState.NEEDS_SETUP
            states.any { it == LinkState.RUNNING } -> LinkState.RUNNING
            states.any { it == LinkState.OFFLINE } -> LinkState.OFFLINE
            else -> LinkState.OK
        }
    }
}
