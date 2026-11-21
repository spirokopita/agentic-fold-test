package com.agentfold.app.nerv

import android.content.Context

class DisplaySettings(context: Context) {

    companion object {
        const val MIN_TEXT_SIZE_SP = 12
        const val MAX_TEXT_SIZE_SP = 20
        const val DEFAULT_TEXT_SIZE_SP = 14
        val PALETTES = listOf("amber", "cyan", "alert", "eva01")

        val CUSTOM_ACCENTS = listOf(
            "GREEN" to 0xff3dff7a.toInt(),
            "PINK" to 0xffff7ad9.toInt(),
            "GOLD" to 0xffffd21a.toInt(),
            "VIOLET" to 0xffb48cff.toInt(),
            "SKY" to 0xff7ac8ff.toInt()
        )
    }

    private val prefs = context.getSharedPreferences("nerv_display", Context.MODE_PRIVATE)

    var palette: String
        get() = prefs.getString("palette", "amber")!!.let { if (it in PALETTES) it else "amber" }
        set(value) { prefs.edit().putString("palette", if (value in PALETTES) value else "amber").apply() }

    var customAccent: Int
        get() = prefs.getInt("custom_accent", 0)
        set(value) {
            val known = CUSTOM_ACCENTS.any { it.second == value }
            prefs.edit().putInt("custom_accent", if (value == 0 || known) value else 0).apply()
        }

    fun customAccentName(): String =
        CUSTOM_ACCENTS.firstOrNull { it.second == customAccent }?.first ?: ""

    var scanlines: Boolean
        get() = prefs.getBoolean("scanlines", false)
        set(value) { prefs.edit().putBoolean("scanlines", value).apply() }

    var flicker: Boolean
        get() = prefs.getBoolean("flicker", true)
        set(value) { prefs.edit().putBoolean("flicker", value).apply() }

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) { prefs.edit().putBoolean("sound", value).apply() }

    var textSizeSp: Int
        get() = prefs.getInt("text_size_sp", DEFAULT_TEXT_SIZE_SP)
            .coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        set(value) {
            prefs.edit().putInt(
                "text_size_sp",
                value.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
            ).apply()
        }

    var highContrast: Boolean
        get() = prefs.getBoolean("high_contrast", false)
        set(value) { prefs.edit().putBoolean("high_contrast", value).apply() }

    var textBoost: Boolean
        get() = prefs.getBoolean("text_boost", false)
        set(value) { prefs.edit().putBoolean("text_boost", value).apply() }

    var reduceMotion: Boolean
        get() = prefs.getBoolean("reduce_motion", false)
        set(value) { prefs.edit().putBoolean("reduce_motion", value).apply() }
}
