package com.agentfold.app.nerv

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom

class LockSecurity(private val context: Context) {

    companion object {
        private const val TAG = "LockSecurity"
        private const val PREFS = "nerv_lock"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        const val MAX_FAILURES = 5
        const val LOCKOUT_SECONDS = 30L
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_len"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        fun looksLikeRawSecret(keyName: String): Boolean {
            val lower = keyName.lowercase()
            if (lower.endsWith("_ct") || lower.endsWith("_iv") ||
                lower.endsWith("_hash") || lower.endsWith("_salt")
            ) return false
            return lower.contains("api_key") || lower.contains("apikey") ||
                lower.contains("password") || lower.contains("passwd") ||
                lower.contains("token") || lower.contains("secret") ||
                lower.contains("private_key") || lower == "key"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class BiometricStatus {
        AVAILABLE, NO_HARDWARE, NONE_ENROLLED, UNAVAILABLE, UNSUPPORTED
    }

    fun hasPin(): Boolean =
        prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)

    fun pinLength(): Int = if (hasPin()) prefs.getInt(KEY_PIN_LENGTH, 0) else 0

    fun setPin(pin: String) {
        require(pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) { "PIN must be $MIN_PIN_LENGTH–$MAX_PIN_LENGTH digits" }
        require(pin.all { it.isDigit() }) { "PIN must be digits only" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun verify(pin: String): Boolean {
        if (!hasPin()) return false
        if (isLockedOut()) return false
        val salt = try {
            Base64.decode(prefs.getString(KEY_PIN_SALT, null) ?: return false, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val expected = try {
            Base64.decode(prefs.getString(KEY_PIN_HASH, null) ?: return false, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val ok = MessageDigest.isEqual(expected, hashPin(pin, salt))
        if (ok) resetFailures() else recordFailure()
        return ok
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).remove(KEY_PIN_LENGTH)
            .remove(KEY_FAIL_COUNT).remove(KEY_LOCKOUT_UNTIL)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    fun failuresLeft(): Int = (MAX_FAILURES - prefs.getInt(KEY_FAIL_COUNT, 0)).coerceAtLeast(0)

    fun isLockedOut(): Boolean = System.currentTimeMillis() < prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    fun lockoutSecondsLeft(): Long {
        val left = (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()) / 1000L
        return left.coerceAtLeast(0L)
    }

    private fun recordFailure() {
        val count = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAIL_COUNT, count)
        if (count >= MAX_FAILURES) {
            edit.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_SECONDS * 1000L)
        }
        edit.apply()
    }

    private fun resetFailures() {
        prefs.edit().putInt(KEY_FAIL_COUNT, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
    }

    fun biometricStatus(): BiometricStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return BiometricStatus.UNSUPPORTED
        return try {
            val manager = context.getSystemService(BiometricManager::class.java)
                ?: return BiometricStatus.UNAVAILABLE
            when (manager.canAuthenticate()) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
                else -> BiometricStatus.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Biometric check failed")
            BiometricStatus.UNAVAILABLE
        }
    }

    fun biometricStatusText(): String = when (biometricStatus()) {
        BiometricStatus.AVAILABLE -> "FINGERPRINT: ready"
        BiometricStatus.NO_HARDWARE -> "FINGERPRINT: this phone has no fingerprint sensor"
        BiometricStatus.NONE_ENROLLED -> "FINGERPRINT: none enrolled — add one in the phone's Settings"
        BiometricStatus.UNAVAILABLE -> "FINGERPRINT: unavailable right now"
        BiometricStatus.UNSUPPORTED -> "FINGERPRINT: needs Android 10 or newer"
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply() }

    fun biometricOffered(): Boolean =
        hasPin() && biometricEnabled && biometricStatus() == BiometricStatus.AVAILABLE

    data class AuditLine(val label: String, val state: String)

    fun auditKeystore(): List<AuditLine> {
        val lines = mutableListOf<AuditLine>()
        val expected = MagiEngine.NodeId.values().map { NodeKeyStore.aliasFor(it) }
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            expected.forEach { alias ->
                val line = when {
                    !ks.containsAlias(alias) -> AuditLine(alias, "MISSING — no key stored yet")
                    else -> {
                        val entry = ks.getEntry(alias, null)
                        if (entry is KeyStore.SecretKeyEntry) {
                            AuditLine(alias, "PRESENT — ${entry.secretKey.algorithm} key, not exportable")
                        } else {
                            AuditLine(alias, "ERROR — entry is not a secret key")
                        }
                    }
                }
                lines.add(line)
            }
            ks.aliases().toList()
                .filter { it.startsWith("nerv_") && it !in expected }
                .sorted()
                .forEach { lines.add(AuditLine(it, "PRESENT — extra NERV entry")) }
        } catch (e: Exception) {
            Log.w(TAG, "Keystore audit failed")
            lines.add(AuditLine("AndroidKeyStore", "ERROR — could not open the Keystore"))
        }
        return lines
    }

    fun auditPlaintextSecrets(): List<AuditLine> {
        val lines = mutableListOf<AuditLine>()
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val files = prefsDir.listFiles()?.filter { it.name.endsWith(".xml") }?.sortedBy { it.name }
            ?: return listOf(AuditLine("shared_prefs", "ERROR — could not list preference files"))
        if (files.isEmpty()) return listOf(AuditLine("shared_prefs", "OK — no preference files found"))
        files.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            val flagged = try {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all.keys
                    .filter { looksLikeRawSecret(it) }
                    .sorted()
            } catch (e: Exception) {
                listOf("<could not read key names>")
            }
            lines.add(
                if (flagged.isEmpty()) AuditLine(name, "OK — no raw-secret key names")
                else AuditLine(name, "CHECK — key names that look like raw secrets: ${flagged.joinToString(", ")}")
            )
        }
        return lines
    }

    fun summary(): String {
        val pin = if (hasPin()) "PIN: SET (${pinLength()} digits)" else "PIN: NOT SET"
        val bio = if (biometricEnabled) "FINGERPRINT: ON" else "FINGERPRINT: OFF"
        val lock = if (isLockedOut()) " — LOCKED OUT ${lockoutSecondsLeft()}s" else ""
        return "$pin · $bio$lock"
    }
}
