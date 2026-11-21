package com.agentfold.app.nerv

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.os.SystemClock

class HealthDashboard(
    private val context: Context,
    private val toxMonitor: ToxMonitor
) {

    data class Snapshot(
        val batteryPct: Int,
        val charging: Boolean,
        val batteryTempC: Float,
        val storageFreeBytes: Long,
        val storageTotalBytes: Long,
        val memoryFreeBytes: Long,
        val memoryTotalBytes: Long,
        val uptimeMillis: Long,
        val warnings: List<String>
    )

    companion object {

        const val STORAGE_LOW_BYTES = 1L * 1024 * 1024 * 1024

        const val BATTERY_HOT_C = 40f

        const val BATTERY_LOW_PCT = 15

        const val MEMORY_LOW_FRACTION = 0.10f

        const val UPTIME_LONG_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    fun snapshot(): Snapshot {
        val signals = try {
            toxMonitor.sample()
        } catch (_: Exception) {
            null
        }
        var storageFree = -1L
        var storageTotal = -1L
        try {
            val stat = StatFs(context.filesDir.path)
            storageFree = stat.availableBytes
            storageTotal = stat.totalBytes
        } catch (_: Exception) {

        }
        var memoryFree = -1L
        var memoryTotal = -1L
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            memoryFree = info.availMem
            memoryTotal = info.totalMem
        } catch (_: Exception) {

        }
        val warnings = mutableListOf<String>()
        if (storageFree in 0 until STORAGE_LOW_BYTES) {
            warnings.add("Storage low — less than 1 GB free. Delete old reports or files in SETUP > FILES.")
        }
        if (signals != null) {
            if (signals.batteryTempC > BATTERY_HOT_C) {
                warnings.add("Battery hot — over 40°C. Let the phone cool down before charging it hard.")
            }
            if (signals.batteryPct in 0..BATTERY_LOW_PCT && !signals.charging) {
                warnings.add("Battery low — plug the phone in.")
            }
        }
        if (memoryFree >= 0L && memoryTotal > 0L &&
            memoryFree.toFloat() / memoryTotal.toFloat() < MEMORY_LOW_FRACTION
        ) {
            warnings.add("Memory low — close apps you are not using.")
        }
        val uptime = SystemClock.elapsedRealtime()
        if (uptime > UPTIME_LONG_MILLIS) {
            warnings.add("Phone has been on for over a week — a restart can help it run better.")
        }
        return Snapshot(
            batteryPct = signals?.batteryPct ?: -1,
            charging = signals?.charging ?: false,
            batteryTempC = signals?.batteryTempC ?: -1f,
            storageFreeBytes = storageFree,
            storageTotalBytes = storageTotal,
            memoryFreeBytes = memoryFree,
            memoryTotalBytes = memoryTotal,
            uptimeMillis = uptime,
            warnings = warnings
        )
    }

    fun lines(s: Snapshot): String {
        val battery = if (s.batteryPct >= 0) "${s.batteryPct}%" else "?"
        val charge = if (s.charging) " · CHARGING" else ""
        val temp = if (s.batteryTempC >= 0f) " · ${String.format("%.1f", s.batteryTempC)}°C" else ""
        val storage = if (s.storageFreeBytes >= 0L && s.storageTotalBytes > 0L) {
            "${formatBytes(s.storageFreeBytes)} FREE OF ${formatBytes(s.storageTotalBytes)}"
        } else {
            "?"
        }
        val memory = if (s.memoryFreeBytes >= 0L && s.memoryTotalBytes > 0L) {
            "${formatBytes(s.memoryFreeBytes)} FREE OF ${formatBytes(s.memoryTotalBytes)}"
        } else {
            "?"
        }
        return "BATTERY $battery$charge$temp\n" +
            "STORAGE $storage\n" +
            "MEMORY $memory\n" +
            "UPTIME ${formatUptime(s.uptimeMillis)}"
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 0L -> "?"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
        else -> String.format("%.1f GB", bytes / 1073741824.0)
    }

    fun formatUptime(millis: Long): String {
        val totalMinutes = (millis / 60000).coerceAtLeast(0L)
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        val mins = totalMinutes % 60
        return if (days > 0) "${days}d ${hours}h ${mins}m" else "${hours}h ${mins}m"
    }
}
