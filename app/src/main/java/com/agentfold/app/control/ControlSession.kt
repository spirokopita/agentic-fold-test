package com.agentfold.app.control

/** Platform-independent, fail-closed authorization. Never persisted or supplied by a model. */
data class WindowFrame(
    val id: Int, val packageName: String, val left: Int, val top: Int,
    val right: Int, val bottom: Int, val displayId: Int = 0
) {
    val width get() = right - left
    val height get() = bottom - top
    fun point(xPercent: Int, yPercent: Int): Pair<Float, Float>? {
        if (width <= 0 || height <= 0 || xPercent !in 1..99 || yPercent !in 1..99) return null
        return (left + width * xPercent / 100f) to (top + height * yPercent / 100f)
    }
}

data class ActionTicket(val generation: Long, val sequence: Long, val frame: WindowFrame, val deadline: Long)

class ControlSession(private val now: () -> Long) {
    companion object { const val SESSION_MS = 5 * 60_000L; const val APPROVAL_MS = 10_000L }
    private var generation = 0L
    private var sequence = 0L
    private var expiresAt = 0L
    private var pending: ActionTicket? = null
    private var dispatched = false
    val active: Boolean get() = expiresAt > now()
    val busy: Boolean get() = pending != null
    val remainingSeconds: Long get() = ((expiresAt - now()).coerceAtLeast(0) + 999) / 1000

    fun start() { stop(); expiresAt = now() + SESSION_MS }
    fun stop() { generation++; expiresAt = 0; pending = null; dispatched = false }
    fun prepare(frame: WindowFrame): ActionTicket? {
        if (!active || busy || frame.displayId != 0 || frame.width <= 0 || frame.height <= 0 || frame.packageName.isBlank()) return null
        return ActionTicket(generation, ++sequence, frame, now() + APPROVAL_MS).also { pending = it }
    }
    /** Called immediately before dispatch. Approval is single-use and tied to the observed window. */
    fun consume(ticket: ActionTicket, current: WindowFrame): Boolean {
        if (dispatched || pending != ticket || ticket.generation != generation) return false
        if (!active ||
            now() >= ticket.deadline || current != ticket.frame) {
            return false
        }
        dispatched = true
        return true
    }
    fun isCurrent(ticket: ActionTicket) = active && pending == ticket && ticket.generation == generation
    fun finish(ticket: ActionTicket): Boolean {
        if (pending != ticket || ticket.generation != generation) return false
        pending = null; dispatched = false
        return true
    }
}
