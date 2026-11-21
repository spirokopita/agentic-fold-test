package com.agentfold.app.nerv

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class NodeKeyStore(private val context: Context) {

    companion object {
        private const val TAG = "NodeKeyStore"
        private const val PREFS = "nerv_node_keys"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        fun aliasFor(node: MagiEngine.NodeId): String = "nerv_key_" + node.name.lowercase()
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keystore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun getOrCreateKey(alias: String): SecretKey {
        val ks = keystore()
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(node: MagiEngine.NodeId, apiKey: String) {
        require(apiKey.isNotBlank()) { "API key is blank" }
        val alias = aliasFor(node)
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        prefs.edit()
            .putString("${alias}_ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString("${alias}_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(node: MagiEngine.NodeId): String? {
        val alias = aliasFor(node)
        val ct = prefs.getString("${alias}_ct", null) ?: return null
        val iv = prefs.getString("${alias}_iv", null) ?: return null
        return try {
            val ks = keystore()
            val secretKey = (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE, secretKey,
                    GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
                )
            }
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt key for ${node.name}; treating as no key")
            null
        }
    }

    fun hasKey(node: MagiEngine.NodeId): Boolean =
        prefs.contains("${aliasFor(node)}_ct") && read(node) != null

    fun clear(node: MagiEngine.NodeId) {
        val alias = aliasFor(node)
        prefs.edit().remove("${alias}_ct").remove("${alias}_iv").apply()
        try {
            val ks = keystore()
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete Keystore entry $alias")
        }
    }

    fun clearAll() {
        MagiEngine.NodeId.values().forEach { clear(it) }
    }

    fun status(): Map<MagiEngine.NodeId, Boolean> =
        MagiEngine.NodeId.values().associateWith { hasKey(it) }
}
