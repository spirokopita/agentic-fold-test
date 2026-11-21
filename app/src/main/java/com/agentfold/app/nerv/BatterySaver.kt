package com.agentfold.app.nerv

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatterySaver(context: Context) {

    companion object {

        val THRESHOLDS = listOf(10, 15, 20, 30)

        const val DEFAULT_THRESHOLD = 20
        const val NORMAL_SAMPLE_MS = 2000L
        const val SAVER_SAMPLE_MS = 8000L

        const val SAVER_ANIMATION_SCALE = 0.35f

        const val SAVER_FLICKER_SCALE = 2.0f
    }

    private val prefs = context.getSharedPreferences("nerv_battery_saver", Context.MODE_PRIVATE)

    var manual: Boolean
        get() = prefs.getBoolean("manual", false)
        set(value) { prefs.edit().putBoolean("manual", value).apply() }

    var autoEnabled: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) { prefs.edit().putBoolean("auto", value).apply() }

    var autoThreshold: Int
        get() = prefs.getInt("threshold", DEFAULT_THRESHOLD)
            .let { if (it in THRESHOLDS) it else DEFAULT_THRESHOLD }
        set(value) {
            prefs.edit().putInt(
                "threshold",
                if (value in THRESHOLDS) value else DEFAULT_THRESHOLD
            ).apply()
        }

    var active: Boolean = false
        private set

    var lastLevel: Int = -1
        private set

    var lastCharging: Boolean = false
        private set

    fun readBattery(context: Context): Pair<Int, Boolean> {
        return try {
            val battery = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return -1 to false
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            pct to charging
        } catch (_: Exception) {
            -1 to false
        }
    }

    fun check(context: Context): Boolean {
        val (level, charging) = readBattery(context)
        lastLevel = level
        lastCharging = charging
        val want = when {
            manual -> true
            !autoEnabled -> false
            charging -> false
            level < 0 -> active
            level <= autoThreshold -> true
            level > autoThreshold + 5 -> false
            else -> active
        }
        if (want == active) return false
        active = want
        return true
    }

    fun animationScale(): Float = if (active) SAVER_ANIMATION_SCALE else 1f

    fun flickerScale(): Float = if (active) SAVER_FLICKER_SCALE else 1f

    fun sampleIntervalMs(): Long = if (active) SAVER_SAMPLE_MS else NORMAL_SAMPLE_MS

    fun statusLine(): String {
        val battery = if (lastLevel >= 0) "${lastLevel}%" else "?"
        val charge = if (lastCharging) " · CHARGING" else ""
        val state = if (active) {
            if (manual) "ON — switched on by hand" else "ON — battery at/below $autoThreshold%"
        } else {
            "OFF"
        }
        return "BATTERY $battery$charge · SAVER $state"
    }
}
