package com.agentfold.app.nerv

import android.content.Context
import com.agentfold.app.agent.CredentialStore
import com.agentfold.app.agent.EndpointPolicy
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import org.json.JSONObject

class PortManager(private val context: Context) {

    enum class PortState { OPEN, CLOSED, UNREACHABLE, UNTESTED }

    data class Endpoint(
        val id: String,
        val label: String,
        val host: String,
        val port: Int,
        val scheme: String,
        val builtin: Boolean
    )

    data class PortCheck(
        val endpoint: Endpoint,
        val state: PortState,
        val detail: String,
        val ms: Long
    )

    private val prefs = context.getSharedPreferences("nerv_ports", Context.MODE_PRIVATE)

    fun endpoints(): List<Endpoint> {
        val list = mutableListOf(
            Endpoint("ollama", "OLLAMA (LOCAL)", "localhost", 11434, "http", builtin = true)
        )
        val config = CredentialStore(context).read()
        if (config != null) {
            try {
                val uri = URI(config.endpoint)
                val host = uri.host ?: ""
                if (host.isNotBlank()) {
                    val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
                    list.add(Endpoint("provider", "PROVIDER (${config.model})", host, port, uri.scheme ?: "https", builtin = true))
                }
            } catch (_: Exception) {

            }
        }
        list.addAll(customEndpoints())
        return list
    }

    fun customEndpoints(): List<Endpoint> {
        val raw = prefs.getString("custom", "") ?: ""
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 4) return@mapNotNull null
            val port = parts[2].toIntOrNull() ?: return@mapNotNull null
            if (port !in 1..65535) return@mapNotNull null
            Endpoint("custom_${parts[0].hashCode()}", parts[0], parts[1], port, parts[3], builtin = false)
        }
    }

    fun addCustom(label: String, host: String, port: Int, scheme: String): String? {
        if (label.isBlank() || label.length > 40) return "Label must be 1–40 characters."
        if (host.isBlank() || host.length > 120 || host.any { it.isWhitespace() }) return "Enter a host name or IP address."
        if (port !in 1..65535) return "Port must be 1–65535."
        if (scheme != "http" && scheme != "https") return "Scheme must be http or https."
        val existing = prefs.getString("custom", "") ?: ""
        val line = "$label|$host|$port|$scheme"
        prefs.edit().putString("custom", if (existing.isBlank()) line else "$existing\n$line").apply()
        return null
    }

    fun removeCustom(endpoint: Endpoint): Boolean {
        if (endpoint.builtin) return false
        val kept = customEndpoints().filter { it.id != endpoint.id }
        prefs.edit().putString("custom", kept.joinToString("\n") { "${it.label}|${it.host}|${it.port}|${it.scheme}" }).apply()
        return true
    }

    fun test(endpoint: Endpoint, timeoutMs: Int = 3000): PortCheck {
        val started = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs)
            }
            val ms = System.currentTimeMillis() - started
            PortCheck(endpoint, PortState.OPEN, "Answered in ${ms}ms", ms)
        } catch (e: java.net.ConnectException) {
            PortCheck(endpoint, PortState.CLOSED, "Connection refused — nothing listening on ${endpoint.port}", System.currentTimeMillis() - started)
        } catch (e: java.net.UnknownHostException) {
            PortCheck(endpoint, PortState.UNREACHABLE, "Unknown host: ${endpoint.host}", System.currentTimeMillis() - started)
        } catch (e: Exception) {
            PortCheck(endpoint, PortState.UNREACHABLE, "No answer (${e.javaClass.simpleName})", System.currentTimeMillis() - started)
        }
    }

    fun testAll(onDone: (List<PortCheck>) -> Unit) {
        Thread {
            val ends = endpoints()
            val results = arrayOfNulls<PortCheck>(ends.size)
            val threads = ends.mapIndexed { i, endpoint ->
                Thread { results[i] = test(endpoint) }.also { it.start() }
            }
            threads.forEach { it.join() }
            onDone(results.filterNotNull())
        }.start()
    }

    fun ollamaModels(baseUrl: String, onDone: (Result<List<String>>) -> Unit) {
        Thread {
            try {
                val base = baseUrl.trimEnd('/')
                EndpointPolicy.validate(base, "ollama")

                val root = base.removeSuffix("/v1")
                val conn = URL("$root/api/tags").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 3000
                    conn.readTimeout = 5000
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        onDone(Result.failure(IllegalStateException("Ollama answered HTTP $code — is that address an Ollama server?")))
                        return@Thread
                    }
                    val bytes = conn.inputStream.use { it.readBytes() }
                    require(bytes.size <= 256 * 1024) { "Ollama model list too large" }
                    val models = JSONObject(String(bytes, Charsets.UTF_8))
                        .optJSONArray("models")
                    val names = mutableListOf<String>()
                    if (models != null) {
                        for (i in 0 until minOf(models.length(), 50)) {
                            models.optJSONObject(i)?.optString("name", "")?.takeIf { it.isNotBlank() }?.let { names.add(it) }
                        }
                    }
                    onDone(Result.success(names))
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                onDone(Result.failure(e))
            }
        }.start()
    }
}
