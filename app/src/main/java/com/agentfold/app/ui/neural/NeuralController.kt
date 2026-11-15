package com.agentfold.app.ui.neural

import java.util.concurrent.CopyOnWriteArraySet

enum class VisualState(val label: String) {
    THINKING_FIELD("thinking field"), INTERNAL_THINKING("internal thinking"),
    EXTERNAL_EXPLORATION("external exploration"), PEACEFUL_COMMUNICATION("peaceful communication"),
    CONFLICT("conflict"), LOCAL_INFECTED("our ai infected"), PEER_TAKEN_OVER("other ai taken over")
}
enum class OrbTarget { LOCAL, PEER }
enum class PeerConnection { DISCONNECTED, PEACEFUL, CONTESTED }

/** Endpoints are supplied by an authorised integration, never inferred from installed packages. */
data class ApplicationEndpoint(
    val id: String,
    val label: String,
    val available: Boolean,
    val connected: Boolean,
    val launchable: Boolean = available
)
/** The integration owns the metric's meaning. Null means unavailable, not zero security. */
data class FirewallMetric(val label: String = "unspecified", val percent: Float? = null, val simulated: Boolean = false)
data class NeuralSnapshot(
    val state: VisualState = VisualState.THINKING_FIELD,
    val activity: Float = 0f,
    val applications: List<ApplicationEndpoint> = emptyList(),
    val peer: PeerConnection = PeerConnection.DISCONNECTED,
    val balance: Float = .5f,
    val localFirewall: FirewallMetric = FirewallMetric(),
    val peerFirewall: FirewallMetric = FirewallMetric(),
    val localInfection: Float = 0f,
    val peerInfection: Float = 0f,
    val preview: Boolean = false
)

/** UI-only, thread-safe signal boundary. Contains no network, permissions or reasoning code. */
class NeuralController {
    @Volatile var snapshot = NeuralSnapshot()
        private set
    private val listeners = CopyOnWriteArraySet<(NeuralSnapshot) -> Unit>()
    fun subscribe(listener: (NeuralSnapshot) -> Unit) { listeners.add(listener); listener(snapshot) }
    fun unsubscribe(listener: (NeuralSnapshot) -> Unit) { listeners.remove(listener) }
    @Synchronized private fun update(transform: (NeuralSnapshot) -> NeuralSnapshot) {
        snapshot = transform(snapshot)
        listeners.forEach { it(snapshot) }
    }
    fun setVisualState(state: VisualState) = update { it.copy(state = state) }
    fun setThoughtActivity(level: Float) = update { it.copy(activity = unit(level)) }
    fun setConnectedApplications(applications: List<ApplicationEndpoint>) = update {
        it.copy(applications = applications.distinctBy { app -> app.id }.take(12).map { app ->
            app.copy(connected = app.connected && app.available && app.launchable)
        })
    }
    fun setPeerAIConnection(connection: PeerConnection) = update { it.copy(peer = connection) }
    fun setConnectionColourBalance(balance: Float) = update { it.copy(balance = unit(balance)) }
    fun setFirewallMetrics(local: FirewallMetric, remote: FirewallMetric) = update {
        fun clean(m: FirewallMetric) = m.copy(percent = m.percent?.takeIf { p -> p.isFinite() }?.coerceIn(0f, 100f))
        it.copy(localFirewall = clean(local), peerFirewall = clean(remote))
    }
    fun setInfectionVisualState(target: OrbTarget, intensity: Float) = update {
        if (target == OrbTarget.LOCAL) it.copy(localInfection = unit(intensity))
        else it.copy(peerInfection = unit(intensity))
    }
    /** Development data never reaches a service or grants authority. */
    fun preview(state: VisualState) = update {
        NeuralSnapshot(state = state, activity = .7f, preview = true,
            peer = if (state.ordinal >= 3) PeerConnection.PEACEFUL else PeerConnection.DISCONNECTED,
            localFirewall = FirewallMetric("demo metric", 72f, true),
            peerFirewall = FirewallMetric("demo metric", 61f, true),
            localInfection = if (state == VisualState.LOCAL_INFECTED) .65f else 0f,
            peerInfection = if (state == VisualState.PEER_TAKEN_OVER) .65f else 0f,
            applications = listOf(
                ApplicationEndpoint("sim-notes", "sim notes", true, true),
                ApplicationEndpoint("sim-camera", "sim camera", true, false),
                ApplicationEndpoint("sim-files", "sim files", true, true),
                ApplicationEndpoint("sim-maps", "sim maps", true, false),
                ApplicationEndpoint("sim-locked", "sim locked", true, false, launchable = false),
                ApplicationEndpoint("sim-absent", "sim absent", false, false, launchable = false),
            ))
    }
    fun endPreview() = update { NeuralSnapshot() }
    companion object { private fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f }
}
