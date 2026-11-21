package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OfflineMode(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nerv_offline", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    fun blocksProvider(kind: MagiEngine.ProviderKind): Boolean {
        if (!enabled) return false
        return kind == MagiEngine.ProviderKind.ANTHROPIC ||
            kind == MagiEngine.ProviderKind.OPENAI ||
            kind == MagiEngine.ProviderKind.XAI
    }

    data class OllamaCheck(

        val running: Boolean,

        val models: List<String>,

        val detail: String
    )

    fun probeOllama(): OllamaCheck {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("http://localhost:11434/api/tags").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return OllamaCheck(false, emptyList(), "NOT RUNNING — Ollama answered ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val models = mutableListOf<String>()
            val array = JSONObject(body).optJSONArray("models")
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { models.add(it) }
                }
            }
            val detail = if (models.isEmpty()) "RUNNING — no model pulled yet (pull one in Ollama first)"
                else "RUNNING — ${models.size} model${if (models.size == 1) "" else "s"}: ${models.joinToString(", ")}"
            OllamaCheck(true, models, detail)
        } catch (e: Exception) {
            OllamaCheck(false, emptyList(), "NOT RUNNING — ${shortReason(e)}")
        } finally {
            connection?.disconnect()
        }
    }

    fun summary(nodes: List<MagiEngine.NodeConfig>): String {
        if (!enabled) return "OFF — cloud providers answer as normal"
        val live = nodes.filter { it.enabled && it.provider == MagiEngine.ProviderKind.LOCAL }
        val blocked = nodes.filter { it.enabled && blocksProvider(it.provider) }
        val off = nodes.count { !it.enabled }
        return buildString {
            append("ON — LOCAL ONLY")
            if (live.isNotEmpty()) append(" · ${live.size} LOCAL node${if (live.size == 1) "" else "s"} live")
            else append(" · no LOCAL node — answers are LOCAL DEMO drafts")
            if (blocked.isNotEmpty()) append(" · ${blocked.size} cloud node${if (blocked.size == 1) "" else "s"} blocked")
            if (off > 0) append(" · $off node${if (off == 1) "" else "s"} off")
        }
    }

    private fun shortReason(e: Exception): String {
        val msg = (e.message ?: e.javaClass.simpleName).replace("\n", " ").take(80)
        return if (msg.contains("refused", ignoreCase = true) || msg.contains("failed to connect", ignoreCase = true)) {
            "Ollama is not running on this phone"
        } else msg
    }
}
