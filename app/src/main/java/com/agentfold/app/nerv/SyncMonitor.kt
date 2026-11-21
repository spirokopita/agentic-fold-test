package com.agentfold.app.nerv

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import kotlin.math.max
import kotlin.math.min

data class SyncSignals(

    val batteryPct: Int,
    val charging: Boolean,

    val cpuLoad: Float,

    val memPressure: Float,

    val network: String,
    val networkUp: Boolean
)

class SyncMonitor(private val context: Context) {

    private var smoothedRatio = 62

    private var prevIdle: Long? = null
    private var prevTotal: Long? = null

    fun sample(): SyncSignals {
        var batteryPct = -1
        var charging = false
        try {
            val battery = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (battery != null) {
                val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) batteryPct = (level * 100 / scale).coerceIn(0, 100)
                val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {

        }
        val (network, networkUp) = networkType()
        return SyncSignals(
            batteryPct = batteryPct,
            charging = charging,
            cpuLoad = cpuLoad(),
            memPressure = memPressure(),
            network = network,
            networkUp = networkUp
        )
    }

    fun ratioFrom(s: SyncSignals): Int {
        val batteryScore = if (s.batteryPct >= 0) s.batteryPct.toFloat() else 50f
        val cpuScore = if (s.cpuLoad >= 0f) (1f - s.cpuLoad) * 100f else 50f
        val memScore = if (s.memPressure >= 0f) (1f - s.memPressure) * 100f else 50f
        val netScore = when (s.network) {
            "WIFI", "CELLULAR", "LINK" -> 100f
            "UNKNOWN" -> 40f
            else -> 0f
        }
        var ratio = 0.30f * batteryScore + 0.30f * cpuScore +
            0.25f * memScore + 0.15f * netScore
        if (s.charging) ratio += 5f
        return ratio.toInt().coerceIn(0, 100)
    }

    fun smooth(target: Int): Int {
        smoothedRatio += ((target - smoothedRatio) * 0.25f).toInt()
        return smoothedRatio.coerceIn(0, 100)
    }

    fun channelAmps(s: SyncSignals): FloatArray {
        val cpu = if (s.cpuLoad >= 0f) s.cpuLoad else 0.5f
        val drain = if (s.batteryPct >= 0) 1f - s.batteryPct / 100f else 0.5f
        val mem = if (s.memPressure >= 0f) s.memPressure else 0.5f
        val net = when (s.network) {
            "WIFI", "CELLULAR", "LINK" -> 1.3f
            "UNKNOWN" -> 0.8f
            else -> 0.5f
        }
        val motor = if (s.charging) 1.5f else 0.8f
        val blend = 0.6f + 0.4f * (smoothedRatio / 100f)
        return floatArrayOf(
            (0.5f + cpu).coerceIn(0.4f, 1.6f),
            (0.5f + drain).coerceIn(0.4f, 1.6f),
            (0.5f + mem).coerceIn(0.4f, 1.6f),
            net,
            motor,
            blend.coerceIn(0.4f, 1.6f)
        )
    }

    private fun cpuLoad(): Float {
        return try {
            val line = java.io.File("/proc/stat").bufferedReader().useLines { it.firstOrNull() }
                ?: return -1f
            val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return -1f
            val idle = parts[3] + if (parts.size > 4) parts[4] else 0L
            val total = parts.sum()
            val prevI = prevIdle
            val prevT = prevTotal
            prevIdle = idle
            prevTotal = total
            if (prevI == null || prevT == null || total <= prevT) return -1f
            val idleDelta = idle - prevI
            val totalDelta = total - prevT
            if (totalDelta <= 0) -1f else (1f - idleDelta.toFloat() / totalDelta.toFloat()).coerceIn(0f, 1f)
        } catch (e: Exception) {
            -1f
        }
    }

    private fun memPressure(): Float {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            if (info.totalMem <= 0L) -1f
            else (1f - info.availMem.toFloat() / info.totalMem.toFloat()).coerceIn(0f, 1f)
        } catch (e: Exception) {
            -1f
        }
    }

    @Suppress("DEPRECATION")
    private fun networkType(): Pair<String, Boolean> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = cm.activeNetworkInfo
            if (info == null || !info.isConnected) {
                "NONE" to false
            } else {
                when (info.type) {
                    ConnectivityManager.TYPE_WIFI -> "WIFI" to true
                    ConnectivityManager.TYPE_MOBILE -> "CELLULAR" to true
                    else -> "LINK" to true
                }
            }
        } catch (e: Exception) {

            "UNKNOWN" to false
        }
    }
}
