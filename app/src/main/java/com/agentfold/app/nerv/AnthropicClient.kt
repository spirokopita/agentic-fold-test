package com.agentfold.app.nerv

import com.agentfold.app.agent.EndpointPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AnthropicClient {
    const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    const val DEFAULT_MODEL = "claude-sonnet-5-5"

    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val MAX_TOKENS = 1500
    private const val MAX_BODY_BYTES = 256 * 1024

    fun complete(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<String, String>>,
        userText: String
    ): String {
        val useModel = model.ifBlank { DEFAULT_MODEL }
        EndpointPolicy.validate(ENDPOINT, useModel)
        require(apiKey.isNotBlank()) { "No Anthropic API key set for this node. Add one in SETUP." }
        val messages = JSONArray()

        val merged = mutableListOf<Pair<String, String>>()
        history.takeLast(12).forEach { (role, text) ->
            if (merged.isNotEmpty() && merged.last().first == role) {
                merged[merged.lastIndex] = role to (merged.last().second + "\n" + text)
            } else {
                merged.add(role to text)
            }
        }
        while (merged.isNotEmpty() && merged.first().first != "user") merged.removeAt(0)
        merged.forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))
        val request = JSONObject()
            .put("model", useModel)
            .put("max_tokens", MAX_TOKENS)
            .put("system", system)
            .put("messages", messages)
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            require(bytes.size <= MAX_BODY_BYTES) { "Anthropic reply too large" }
            val body = JSONObject(String(bytes, Charsets.UTF_8))
            if (code !in 200..299) {
                val detail = body.optJSONObject("error")?.optString("message", "") ?: ""
                throw IllegalStateException(
                    "Anthropic HTTP $code" +
                        if (detail.isNotBlank()) ": $detail" else " — check API key, model, account or quota"
                )
            }
            val content = body.optJSONArray("content") ?: JSONArray()
            val text = (0 until content.length())
                .mapNotNull { content.optJSONObject(it)?.optString("text", "") }
                .joinToString("")
                .trim()
            require(text.isNotBlank()) { "Anthropic returned an empty answer" }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
