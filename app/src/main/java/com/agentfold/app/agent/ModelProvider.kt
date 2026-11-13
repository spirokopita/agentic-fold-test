package com.agentfold.app.agent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class ModelReply(val message: JSONObject, val tokens: Int)
interface ModelProvider {
    fun complete(messages: JSONArray, tools: JSONArray, cancelled: AtomicBoolean): ModelReply
    fun cancel()
}

/** Configurable OpenAI-compatible adapter. No redirects, raw error-body logs or embedded credentials. */
class CompatibleProvider(private val config: ProviderConfig) : ModelProvider {
    @Volatile private var connection: HttpURLConnection? = null
    override fun cancel() { connection?.disconnect() }
    override fun complete(messages: JSONArray, tools: JSONArray, cancelled: AtomicBoolean): ModelReply {
        EndpointPolicy.validate(config.endpoint, config.model)
        if (cancelled.get()) throw CancellationException()
        val request = JSONObject().put("model", config.model).put("messages", messages).put("tools", tools)
            .put("tool_choice", "auto").put("parallel_tool_calls", false).put("max_completion_tokens", 2048)
        val conn = URL(config.endpoint.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        connection = conn
        try {
            conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000; conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            if (cancelled.get()) throw CancellationException()
            conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("Provider HTTP $code; check endpoint, model, account or quota")
            val bytes = conn.inputStream.use { input -> input.readBytesBounded(256 * 1024) }
            if (cancelled.get()) throw CancellationException()
            val result = JSONObject(String(bytes, Charsets.UTF_8))
            val choice = result.getJSONArray("choices").getJSONObject(0)
            require(choice.optString("finish_reason") in listOf("stop", "tool_calls")) { "Provider truncated or refused response" }
            return ModelReply(choice.getJSONObject("message"), result.optJSONObject("usage")?.optInt("total_tokens", 0) ?: 0)
        } finally { conn.disconnect(); connection = null }
    }
}
internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(output.size() + n <= limit) { "Content exceeds safe size limit" }
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}
