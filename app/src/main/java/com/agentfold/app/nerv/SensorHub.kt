package com.agentfold.app.nerv

// MAGI EVOLVER — shared sensor layer (SensorHub), 25 Nov 2026.
//
// What this file is: ONE shared reader for the phone's passive sensors.
// Spiros's point was that sensor data should be used equally — one sensor
// read feeding everything that needs it, instead of each experiment or
// tool grabbing the sensor for itself. The hub registers exactly one
// SensorManager listener per passive sensor (accelerometer, gyroscope,
// magnetometer, light, pressure, proximity) plus one GPS listener, reads
// each sensor once, and broadcasts the same readings equally to every
// consumer: the MAGI phyphox tool, the recorder, the mass scan, and any
// future consumer. No consumer may register its own duplicate listener
// for a sensor the hub already covers — they subscribe to the hub instead.
//
// What it keeps: a latest-reading cache per sensor, plus a short rolling
// buffer (5 seconds) for consumers that need a window. That is all —
// this is not a recording pipeline. The recorder (SensorRecorder) is the
// thing that writes to a file.
//
// Battery: the hub starts only when something asks for sensor data and
// stops when nothing needs it. A listener is registered when the first
// consumer subscribes and unregistered when the last one leaves. There
// is no always-on sensing. Sensors are read at SENSOR_DELAY_NORMAL, the
// slowest useful rate.
//
// Honest limits (do not pretend these are solved):
// - Camera and microphone cannot be shared this way. One camera session
//   and one mic stream at a time, so camera and audio experiments still
//   run one at a time. The microphone is not a hub sensor: the recorder
//   owns the single mic stream while a recording runs.
// - Experiments that need the user to do something (pendulum, spring,
//   elevator, collision, stopwatches) still need that interaction. The
//   hub covers passive sensor data only.
// - GPS needs the location permission the app already has. If it is not
//   granted (or GPS is off), GPS is simply uncovered — the hub never
//   asks for a permission.
// - LocationTracker (the app's existing one-shot location helper) predates
//   the hub and is left alone. All NEW sensor consumers go through the
//   hub.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SensorHub(private val context: Context) : SensorEventListener {

    data class SensorReading(
        val sensorType: Int,
        val values: FloatArray,
        val accuracy: Int,
        val timestampNanos: Long,
        val wallTimeMs: Long,
        // Only set for GPS readings — lat/lon need doubles, and a
        // FloatArray would lose the precision a track needs.
        val location: Location? = null
    )

    fun interface ReadingListener {
        fun onReading(reading: SensorReading)
    }

    companion object {
        // Custom sensor-type numbers for things that are not Android
        // sensors. They never collide with Sensor.TYPE_* (1..34).
        const val SENSOR_TYPE_GPS = 10001
        const val SENSOR_TYPE_MICROPHONE = 10002 // not a hub sensor — see header

        val PASSIVE_SENSORS = intArrayOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_PROXIMITY
        )

        private const val BUFFER_WINDOW_MS = 5000L
        private const val BUFFER_MAX_ENTRIES = 500
        private const val GPS_MIN_TIME_MS = 1000L
        private const val FRESH_READING_MS = 1000L
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val lock = Any()
    private val cache = mutableMapOf<Int, SensorReading>()
    private val buffers = mutableMapOf<Int, ArrayDeque<SensorReading>>()
    // sensor type -> (consumer name -> listener)
    private val subscriptions = mutableMapOf<Int, MutableMap<String, ReadingListener>>()
    private val active = mutableSetOf<Int>()
    private var gpsListener: LocationListener? = null

    // True when this phone can provide the sensor at all. GPS additionally
    // needs the location permission and an enabled GPS provider.
    fun covers(sensorType: Int): Boolean {
        if (sensorType == SENSOR_TYPE_GPS) {
            return hasLocationPermission() && try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {
                false
            }
        }
        return sensorManager.getDefaultSensor(sensorType) != null
    }

    // Subscribe to a sensor's readings. Returns false when the sensor is
    // not covered on this phone. The first consumer starts the single
    // shared listener; later consumers just join it. The listener is
    // called on the main thread.
    fun subscribe(sensorType: Int, consumer: String, listener: ReadingListener): Boolean {
        if (!covers(sensorType)) return false
        synchronized(lock) {
            subscriptions.getOrPut(sensorType) { mutableMapOf() }[consumer] = listener
            if (sensorType !in active) startSensorLocked(sensorType)
        }
        return true
    }

    fun unsubscribe(sensorType: Int, consumer: String) {
        synchronized(lock) {
            subscriptions[sensorType]?.remove(consumer)
            if (subscriptions[sensorType].isNullOrEmpty()) {
                subscriptions.remove(sensorType)
                stopSensorLocked(sensorType)
            }
        }
    }

    // The latest reading, or null when none has arrived yet.
    fun snapshot(sensorType: Int): SensorReading? = synchronized(lock) { cache[sensorType] }

    // One reading, waiting briefly for a fresh one when the cache is
    // stale. Used by the phyphox tool. Returns null when nothing arrives
    // in time — the caller says "no data", never invents a reading.
    fun request(sensorType: Int, timeoutMs: Long = 2000L): SensorReading? {
        val cached = snapshot(sensorType)
        if (cached != null && System.currentTimeMillis() - cached.wallTimeMs < FRESH_READING_MS) {
            return cached
        }
        val latch = CountDownLatch(1)
        var reading: SensorReading? = null
        val consumer = "__request__" + System.nanoTime()
        val ok = subscribe(sensorType, consumer, ReadingListener {
            if (reading == null) {
                reading = it
                latch.countDown()
            }
        })
        if (!ok) return null
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            reading ?: snapshot(sensorType)
        } catch (_: Exception) {
            null
        } finally {
            unsubscribe(sensorType, consumer)
        }
    }

    // Readings from the last durationMs, oldest first. The rolling buffer
    // only holds about 5 seconds — this is a window, not a recording.
    fun window(sensorType: Int, durationMs: Long): List<SensorReading> {
        val since = System.currentTimeMillis() - durationMs
        return synchronized(lock) {
            buffers[sensorType]?.filter { it.wallTimeMs >= since }?.toList() ?: emptyList()
        }
    }

    // Which sensors currently have a listener running. For the checklist:
    // two consumers on the accelerometer still means one listener here.
    fun activeSensors(): Set<Int> = synchronized(lock) { active.toSet() }

    fun listenerCount(): Int = synchronized(lock) { active.size }

    fun hasActiveListeners(): Boolean = listenerCount() > 0

    // Stop everything. Safety net for shutdown; normal use stops each
    // sensor when its last consumer leaves.
    fun stopAll() {
        synchronized(lock) {
            subscriptions.keys.toList().forEach { stopSensorLocked(it) }
            subscriptions.clear()
        }
    }

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // Called only with lock held.
    private fun startSensorLocked(sensorType: Int) {
        if (sensorType == SENSOR_TYPE_GPS) {
            if (!hasLocationPermission()) return
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val values = floatArrayOf(
                        if (location.hasAccuracy()) location.accuracy else -1f,
                        if (location.hasAltitude()) location.altitude.toFloat() else 0f,
                        if (location.hasSpeed()) location.speed else 0f
                    )
                    publish(
                        SensorReading(
                            SENSOR_TYPE_GPS, values, 0,
                            System.nanoTime(), System.currentTimeMillis(), location
                        )
                    )
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            gpsListener = listener
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, GPS_MIN_TIME_MS, 0f, listener, Looper.getMainLooper()
                )
                active.add(sensorType)
            } catch (_: SecurityException) {
                gpsListener = null
            } catch (_: IllegalArgumentException) {
                gpsListener = null
            }
            return
        }
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
        try {
            sensorManager.registerListener(
                this, sensor, SensorManager.SENSOR_DELAY_NORMAL, mainHandler
            )
            active.add(sensorType)
        } catch (_: Exception) {
        }
    }

    // Called only with lock held.
    private fun stopSensorLocked(sensorType: Int) {
        if (sensorType == SENSOR_TYPE_GPS) {
            gpsListener?.let { listener ->
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {
                }
            }
            gpsListener = null
        } else {
            try {
                sensorManager.getDefaultSensor(sensorType)?.let { sensor ->
                    sensorManager.unregisterListener(this, sensor)
                }
            } catch (_: Exception) {
            }
        }
        active.remove(sensorType)
    }

    override fun onSensorChanged(event: SensorEvent) {
        publish(
            SensorReading(
                event.sensor.type,
                event.values.copyOf(),
                event.accuracy,
                event.timestamp,
                System.currentTimeMillis()
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    // Cache + buffer under the lock, fan-out outside it so a listener can
    // never deadlock the hub by calling back into it.
    private fun publish(reading: SensorReading) {
        val listeners = synchronized(lock) {
            cache[reading.sensorType] = reading
            val buffer = buffers.getOrPut(reading.sensorType) { ArrayDeque() }
            buffer.addLast(reading)
            while (buffer.size > BUFFER_MAX_ENTRIES ||
                (buffer.firstOrNull()?.wallTimeMs ?: 0L) < System.currentTimeMillis() - BUFFER_WINDOW_MS
            ) {
                buffer.removeFirst()
            }
            subscriptions[reading.sensorType]?.values?.toList() ?: emptyList()
        }
        listeners.forEach { listener ->
            try {
                listener.onReading(reading)
            } catch (_: Exception) {
            }
        }
    }
}
