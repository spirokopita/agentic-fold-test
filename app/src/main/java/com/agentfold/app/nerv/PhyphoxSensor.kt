package com.agentfold.app.nerv

// MAGI EVOLVER — phyphox sensor access, 25 Nov 2026.
//
// What this file is: the read-only bridge between MAGI and the phyphox
// sensor lab built into this phone. It answers two questions only:
//   1. Which experiments are bundled? (the 40 .phyphox files in assets)
//   2. What does the phone's sensor say right now for one experiment?
//
// What the phyphox API actually supports — checked against the module:
// - The 40 experiment definitions live in phyphox/src/main/assets/
//   experiments/*.phyphox. Each has a <title>, a <category> and an
//   <input> with a <sensor type="..."> naming the Android sensor it
//   reads. SensorInput.java maps those types to Sensor.TYPE_*.
// - phyphox keeps NO persistent store of past recordings. Data exists
//   only in live buffers while an experiment runs, or in files the user
//   exports and shares. So there is no "last recording" to read back.
//   The honest answer to "latest data" is one live snapshot, taken now.
// - 19 of the 40 experiments use a hardware sensor that needs no
//   runtime permission (accelerometer, gyroscope, light, pressure and
//   friends). The other 21 need the microphone, camera, GPS, Bluetooth
//   or depth sensor, or are outputs (tone generator, strobe) — MAGI
//   cannot read those from a tool call and the tool says so.
//
// SHARED SENSOR LAYER (SensorHub, added 25 Nov 2026): when a hub is
// supplied, readings for the six passive sensors the hub covers
// (accelerometer, gyroscope, magnetic field, light, pressure, proximity)
// come from the hub — one shared listener, one read feeding every
// consumer. The one-shot listener below stays only as the fallback for
// sensors the hub does not cover (linear acceleration, gravity,
// temperature, humidity, rotation vector). This class never registers
// its own listener for a sensor the hub already covers.
//
// Rules this file never breaks:
// - Read-only. No recording, no streaming, no new permission.
// - Never invent a reading. Every path that cannot read returns an
//   "error: ..." message saying why, so MAGI reports "no data" instead
//   of making a number up.
// - One-shot reads: register a listener, take the first event, wait at
//   most 2 seconds, unregister. Sensor events are delivered on the main
//   thread (the tool runs on a MAGI background thread, which has no
//   Looper) while the caller waits on a latch.

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PhyphoxSensor(private val context: Context, private val hub: SensorHub? = null) {

    data class ExperimentInfo(
        val id: String, // file name without .phyphox, e.g. "accelerometer"
        val title: String,
        val category: String,
        // phyphox sensor types in file order, e.g. ["accelerometer"].
        // Empty when the experiment uses no phone sensor.
        val sensorTypes: List<String>,
        // True when the first sensor can be read by a tool call (no
        // permission needed). False for audio/camera/GPS/Bluetooth/depth.
        val readable: Boolean
    )

    companion object {
        private const val EXPERIMENTS_DIR = "experiments"
        private const val READ_TIMEOUT_MS = 2000L

        // phyphox sensor type (from the .phyphox file) -> Android sensor
        // type. Mirrors de.rwth_aachen.phyphox.SensorInput. Only these need
        // no runtime permission, so only these can be read by a tool call.
        private val SENSOR_TYPE_MAP = mapOf(
            "accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "linear_acceleration" to Sensor.TYPE_LINEAR_ACCELERATION,
            "gravity" to Sensor.TYPE_GRAVITY,
            "gyroscope" to Sensor.TYPE_GYROSCOPE,
            "magnetic_field" to Sensor.TYPE_MAGNETIC_FIELD,
            "pressure" to Sensor.TYPE_PRESSURE,
            "light" to Sensor.TYPE_LIGHT,
            "proximity" to Sensor.TYPE_PROXIMITY,
            "temperature" to Sensor.TYPE_AMBIENT_TEMPERATURE,
            "humidity" to Sensor.TYPE_RELATIVE_HUMIDITY,
            "attitude" to Sensor.TYPE_ROTATION_VECTOR
        )

        private val SENSOR_LABELS = mapOf(
            "accelerometer" to "accelerometer",
            "linear_acceleration" to "linear acceleration",
            "gravity" to "gravity",
            "gyroscope" to "gyroscope",
            "magnetic_field" to "magnetic field",
            "pressure" to "pressure",
            "light" to "light",
            "proximity" to "proximity",
            "temperature" to "temperature",
            "humidity" to "humidity",
            "attitude" to "rotation vector"
        )

        private val SENSOR_UNITS = mapOf(
            Sensor.TYPE_ACCELEROMETER to "m/s²",
            Sensor.TYPE_LINEAR_ACCELERATION to "m/s²",
            Sensor.TYPE_GRAVITY to "m/s²",
            Sensor.TYPE_GYROSCOPE to "rad/s",
            Sensor.TYPE_MAGNETIC_FIELD to "µT",
            Sensor.TYPE_PRESSURE to "hPa",
            Sensor.TYPE_LIGHT to "lx",
            Sensor.TYPE_PROXIMITY to "cm",
            Sensor.TYPE_AMBIENT_TEMPERATURE to "°C",
            Sensor.TYPE_RELATIVE_HUMIDITY to "%",
            Sensor.TYPE_ROTATION_VECTOR to ""
        )
    }

    // The bundled definitions never change while the app runs, so parse
    // them once and reuse the list.
    private var cachedExperiments: List<ExperimentInfo>? = null

    fun listExperiments(): List<ExperimentInfo> {
        cachedExperiments?.let { return it }
        val names = try {
            context.assets.list(EXPERIMENTS_DIR) ?: emptyArray()
        } catch (_: Exception) {
            return emptyList()
        }
        val parsed = names.filter { it.endsWith(".phyphox") }.sorted()
            .mapNotNull { file -> parseExperiment(file) }
        cachedExperiments = parsed
        return parsed
    }

    // Read one live snapshot for a named experiment. Returns the reading,
    // or an "error: ..." message explaining why there is no data. The
    // caller (ToolRunner) treats "error: ..." as a failed tool call.
    // Never returns an invented reading.
    fun readLatest(queryRaw: String): String {
        val query = queryRaw.trim()
        if (query.isEmpty()) {
            return "error: say which experiment — use phyphox_list to see the 40 bundled experiments."
        }
        val info = listExperiments().firstOrNull {
            it.id.equals(query, ignoreCase = true) || it.title.equals(query, ignoreCase = true)
        } ?: return "error: no experiment called \"$query\" — use phyphox_list to see the 40 bundled experiments."
        val firstSensor = info.sensorTypes.firstOrNull()
            ?: return "error: \"${info.title}\" does not use a phone sensor — it needs the microphone, camera, GPS or Bluetooth, which MAGI cannot read. Open it in TOOLS > EXPERIMENTS to run it."
        val androidType = SENSOR_TYPE_MAP[firstSensor]
            ?: return "error: \"${info.title}\" uses a sensor MAGI cannot read ($firstSensor). Open it in TOOLS > EXPERIMENTS to run it."
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return "error: no sensor service on this phone."
        val sensor = sensorManager.getDefaultSensor(androidType)
            ?: return "error: this phone has no ${SENSOR_LABELS[firstSensor] ?: firstSensor} sensor, so \"${info.title}\" has nothing to read."
        // EVOLVER (SensorHub): when the hub covers this sensor, the
        // reading comes from the hub — one shared listener for every
        // consumer. Only sensors the hub does not cover fall back to the
        // one-shot listener below.
        val values = if (hub != null && hub.covers(androidType)) {
            hub.request(androidType)?.values
                ?: return "error: no reading arrived from the ${SENSOR_LABELS[firstSensor] ?: firstSensor} sensor in ${READ_TIMEOUT_MS / 1000} seconds — the sensor may be asleep. Try again, or open the experiment in TOOLS > EXPERIMENTS."
        } else {
            readOneShot(sensorManager, sensor)
                ?: return "error: no reading arrived from the ${SENSOR_LABELS[firstSensor] ?: firstSensor} sensor in ${READ_TIMEOUT_MS / 1000} seconds — the sensor may be asleep. Try again, or open the experiment in TOOLS > EXPERIMENTS."
        }
        return formatReading(info, firstSensor, androidType, values)
    }

    // Parse one .phyphox definition: title, category and sensor types.
    // Stops as soon as all three are found — the icon and translations
    // after them are not needed here.
    private fun parseExperiment(file: String): ExperimentInfo? {
        val id = file.removeSuffix(".phyphox")
        return try {
            context.assets.open("$EXPERIMENTS_DIR/$file").use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, "UTF-8")
                var title = id
                var category = ""
                val sensors = mutableListOf<String>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            // First <title> is the English one; translated
                            // titles inside <translations> are skipped.
                            "title" -> if (title == id) title = parser.nextText().trim()
                            "category" -> if (category.isEmpty()) category = parser.nextText().trim()
                            "sensor" -> parser.getAttributeValue(null, "type")
                                ?.trim()?.takeIf { it.isNotEmpty() }?.let { sensors.add(it) }
                        }
                    }
                    if (title != id && category.isNotEmpty() && sensors.isNotEmpty()) break
                    event = parser.next()
                }
                ExperimentInfo(
                    id = id,
                    title = title.ifBlank { id },
                    category = category,
                    sensorTypes = sensors,
                    readable = sensors.firstOrNull() in SENSOR_TYPE_MAP
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    // One live event from a sensor, or null when nothing arrives in time.
    // Fallback only — used for sensors the SensorHub does not cover
    // (linear acceleration, gravity, temperature, humidity, rotation
    // vector). Sensors the hub covers are read through the hub, never
    // with a second listener here.
    // Sensor events need a Looper. The tool runs on a MAGI background
    // thread, which has none, so events are delivered on the main thread
    // while this thread waits on the latch. Always unregisters.
    private fun readOneShot(sensorManager: SensorManager, sensor: Sensor): FloatArray? {
        val latch = CountDownLatch(1)
        var reading: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (reading == null) {
                    reading = event.values.copyOf()
                    latch.countDown()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }
        }
        val handler = Handler(Looper.getMainLooper())
        return try {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
            latch.await(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            reading
        } catch (_: Exception) {
            null
        } finally {
            try { sensorManager.unregisterListener(listener) } catch (_: Exception) { }
        }
    }

    private fun formatReading(
        info: ExperimentInfo, sensorType: String, androidType: Int, values: FloatArray
    ): String {
        val unit = SENSOR_UNITS[androidType].orEmpty()
        val stamp = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val body = when (androidType) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY -> {
                val base = "x=${fmt(values, 0)} y=${fmt(values, 1)} z=${fmt(values, 2)} $unit"
                if (values.size >= 3) "$base | |a|=${fmtAbs(values)} $unit" else base
            }
            Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD ->
                "x=${fmt(values, 0)} y=${fmt(values, 1)} z=${fmt(values, 2)} $unit"
            Sensor.TYPE_ROTATION_VECTOR ->
                "x=${fmt(values, 0)} y=${fmt(values, 1)} z=${fmt(values, 2)} w=${fmt(values, 3)}"
            else -> "${fmt(values, 0)} $unit"
        }
        return "${info.title} — ${SENSOR_LABELS[sensorType] ?: sensorType}: $body " +
            "(live snapshot, $stamp — one reading right now, not a recording)"
    }

    private fun fmt(values: FloatArray, i: Int): String =
        if (i < values.size) "%.2f".format(Locale.US, values[i]) else "?"

    private fun fmtAbs(values: FloatArray): String {
        val sum = (0 until minOf(3, values.size)).sumOf { (values[it] * values[it]).toDouble() }
        return "%.2f".format(Locale.US, Math.sqrt(sum))
    }
}
