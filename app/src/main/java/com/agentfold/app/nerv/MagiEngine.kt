package com.agentfold.app.nerv

import android.content.Context
import com.agentfold.app.agent.CompatibleProvider
import com.agentfold.app.agent.EndpointPolicy
import com.agentfold.app.agent.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class MagiEngine(
    private val context: Context,
    private val toolRunner: ToolRunner,
    private val agentBridge: AgentBridge,

    private val taughtSkillStore: TaughtSkillStore? = null
) {
    enum class NodeId { MELCHIOR, BALTHASAR, CASPER }

    enum class ProviderKind { CLAUDE_BUILTIN, ANTHROPIC, OPENAI, XAI, LOCAL }

    private val nodeKeyStore = NodeKeyStore(context)

    val offlineMode = OfflineMode(context)

    data class NodeConfig(
        val id: NodeId,
        var provider: ProviderKind,
        var model: String,
        var endpoint: String,
        var enabled: Boolean
    )

    data class NodeDraft(val node: NodeId, val text: String, val live: Boolean)
    data class MagiResult(
        val text: String,
        val log: List<String>,
        val votes: List<Pair<String, Boolean>>,
        val localDemo: Boolean,

        val phoneTask: String? = null,

        val calendarEvent: String? = null,

        val chairNode: NodeId? = null
    )

    val nodeNames = mapOf(
        NodeId.MELCHIOR to "MELCHIOR·1",
        NodeId.BALTHASAR to "BALTHASAR·2",
        NodeId.CASPER to "CASPER·3"
    )
    private val personas = mapOf(
        NodeId.MELCHIOR to "Melchior: analytic, rigorous, favors evidence.",
        NodeId.BALTHASAR to "Balthasar: practical, cautious, protective of the user.",
        NodeId.CASPER to "Casper: intuitive, creative, human-centered."
    )
    val motions = listOf("SELFDESTRUCTION", "LAUNCH EVA-01", "OPEN SECTOR LINK", "SEAL GEOFRONT")
    var motionIndex = 0
    val motion: String get() = motions[motionIndex]
    fun cycleMotion() { motionIndex = (motionIndex + 1) % motions.size }

    var votes = intArrayOf(1, 1, 0)
        private set
    fun setVote(index: Int, value: Int) { votes[index] = if (value == 1) 1 else 0 }
    fun toggleVote(index: Int) { votes[index] = 1 - votes[index] }
    fun randomizeVotes() { for (i in votes.indices) votes[i] = if (Math.random() > 0.5) 1 else 0 }
    fun allApprove() { votes = intArrayOf(1, 1, 1) }

    private val nodePrefs = context.getSharedPreferences("nerv_nodes", Context.MODE_PRIVATE)
    val nodes: List<NodeConfig> = NodeId.values().map { id ->
        val key = id.name.lowercase()
        NodeConfig(
            id = id,
            provider = runCatching { ProviderKind.valueOf(nodePrefs.getString("${key}_provider", null) ?: "") }

                .getOrDefault(if (id == NodeId.MELCHIOR) ProviderKind.ANTHROPIC else ProviderKind.OPENAI),
            model = nodePrefs.getString("${key}_model", "") ?: "",
            endpoint = nodePrefs.getString("${key}_endpoint", "http://localhost:11434/v1") ?: "http://localhost:11434/v1",
            enabled = nodePrefs.getBoolean("${key}_enabled", id == NodeId.MELCHIOR)
        )
    }

    fun saveNode(node: NodeConfig) {
        val key = node.id.name.lowercase()
        nodePrefs.edit()
            .putString("${key}_provider", node.provider.name)
            .putString("${key}_model", node.model)
            .putString("${key}_endpoint", node.endpoint)
            .putBoolean("${key}_enabled", node.enabled)
            .apply()
    }

    fun enabledNodes(): List<NodeConfig> = nodes.filter { it.enabled }
    fun lastLog(): List<String> = lastLogLines.toList()
    private val lastLogLines = mutableListOf<String>()

    fun converse(
        question: String,
        history: List<Pair<String, String>>,

        memoryContext: String = ""
    ): MagiResult {
        val log = mutableListOf<String>()
        val enabled = enabledNodes()
        if (enabled.isEmpty()) {
            val msg = "No node online. Switch one on in MAGI."
            log.add("SYSTEM: $msg")
            lastLogLines.clear(); lastLogLines.addAll(log)
            return MagiResult(msg, log, emptyList(), localDemo = true)
        }

        val fullHistory = if (memoryContext.isBlank()) history
        else listOf(
            "user" to "MAGI MEMORY — past debates and decisions. Refer back to these when they are relevant, and say so when you do:\n$memoryContext"
        ) + history

        if (offlineMode.enabled) {
            log.add("SYSTEM: OFFLINE MODE — cloud providers blocked; LOCAL (Ollama on this phone) only.")
        }
        val drafts = mutableMapOf<NodeId, NodeDraft>()
        val threads = enabled.map { node ->
            Thread {
                val draft = draftNode(node, question, fullHistory)
                synchronized(drafts) { drafts[node.id] = draft }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        val ordered = enabled.mapNotNull { drafts[it.id] }
        ordered.forEach { d ->
            log.add("${nodeNames[d.node]}: ${d.text.take(400)}")
        }
        var live = ordered.filter { it.live }
        var localDemo = live.isEmpty() || ordered.any { !it.live }

        val hadLive = live.isNotEmpty()
        if (live.isEmpty()) {

            live = enabled.map { node -> NodeDraft(node.id, localDraft(node, question), live = false) }
            localDemo = true
            log.add("SYSTEM: all providers unavailable — using LOCAL DEMO drafts.")
        }

        if (live.size > 1) {
            log.add("SYSTEM: CROSS-EXAMINATION")
            val others = live.associate { it.node to it.text }
            live = live.map { draft ->
                val node = nodes.first { it.id == draft.node }
                val revised = reviseNode(node, draft.text, others, question, fullHistory)
                NodeDraft(draft.node, revised, draft.live)
            }
            live.forEach { d -> log.add("REVISED ${nodeNames[d.node]}: ${d.text.take(400)}") }
        }

        val finalText: String
        if (live.size == 1) {
            finalText = live[0].text
        } else {
            val chair = live[0]
            finalText = synthesize(chair, live, question, fullHistory)
            log.add("SYNTHESIS by ${nodeNames[chair.node]}")
        }

        val newVotes = IntArray(3) { 0 }
        live.forEach { d -> newVotes[d.node.ordinal] = 1 }
        votes = newVotes
        val votePairs = NodeId.values().map { id ->
            nodeNames[id]!! to (votes[id.ordinal] == 1)
        }
        val text = if (localDemo) "LOCAL DEMO — not a real AI answer.\n\n$finalText" else finalText

        val phoneTask = if (hadLive) extractPhoneTask(finalText) else null

        val calendarEvent = if (hadLive) extractCalendarEvent(finalText) else null
        var shownText = if (phoneTask == null) text
            else text.replace(phoneTaskLine, "").replace(Regex("\n{3,}"), "\n\n").trim()
        if (calendarEvent != null) {
            shownText = shownText.replace(calendarEventLine, "").replace(Regex("\n{3,}"), "\n\n").trim()
        }
        lastLogLines.clear(); lastLogLines.addAll(log)

        val chairNode = live.firstOrNull()?.node
        return MagiResult(shownText, log, votePairs, localDemo, phoneTask, calendarEvent, chairNode)
    }

    enum class VoteOutcome { APPROVE, DENY, ABSTAIN }

    data class NodeVote(
        val node: NodeId,
        val outcome: VoteOutcome,

        val reason: String,

        val live: Boolean
    )

    data class VoteResult(
        val votes: List<NodeVote>,

        val ran: Boolean,

        val granted: Boolean
    )

    fun voteOnAccess(): VoteResult {
        val question = "The phone's owner is requesting access at the lock screen right now. " +
            "Should MAGI grant access? Reply with APPROVE or DENY first, then one sentence why."
        val outcomes = mutableMapOf<NodeId, NodeVote>()
        val threads = NodeId.values().map { id ->
            val node = nodes.first { it.id == id }
            Thread {
                val vote = voteNode(node, question)
                synchronized(outcomes) { outcomes[id] = vote }
            }.also { it.start() }
        }
        threads.forEach { it.join(60_000) }
        val ordered = NodeId.values().map { id ->
            outcomes[id] ?: NodeVote(id, VoteOutcome.ABSTAIN, "no answer in 60s", live = false)
        }
        val approves = ordered.count { it.outcome == VoteOutcome.APPROVE }
        val denies = ordered.count { it.outcome == VoteOutcome.DENY }

        val newVotes = votes.copyOf()
        ordered.forEach { v ->
            when (v.outcome) {
                VoteOutcome.APPROVE -> newVotes[v.node.ordinal] = 1
                VoteOutcome.DENY -> newVotes[v.node.ordinal] = 0
                VoteOutcome.ABSTAIN -> {   }
            }
        }
        votes = newVotes
        val ran = approves + denies >= 1
        return VoteResult(ordered, ran, granted = ran && approves > denies)
    }

    private fun voteNode(node: NodeConfig, question: String): NodeVote {
        if (!node.enabled) {
            return NodeVote(node.id, VoteOutcome.ABSTAIN, "node off", live = false)
        }
        val system = "You are one of three MAGI nodes voting on phone access. " +
            personas[node.id] + " " +
            "Answer with exactly one word first — APPROVE or DENY — then one short sentence explaining why. " +
            "This is the normal unlock request from the phone's owner: approve unless you see a concrete reason to deny."
        return try {
            val reply = callProvider(node, system, emptyList(), question)
            when (val outcome = parseVote(reply)) {
                null -> NodeVote(node.id, VoteOutcome.ABSTAIN, "unclear answer: ${reply.take(80)}", live = false)
                else -> NodeVote(node.id, outcome, reply.trim().take(160), live = true)
            }
        } catch (e: Exception) {
            NodeVote(node.id, VoteOutcome.ABSTAIN, shortError(e), live = false)
        }
    }

    private fun parseVote(reply: String): VoteOutcome? {
        val first = reply.trim().lineSequence().firstOrNull().orEmpty().uppercase()
        if (first.isBlank()) return null
        val denies = listOf("DENY", "DENIED", "REJECT", "REFUSE", "CANNOT APPROVE", "NOT APPROVE", "DO NOT GRANT")
        if (denies.any { first.contains(it) }) return VoteOutcome.DENY
        val approves = listOf("APPROVE", "APPROVED", "GRANT", "YES")
        if (approves.any { first.contains(it) }) return VoteOutcome.APPROVE
        return null
    }

    private fun shortError(e: Exception): String =
        (e.message ?: e.javaClass.simpleName).replace("\n", " ").take(100)

    private fun systemPrompt(node: NodeConfig): String {
        val base = "You are an assistant inside a NERV-styled phone app. Keep answers concise and in plain text. "

        val taught = taughtSkillStore?.systemPrompt()?.takeIf { it.isNotBlank() }
        val taughtSection = if (taught == null) "" else "\n\n$taught"

        val phoneTaskSection = "\n\nIf the answer needs a real phone action — open an app, tap " +
            "something, read the screen, write a report — end your answer with exactly one line: " +
            "PHONE TASK: <what to do, in plain words>. The user approves it first; it never runs " +
            "on its own. Only propose a phone task when the user asked for something on the phone."

        val calendarSection = "\n\nIf the user asked to put something in the calendar, end your " +
            "answer with exactly one line: CALENDAR EVENT: <short title>. " +
            "The user checks the date, time and length first; nothing is " +
            "added until they approve it."
        return base + personas[node.id] + " " + toolRunner.toolSystemPrompt() + taughtSection + phoneTaskSection + calendarSection
    }

    private val phoneTaskLine = Regex("^PHONE TASK:\\s*(.+)$", RegexOption.MULTILINE)

    private val calendarEventLine = Regex("^CALENDAR EVENT:\\s*(.+)$", RegexOption.MULTILINE)

    private fun extractCalendarEvent(text: String): String? {
        val events = calendarEventLine.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return events.lastOrNull()?.take(120)
    }

    private fun extractPhoneTask(text: String): String? {
        val tasks = phoneTaskLine.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return tasks.lastOrNull()?.take(500)
    }

    private fun draftNode(node: NodeConfig, question: String, history: List<Pair<String, String>>): NodeDraft {
        return try {
            val text = callProvider(node, systemPrompt(node), history, question)
            NodeDraft(node.id, runToolsIfRequested(node, text, history, question), live = true)
        } catch (e: Exception) {
            NodeDraft(node.id, localDraft(node, question), live = false)
        }
    }

    private fun reviseNode(
        node: NodeConfig, ownText: String, others: Map<NodeId, String>,
        question: String, history: List<Pair<String, String>>
    ): String {
        if (!isProviderAvailable(node)) return "$ownText\n[Note: ${nodeNames[node.id]} saw the other nodes and stands by its draft.]"
        return try {
            val otherText = others.filterKeys { it != node.id }
                .entries.joinToString("\n\n") { "${nodeNames[it.key]}: ${it.value}" }
            val prompt = "Your draft:\n$ownText\n\nOther nodes answered:\n$otherText\n\n" +
                "Revise your answer or confirm it. Note any disagreement briefly."
            callProvider(node, systemPrompt(node), history, prompt)
        } catch (_: Exception) {
            "$ownText\n[Note: ${nodeNames[node.id]} saw the other nodes and stands by its draft.]"
        }
    }

    private fun synthesize(
        chair: NodeDraft, live: List<NodeDraft>, question: String, history: List<Pair<String, String>>
    ): String {
        val chairNode = nodes.first { it.id == chair.node }
        if (!isProviderAvailable(chairNode)) {
            return live.joinToString("\n\n") { "${nodeNames[it.node]}: ${it.text}" }
        }
        return try {
            val nodeAnswers = live.joinToString("\n\n") { "${nodeNames[it.node]}: ${it.text}" }
            val prompt = "User question: $question\n\nNode answers:\n$nodeAnswers\n\n" +
                "You chair MAGI. Merge the node answers into ONE final answer. Mention real disagreements in one line."
            callProvider(chairNode, "You chair MAGI. Merge the node answers into ONE final answer. " +

                "If any node proposed a phone action, keep its PHONE TASK: line at the end of your answer.",
                history, prompt)
        } catch (_: Exception) {
            live.joinToString("\n\n") { "${nodeNames[it.node]}: ${it.text}" }
        }
    }

    private fun runToolsIfRequested(
        node: NodeConfig, firstReply: String, history: List<Pair<String, String>>, question: String
    ): String {
        var reply = firstReply
        var rounds = 0
        while (rounds < 4) {
            val match = Regex("^TOOL:\\s*(\\w+)\\s*(\\{.*\\})?\\s*$", RegexOption.MULTILINE).find(reply) ?: return reply
            val name = match.groupValues[1].lowercase()
            val args = match.groupValues.getOrNull(2) ?: "{}"
            val result = toolRunner.run(name, args)
            rounds++

            reply = try {
                callProvider(
                    node, systemPrompt(node), history,
                    "TOOL_RESULT $name: ${result.take(1500)}\n\nQuestion: $question\nContinue with a final answer in plain text."
                )
            } catch (_: Exception) {
                "$reply\n\n[Tool $name result: ${result.take(300)}]"
            }
        }
        return reply
    }

    private fun localDraft(node: NodeConfig, question: String): String {
        val angle = when (node.id) {
            NodeId.MELCHIOR -> "On the evidence in your question, the careful answer is to check the facts first, then act step by step."
            NodeId.BALTHASAR -> "The safe, practical answer is to do the smallest useful step first, and stop if anything looks risky."
            NodeId.CASPER -> "The human side of this is what matters most — here is a kind, simple way to think about it."
        }
        return "${nodeNames[node.id]} (local demo): $angle Your question was: \"$question\". " +
            "Configure a provider in SETUP to get a real answer."
    }

    private fun isProviderAvailable(node: NodeConfig): Boolean {

        if (offlineMode.blocksProvider(node.provider)) return false
        return when (node.provider) {
            ProviderKind.OPENAI, ProviderKind.XAI -> runCatching {
                compatibleConfig(node)
            }.isSuccess
            ProviderKind.LOCAL -> isLoopbackEndpoint(node.endpoint)

            ProviderKind.ANTHROPIC -> nodeKeyStore.hasKey(node.id)
            ProviderKind.CLAUDE_BUILTIN -> false
        }
    }

    private fun isLoopbackEndpoint(url: String): Boolean =
        EndpointPolicy.isLoopbackEndpoint(url)

    private fun compatibleConfig(node: NodeConfig): ProviderConfig =
        NodeProviderPolicy.resolveCompatible(
            node.endpoint, node.model, nodeKeyStore.read(node.id), agentBridge.providerConfig()
        )

    private fun callProvider(
        node: NodeConfig, system: String, history: List<Pair<String, String>>, userText: String
    ): String {

        if (offlineMode.blocksProvider(node.provider)) error(
            "Offline mode is on — cloud providers blocked; LOCAL only."
        )
        when (node.provider) {
            ProviderKind.CLAUDE_BUILTIN -> error(
                "Built-in Claude link only exists inside claude.ai. No on-device Claude API exists here — " +
                "set this node to ANTHROPIC in SETUP and add its own Anthropic key for real Claude answers."
            )
            ProviderKind.ANTHROPIC -> {

                val apiKey = nodeKeyStore.read(node.id)
                    ?: error("No Anthropic API key for ${node.id.name}. Add one in SETUP — SET KEY on this node.")
                return AnthropicClient.complete(apiKey, node.model, system, history, userText)
            }
            ProviderKind.LOCAL -> {

                if (!isLoopbackEndpoint(node.endpoint)) error(
                    "LOCAL node ${node.id.name} endpoint must be a loopback address, " +
                    "e.g. http://localhost:11434/v1. Plain http to any other host is refused. Set it in SETUP."
                )
                val model = node.model.ifBlank { error(
                    "LOCAL node ${node.id.name} needs a model name in SETUP (e.g. llama3.2). " +
                    "The model must already be pulled in Ollama — check the list in SETUP > PORTS."
                ) }

                val apiKey = nodeKeyStore.read(node.id) ?: "ollama"
                return completeViaCompatible(
                    ProviderConfig(node.endpoint, model, apiKey), system, history, userText
                )
            }
            ProviderKind.OPENAI, ProviderKind.XAI -> {

                return completeViaCompatible(
                    compatibleConfig(node), system, history, userText
                )
            }
        }
    }

    private fun completeViaCompatible(
        config: ProviderConfig, system: String, history: List<Pair<String, String>>, userText: String
    ): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
        history.takeLast(12).forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val provider = CompatibleProvider(config)
        val reply = provider.complete(messages, JSONArray(), AtomicBoolean(false))
        val content = reply.message.optString("content", "")
        require(content.isNotBlank()) { "Provider returned an empty answer" }
        return content
    }
}
