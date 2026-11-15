package com.agentfold.app.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProviderConfig(val endpoint: String, val model: String, val apiKey: String)

object EndpointPolicy {
    fun validate(endpoint: String, model: String) {
        val uri = URI(endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Use an HTTPS API base URL" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "URL cannot contain credentials, query or fragment" }
        require(model.matches(Regex("[a-zA-Z0-9._:/-]{1,120}"))) { "Enter the provider's model ID" }
    }
}

/** User-owned API key encrypted with a non-exportable Android Keystore key. */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("provider", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("agentfold_provider", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("agentfold_provider", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(config: ProviderConfig) {
        EndpointPolicy.validate(config.endpoint, config.model)
        require(config.apiKey.length in 1..4096 && !config.apiKey.any { it == '\n' || it == '\r' }) { "Enter a valid API key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(config.apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("endpoint", config.endpoint.trimEnd('/')).putString("model", config.model)
            .putString("key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }
    fun read(): ProviderConfig? {
        val encrypted = prefs.getString("key", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)))
            }
            ProviderConfig(prefs.getString("endpoint", "")!!, prefs.getString("model", "")!!,
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) { null }
    }
    fun modelName(): String = prefs.getString("model", "unconfigured") ?: "unconfigured"
    fun clear() {
        prefs.edit().clear().apply()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("agentfold_provider") }
    }
}
