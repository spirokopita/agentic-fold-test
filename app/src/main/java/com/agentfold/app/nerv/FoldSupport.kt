package com.agentfold.app.nerv

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build

class FoldSupport(context: Context) {

    companion object {
        private const val PREFS = "nerv_fold"
        private const val KEY_DUAL_PANE = "dual_pane"
        private const val KEY_DUAL_SPLIT = "dual_split"

        const val UNFOLDED_MIN_WIDTH_DP = 600

        const val NO_ANGLE = -1f

        const val MIN_SPLIT = 0.25f

        const val MAX_SPLIT = 0.75f
    }

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var dualPaneEnabled: Boolean
        get() = prefs.getBoolean(KEY_DUAL_PANE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DUAL_PANE, value).apply()
        }

    var dualSplit: Float
        get() = prefs.getFloat(KEY_DUAL_SPLIT, 0.5f).coerceIn(MIN_SPLIT, MAX_SPLIT)
        set(value) {
            prefs.edit().putFloat(KEY_DUAL_SPLIT, value.coerceIn(MIN_SPLIT, MAX_SPLIT)).apply()
        }

    fun isFoldable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return appContext.packageManager.hasSystemFeature(
            PackageManager.FEATURE_SENSOR_HINGE_ANGLE
        )
    }

    private var hingeAngle: Float = NO_ANGLE
    private var hingeListener: SensorEventListener? = null

    fun hingeAngleDegrees(): Float = hingeAngle

    fun startHingeListening() {
        if (hingeListener != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val angle = event.values.firstOrNull() ?: return
                if (angle in 0f..360f) hingeAngle = angle
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            hingeListener = listener
        }
    }

    fun stopHingeListening() {
        val listener = hingeListener ?: return
        hingeListener = null
        try {
            val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            manager?.unregisterListener(listener)
        } catch (e: Exception) {
        }
    }

    fun windowWidthDp(activity: Activity): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                val density = activity.resources.displayMetrics.density
                if (density > 0f) (bounds.width() / density).toInt() else 0
            } else {
                @Suppress("DEPRECATION")
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getMetrics(metrics)
                if (metrics.density > 0f) (metrics.widthPixels / metrics.density).toInt() else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun isUnfolded(activity: Activity): Boolean =
        windowWidthDp(activity) >= UNFOLDED_MIN_WIDTH_DP

    fun stateLabel(activity: Activity): String {
        if (!isFoldable()) return "NOT A FOLDING PHONE — single column, as before"
        val angle = hingeAngleDegrees()
        val angleText = if (angle == NO_ANGLE) "hinge angle unknown yet" else "hinge ${angle.toInt()}°"
        return if (isUnfolded(activity)) {
            "FOLDABLE — UNFOLDED (${windowWidthDp(activity)}dp, $angleText) — dual-pane ${if (dualPaneEnabled) "ON" else "OFF"}"
        } else {
            "FOLDABLE — FOLDED (${windowWidthDp(activity)}dp, $angleText) — single column"
        }
    }
}
