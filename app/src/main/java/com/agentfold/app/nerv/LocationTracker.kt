package com.agentfold.app.nerv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class LocationTracker(private val context: Context) {

    data class Fix(
        val lat: Double,
        val lon: Double,

        val accuracyM: Float,
        val provider: String,
        val timeMs: Long,
        val live: Boolean,
    )

    private val prefs = context.getSharedPreferences("nerv_location", Context.MODE_PRIVATE)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null
    private var onFix: ((Fix) -> Unit)? = null

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean =
        try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }

    fun lastKnown(): Fix? {
        if (!hasPermission()) return null
        val gps = safeLastKnown(LocationManager.GPS_PROVIDER)
        val net = safeLastKnown(LocationManager.NETWORK_PROVIDER)
        return betterOf(gps, net)?.let { toFix(it, live = false) }
    }

    fun savedFix(): Fix? {
        if (!hasPermission()) return null
        if (!prefs.contains("lat")) return null
        val lat = prefs.getFloat("lat", 0f).toDouble()
        val lon = prefs.getFloat("lon", 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return null
        return Fix(
            lat = lat,
            lon = lon,
            accuracyM = prefs.getFloat("acc", -1f),
            provider = prefs.getString("provider", "LAST KNOWN") ?: "LAST KNOWN",
            timeMs = prefs.getLong("time", 0L),
            live = false,
        )
    }

    fun start(onFix: (Fix) -> Unit): Boolean {
        if (!hasPermission()) return false
        stop()
        this.onFix = onFix
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val fix = toFix(location, live = true)
                saveFix(fix)
                this@LocationTracker.onFix?.invoke(fix)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        this.listener = listener
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5000L, 10f, listener,
            )

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 5000L, 10f, listener,
            )
            true
        } catch (e: SecurityException) {
            stop()
            false
        } catch (e: IllegalArgumentException) {

            stop()
            false
        }
    }

    fun stop() {
        val listener = listener ?: return
        this.listener = null
        onFix = null
        try {
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {

        }
    }

    private fun safeLastKnown(provider: String): Location? =
        try {
            locationManager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun betterOf(a: Location?, b: Location?): Location? {
        if (a == null) return b
        if (b == null) return a
        if (a.time != b.time) return if (a.time > b.time) a else b
        val aAcc = if (a.hasAccuracy()) a.accuracy else Float.MAX_VALUE
        val bAcc = if (b.hasAccuracy()) b.accuracy else Float.MAX_VALUE
        return if (aAcc <= bAcc) a else b
    }

    private fun toFix(location: Location, live: Boolean): Fix = Fix(
        lat = location.latitude,
        lon = location.longitude,
        accuracyM = if (location.hasAccuracy()) location.accuracy else -1f,
        provider = location.provider ?: "UNKNOWN",
        timeMs = location.time,
        live = live,
    )

    private fun saveFix(fix: Fix) {
        prefs.edit()
            .putFloat("lat", fix.lat.toFloat())
            .putFloat("lon", fix.lon.toFloat())
            .putFloat("acc", fix.accuracyM)
            .putString("provider", fix.provider)
            .putLong("time", fix.timeMs)
            .apply()
    }

    companion object {

        fun formatLat(lat: Double): String {
            val hemi = if (lat >= 0) "N" else "S"
            return String.format(java.util.Locale.US, "%.4f° %s", kotlin.math.abs(lat), hemi)
        }

        fun formatLon(lon: Double): String {
            val hemi = if (lon >= 0) "E" else "W"
            return String.format(java.util.Locale.US, "%.4f° %s", kotlin.math.abs(lon), hemi)
        }

        fun formatAge(timeMs: Long): String {
            val ageSec = ((System.currentTimeMillis() - timeMs) / 1000).coerceAtLeast(0)
            return when {
                ageSec < 60 -> "${ageSec}s AGO"
                ageSec < 3600 -> "${ageSec / 60}m AGO"
                ageSec < 86400 -> "${ageSec / 3600}h AGO"
                else -> "${ageSec / 86400}d AGO"
            }
        }
    }
}
