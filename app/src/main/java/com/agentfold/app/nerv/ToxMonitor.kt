package com.agentfold.app.nerv

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock

data class ToxSignals(

    val batteryPct: Int,
    val charging: Boolean,

    val batteryTempC: Float,

    val cpuLoad: Float,

    val memPressure: Float,

    val uptimeHours: Float
)

class ToxMonitor(private val context: Context) {

    private val smoothed = mutableListOf(38, 52, 29)

    private val cpuHistory = ArrayDeque<Float>()

    private var prevIdle: Long? = null
    private var prevTotal: Long? = null

    fun sample(): ToxSignals {
        var batteryPct = -1
        var charging = false
        var batteryTempC = -1f
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
                val tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (tempTenths >= 0) batteryTempC = tempTenths / 10f
            }
        } catch (e: Exception) {

        }
        return ToxSignals(
            batteryPct = batteryPct,
            charging = charging,
            batteryTempC = batteryTempC,
            cpuLoad = cpuLoad(),
            memPressure = memPressure(),
            uptimeHours = SystemClock.elapsedRealtime() / 3_600_000f
        )
    }

    fun valuesFrom(s: ToxSignals): IntArray {

        val bar0 = if (s.batteryPct < 0) {
            50
        } else {
            val drain = (100 - s.batteryPct).toFloat()
            val heat = if (s.batteryTempC > 30f) {
                ((s.batteryTempC - 30f) * 3f).coerceIn(0f, 25f)
            } else {
                0f
            }
            val relief = if (s.charging) 10f else 0f
            (0.6f * drain + heat - relief).toInt()
        }

        if (s.cpuLoad >= 0f) {
            cpuHistory.addLast(s.cpuLoad)
            while (cpuHistory.size > 4) cpuHistory.removeFirst()
        }
        val bar1 = if (cpuHistory.isEmpty()) {
            50
        } else {
            (cpuHistory.average() * 100f).toInt()
        }

        val wear = s.uptimeHours.coerceIn(0f, 24f) * 0.5f
        val bar2 = if (s.memPressure < 0f) {
            (50f + wear).toInt()
        } else {
            (s.memPressure * 70f + wear).toInt()
        }
        return intArrayOf(
            bar0.coerceIn(2, 100),
            bar1.coerceIn(2, 100),
            bar2.coerceIn(2, 100)
        )
    }

    fun smooth(targets: IntArray): IntArray {
        for (i in smoothed.indices) {
            val target = targets.getOrNull(i) ?: smoothed[i]
            smoothed[i] = (smoothed[i] + ((target - smoothed[i]) * 0.25f).toInt())
                .coerceIn(2, 100)
        }
        return intArrayOf(smoothed[0], smoothed[1], smoothed[2])
    }

    fun readout(s: ToxSignals): String {
        val battery = if (s.batteryPct >= 0) "${s.batteryPct}%" else "?"
        val charge = if (s.charging) " · CHARGING" else ""
        val temp = if (s.batteryTempC >= 0f) {
            " · ${String.format("%.1f", s.batteryTempC)}°C"
        } else {
            ""
        }
        val cpu = if (s.cpuLoad >= 0f) "${(s.cpuLoad * 100).toInt()}%" else "?"
        val mem = if (s.memPressure >= 0f) "${(s.memPressure * 100).toInt()}%" else "?"
        val hours = s.uptimeHours.toInt()
        val mins = ((s.uptimeHours - hours) * 60).toInt()
        return "BATTERY $battery$charge$temp · CPU LOAD $cpu · MEMORY $mem · UP ${hours}h ${mins}m"
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
}
