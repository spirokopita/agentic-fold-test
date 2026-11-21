package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebateHistory(context: Context) {

    data class DebateRecord(
        val timeMillis: Long,
        val question: String,
        val winner: String,
        val votesYes: Int,
        val votesTotal: Int,
        val decision: String,
        val answerExcerpt: String,
        val localDemo: Boolean
    )

    companion object {
        private const val PREFS = "nerv_debate_history"
        private const val KEY_JSON = "debates_json"
        const val MAX_DEBATES = 100
        const val MAX_QUESTION_CHARS = 120
        const val MAX_EXCERPT_CHARS = 200

        fun winnerName(node: MagiEngine.NodeId?): String = when (node) {
            MagiEngine.NodeId.MELCHIOR -> "MELCHIOR·1"
            MagiEngine.NodeId.BALTHASAR -> "BALTHASAR·2"
            MagiEngine.NodeId.CASPER -> "CASPER·3"
            null -> "—"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val debates = load().toMutableList()

    fun record(question: String, result: MagiEngine.MagiResult) {
        val yes = result.votes.count { it.second }
        val total = result.votes.size
        val decision = when {
            result.votes.isEmpty() -> "NO VOTE"
            result.localDemo -> if (yes >= 2) "APPROVED $yes/$total (LOCAL DEMO)" else "DENIED $yes/$total (LOCAL DEMO)"
            yes >= 2 -> "APPROVED $yes/$total"
            else -> "DENIED $yes/$total"
        }
        debates.add(
            0,
            DebateRecord(
                timeMillis = System.currentTimeMillis(),
                question = question.trim().take(MAX_QUESTION_CHARS).ifBlank { "(no question)" },
                winner = winnerName(result.chairNode),
                votesYes = yes,
                votesTotal = total,
                decision = decision,
                answerExcerpt = result.text.trim().take(MAX_EXCERPT_CHARS),
                localDemo = result.localDemo
            )
        )
        while (debates.size > MAX_DEBATES) debates.removeAt(debates.size - 1)
        save()
    }

    fun recent(count: Int): List<DebateRecord> = debates.take(count)

    fun all(): List<DebateRecord> = debates.toList()

    fun search(queryRaw: String): List<DebateRecord> {
        val words = queryRaw.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return debates.filter { record ->
            val haystack = "${record.question} ${record.answerExcerpt} ${record.winner} ${record.decision}".lowercase(Locale.ROOT)
            words.all { haystack.contains(it) }
        }
    }

    fun count(): Int = debates.size

    fun clear() {
        debates.clear()
        save()
    }

    fun timeLabel(record: DebateRecord): String =
        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(record.timeMillis))

    fun lineFor(record: DebateRecord): String =
        "${timeLabel(record)} — ${record.question} (${record.decision}, ${record.winner})"

    private fun load(): List<DebateRecord> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val question = obj.optString("question", "")
                val winner = obj.optString("winner", "")
                val decision = obj.optString("decision", "")
                val time = obj.optLong("time", 0L)
                if (question.isBlank() || decision.isBlank() || time <= 0L) null
                else DebateRecord(
                    timeMillis = time,
                    question = question,
                    winner = winner.ifBlank { "—" },
                    votesYes = obj.optInt("votesYes", 0),
                    votesTotal = obj.optInt("votesTotal", 0),
                    decision = decision,
                    answerExcerpt = obj.optString("answerExcerpt", ""),
                    localDemo = obj.optBoolean("localDemo", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save() {
        val array = JSONArray()
        debates.forEach { record ->
            array.put(
                JSONObject()
                    .put("time", record.timeMillis)
                    .put("question", record.question)
                    .put("winner", record.winner)
                    .put("votesYes", record.votesYes)
                    .put("votesTotal", record.votesTotal)
                    .put("decision", record.decision)
                    .put("answerExcerpt", record.answerExcerpt)
                    .put("localDemo", record.localDemo)
            )
        }
        prefs.edit().putString(KEY_JSON, array.toString()).apply()
    }
}
