package com.agentfold.app.nerv

// MAGI EVOLVER — Scan (replay the recording through the analyses), 25 Nov 2026.
//
// What this file is: the mass scan. Instead of running experiments live
// one after another, the recorder captures the sensors once and this
// subsystem replays that recording through the analysis for each
// experiment that can be computed from recorded data, and collects all
// results into one mass-scan report that MAGI reads via the phyphox tool.
//
// How it works: each phyphox experiment's analysis works on data buffers,
// so the scan feeds it the recorded buffers instead of live ones. The
// analyses here are plain statistics on the recorded samples — means,
// peaks, dominant frequency from zero crossings, trigger times — no FFT
// library, no new dependency. Anything that is an estimate is labelled
// an estimate in the report.
//
// Stopwatches are computed from the recording too, by detecting their
// trigger in the data: a clap spike in the audio levels, a motion spike
// in the acceleration, a light step, a proximity change. The report lists
// the trigger times it found, or says plainly that it found none.
//
// Honest exclusions for v1 — stated here, in the report, and in the
// checklist. They are not bugs, they are the boundary of this version:
// - Camera experiments (colour, brightness, camera spectrum, camera
//   stopwatches, depth, spectroscopy): frames are far too heavy to
//   record and replay in v1.
// - Tone generator and strobe: they produce output, they do not measure
//   anything — there is nothing to scan.
// - Sonar: it emits its own chirp and times the echo. It needs the live
//   sound; a recording cannot replay it.
// - Audio scope and audio autocorrelation: they need raw audio samples.
//   The recorder stores levels only, never raw audio, by design.
// - (In)elastic collision: it needs two moving carts and their masses.
//   A spike alone is not the analysis.
// - Submit to Sensor Database and the support page are not experiments.
//   The database one would upload data — the recorder never uploads.
//
// Privacy: the scan reads only the on-device recording file. It never
// uploads anything and never sends data anywhere. By default the
// recording is deleted after the scan (deleteRecordingAfter = true).
// The report itself is plain text and is saved next to the recording
// (scan-<timestamp>.txt) so Spiros can read it later.

import android.content.Context
import android.hardware.Sensor
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SensorScan(
    private val context: Context,
    private val phyphoxSensor: PhyphoxSensor
) {
    data class ExperimentResult(
        val id: String,
        val title: String,
        val covered: Boolean,
        val summary: String
    )

    data class ScanReport(
        val recordingName: String,
        val durationMs: Long,
        val sampleCount: Int,
        val results: List<ExperimentResult>,
        val reportText: String,
        val reportFile: File?
    )

    companion object {
        // Experiments that cannot be computed from a recording in v1, with
        // the honest reason that goes in the report.
        private val NOT_COVERED = mapOf(
            "camera-hsv" to "needs the camera — frames are far too heavy to record and replay in v1",
            "camera-luminance" to "needs the camera — frames are far too heavy to record and replay in v1",
            "camera_spectrum_luma" to "needs the camera — frames are far too heavy to record and replay in v1",
            "camera_stopwatch_hue" to "needs the camera — frames are far too heavy to record and replay in v1",
            "camera_stopwatch_luma" to "needs the camera — frames are far too heavy to record and replay in v1",
            "depth" to "needs the camera / LiDAR — frames are far too heavy to record and replay in v1",
            "spectroscopy" to "needs the camera — frames are far too heavy to record and replay in v1",
            "sensordb" to "not an experiment — it would upload data to phyphox.org, and the recorder never uploads",
            "support" to "not an experiment — information page",
            "strobe" to "produces light, it does not measure anything — there is nothing to scan",
            "tone_generator" to "produces sound, it does not measure anything — there is nothing to scan",
            "sonar" to "emits its own chirp and times the echo — it needs the live sound, a recording cannot replay it",
            "audio_scope" to "needs raw audio samples — the recorder stores levels only, never raw audio",
            "audio_autocorrelation" to "needs raw audio samples — the recorder stores levels only, never raw audio",
            "inelastic_collision" to "needs two moving carts and their masses — a spike alone is not the analysis"
        )
    }

    private val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }

    // Scan one recording file. By default the recording is deleted after
    // the scan — recordings are single-use by design.
    fun scan(file: File, deleteRecordingAfter: Boolean = true): ScanReport {
        val data = parseRecording(file)
        val experiments = phyphoxSensor.listExperiments()
        val results = experiments.map { info -> analyze(info, data) }
        val covered = results.count { it.covered }
        val text = buildString {
            append("MASS SCAN — ${file.name} (${"%.1f".format(Locale.US, data.durationMs / 1000.0)}s, ${data.sampleCount} samples)\n")
            append("Sensors recorded: ${data.sensorNames.joinToString(", ")}\n")
            append("Covered: $covered of ${results.size} experiments — results from the recording:\n")
            results.filter { it.covered }.forEach { append("• ${it.title}: ${it.summary}\n") }
            append("Not covered in v1:\n")
            results.filter { !it.covered }.forEach { append("• ${it.title}: ${it.summary}\n") }
            if (deleteRecordingAfter) {
                append("Recording deleted after scan.\n")
            } else {
                append("Recording kept at ${file.name} — delete it when done.\n")
            }
        }
        val reportFile = try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val out = File(recordingsDir, "scan-$stamp.txt")
            out.writeText(text)
            out
        } catch (_: Exception) {
            null
        }
        if (deleteRecordingAfter) {
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
        return ScanReport(file.name, data.durationMs, data.sampleCount, results, text, reportFile)
    }

    fun scanLatest(deleteRecordingAfter: Boolean = true): ScanReport? {
        val latest = latestRecording() ?: return null
        return scan(latest, deleteRecordingAfter)
    }

    fun latestRecording(): File? =
        recordingsDir.listFiles { f -> f.isFile && f.name.endsWith(".nervrec") }
            ?.maxByOrNull { it.lastModified() }

    // ---- parsing ----

    private data class Sample(val tMs: Long, val values: FloatArray)
    private data class GpsSample(val tMs: Long, val lat: Double, val lon: Double, val acc: Float, val alt: Double, val speed: Float)
    private data class MicSample(val tMs: Long, val rms: Double, val peak: Double, val freq: Double)

    private class RecordingData(
        val durationMs: Long,
        val sampleCount: Int,
        val sensorNames: List<String>,
        val samples: Map<Int, List<Sample>>,
        val gps: List<GpsSample>,
        val mic: List<MicSample>
    ) {
        fun of(type: Int): List<Sample> = samples[type] ?: emptyList()
    }

    private fun parseRecording(file: File): RecordingData {
        var durationMs = 0L
        var sampleCount = 0
        val sensorNames = mutableListOf<String>()
        val samples = mutableMapOf<Int, MutableList<Sample>>()
        val gps = mutableListOf<GpsSample>()
        val mic = mutableListOf<MicSample>()
        try {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                val obj = try {
                    JSONObject(trimmed)
                } catch (_: Exception) {
                    return@forEachLine
                }
                when (obj.optString("kind", "")) {
                    "header" -> {
                        durationMs = obj.optLong("durationMs", 0L)
                        val arr = obj.optJSONArray("sensors")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                sensorNames.add(sensorName(arr.optInt(i, -1)))
                            }
                        }
                        if (obj.optBoolean("microphone", false) && "microphone" !in sensorNames) {
                            sensorNames.add("microphone")
                        }
                    }
                    "sample" -> {
                        sampleCount++
                        val sensor = obj.optInt("sensor", -1)
                        val t = obj.optLong("t", 0L)
                        when (sensor) {
                            SensorHub.SENSOR_TYPE_GPS -> gps.add(
                                GpsSample(
                                    t, obj.optDouble("lat", 0.0), obj.optDouble("lon", 0.0),
                                    obj.optDouble("acc", -1.0).toFloat(),
                                    obj.optDouble("alt", 0.0), obj.optDouble("speed", 0.0).toFloat()
                                )
                            )
                            SensorHub.SENSOR_TYPE_MICROPHONE -> mic.add(
                                MicSample(
                                    t, obj.optDouble("rms", 0.0),
                                    obj.optDouble("peak", 0.0), obj.optDouble("freq", 0.0)
                                )
                            )
                            else -> {
                                val arr = obj.optJSONArray("v") ?: return@forEachLine
                                val values = FloatArray(arr.length()) { i -> arr.optDouble(i, 0.0).toFloat() }
                                samples.getOrPut(sensor) { mutableListOf() }.add(Sample(t, values))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return RecordingData(durationMs, sampleCount, sensorNames, samples, gps, mic)
    }

    private fun sensorName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "accelerometer"
        Sensor.TYPE_GYROSCOPE -> "gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "magnetic_field"
        Sensor.TYPE_LIGHT -> "light"
        Sensor.TYPE_PRESSURE -> "pressure"
        Sensor.TYPE_PROXIMITY -> "proximity"
        SensorHub.SENSOR_TYPE_GPS -> "gps"
        SensorHub.SENSOR_TYPE_MICROPHONE -> "microphone"
        else -> "sensor $type"
    }

    // ---- per-experiment analysis ----

    private fun analyze(info: PhyphoxSensor.ExperimentInfo, data: RecordingData): ExperimentResult {
        NOT_COVERED[info.id]?.let { reason ->
            return ExperimentResult(info.id, info.title, false, reason)
        }
        val summary = when (info.id) {
            "accelerometer" -> accelStats(data)
            "acc_spectrum" -> spectrumStats(data, Sensor.TYPE_ACCELEROMETER, "acceleration")
            "inclination" -> inclinationStats(data)
            "linear_accelerometer" -> linearAccelStats(data)
            "centripetal_acceleration" -> centripetalStats(data)
            "gyroscope" -> gyroStats(data)
            "roll" -> rollStats(data)
            "magnetometer" -> magStats(data)
            "mag_spectrum" -> spectrumStats(data, Sensor.TYPE_MAGNETIC_FIELD, "magnetic field")
            "magnetic_ruler" -> magRulerStats(data)
            "light" -> lightStats(data)
            "pressure" -> pressureStats(data)
            "elevator" -> elevatorStats(data)
            "pendulum" -> periodicStats(data, Sensor.TYPE_GYROSCOPE, "swing")
            "spring" -> periodicStats(data, Sensor.TYPE_ACCELEROMETER, "oscillation")
            "motion_stopwatch" -> motionStopwatch(data)
            "optical_stopwatch" -> lightStopwatch(data)
            "proximity_stopwatch" -> proximityStopwatch(data)
            "acoustic_stopwatch" -> audioStopwatch(data)
            "audio_amplitude" -> audioStats(data)
            "audio_spectrum" -> audioSpectrum(data)
            "frequency_history" -> frequencyHistory(data)
            "doppler" -> dopplerStats(data)
            "applause_multi" -> applauseStats(data)
            "gps" -> gpsStats(data)
            else -> familyFallback(info, data)
        }
        val covered = !summary.startsWith("no data")
        return ExperimentResult(info.id, info.title, covered, summary)
    }

    // Experiments not named above: compute what their sensors allow, or
    // say plainly there is no analysis for them in v1.
    private fun familyFallback(info: PhyphoxSensor.ExperimentInfo, data: RecordingData): String {
        val types = info.sensorTypes
        if (types.isEmpty()) return "no data — this experiment uses no phone sensor the recorder captures"
        val allPresent = types.all { type ->
            val android = phyphoxSensorTypeToAndroid(type)
            android != null && data.of(android).isNotEmpty()
        }
        if (!allPresent) return "no data — the recording has no samples for its sensor"
        return "recorded (${types.joinToString(", ")}) — no dedicated analysis for this experiment in v1, see the raw numbers in the recording"
    }

    private fun phyphoxSensorTypeToAndroid(type: String): Int? = when (type) {
        "accelerometer" -> Sensor.TYPE_ACCELEROMETER
        "linear_acceleration" -> Sensor.TYPE_LINEAR_ACCELERATION
        "gravity" -> Sensor.TYPE_GRAVITY
        "gyroscope" -> Sensor.TYPE_GYROSCOPE
        "magnetic_field" -> Sensor.TYPE_MAGNETIC_FIELD
        "pressure" -> Sensor.TYPE_PRESSURE
        "light" -> Sensor.TYPE_LIGHT
        "proximity" -> Sensor.TYPE_PROXIMITY
        "temperature" -> Sensor.TYPE_AMBIENT_TEMPERATURE
        "humidity" -> Sensor.TYPE_RELATIVE_HUMIDITY
        "attitude" -> Sensor.TYPE_ROTATION_VECTOR
        else -> null
    }

    // ---- analyses (all plain statistics on the recorded buffers) ----

    private fun accelStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_ACCELEROMETER)
        if (list.isEmpty()) return "no data — no accelerometer samples in this recording"
        val mags = list.map { magnitude(it.values) }
        return "mean |a| ${f(mags.average())} m/s², peak ${f(mags.maxOrNull() ?: 0.0)} m/s², " +
            "x ${axisRange(list, 0)}, y ${axisRange(list, 1)}, z ${axisRange(list, 2)}"
    }

    private fun spectrumStats(data: RecordingData, type: Int, label: String): String {
        val list = data.of(type)
        if (list.isEmpty()) return "no data — no $label samples in this recording"
        val mags = list.map { magnitude(it.values) }
        val mean = mags.average()
        val freq = dominantFrequency(mags, data.durationMs)
        return "dominant frequency ~${f(freq)} Hz (estimate from zero crossings), " +
            "mean ${f(mean)}, peak ${f(mags.maxOrNull() ?: 0.0)}"
    }

    private fun inclinationStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_ACCELEROMETER)
        if (list.isEmpty()) return "no data — no accelerometer samples in this recording"
        val mx = list.map { it.values.getOrElse(0) { 0f }.toDouble() }.average()
        val my = list.map { it.values.getOrElse(1) { 0f }.toDouble() }.average()
        val mz = list.map { it.values.getOrElse(2) { 0f }.toDouble() }.average()
        val pitch = Math.toDegrees(atan2(my, sqrt(mx * mx + mz * mz)))
        val roll = Math.toDegrees(atan2(mx, sqrt(my * my + mz * mz)))
        return "tilt from the recording's mean acceleration — pitch ${f(pitch)}°, roll ${f(roll)}°"
    }

    private fun linearAccelStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_ACCELEROMETER)
        if (list.isEmpty()) return "no data — no accelerometer samples in this recording"
        // Gravity estimate: a slow moving average of the acceleration.
        // Subtracting it leaves an estimate of the linear acceleration —
        // labelled as an estimate, not the phone's own linear sensor.
        val linear = removeGravity(list)
        val mags = linear.map { magnitude(it) }
        return "linear acceleration (estimate — gravity removed with a slow average, not the phone's own linear sensor): " +
            "mean |a| ${f(mags.average())} m/s², peak ${f(mags.maxOrNull() ?: 0.0)} m/s²"
    }

    private fun centripetalStats(data: RecordingData): String {
        val gyro = data.of(Sensor.TYPE_GYROSCOPE)
        val accel = data.of(Sensor.TYPE_ACCELEROMETER)
        if (gyro.isEmpty() || accel.isEmpty()) return "no data — needs gyroscope and accelerometer samples"
        val omegas = gyro.map { magnitude(it.values) }
        val linear = removeGravity(accel).map { magnitude(it) }
        return "rotation up to ${f(omegas.maxOrNull() ?: 0.0)} rad/s, linear acceleration up to " +
            "${f(linear.maxOrNull() ?: 0.0)} m/s² (centripetal part estimated from the same data)"
    }

    private fun gyroStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_GYROSCOPE)
        if (list.isEmpty()) return "no data — no gyroscope samples in this recording"
        val mags = list.map { magnitude(it.values) }
        return "mean rotation ${f(mags.average())} rad/s, peak ${f(mags.maxOrNull() ?: 0.0)} rad/s, " +
            "x ${axisRange(list, 0)}, y ${axisRange(list, 1)}, z ${axisRange(list, 2)}"
    }

    private fun rollStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_GYROSCOPE)
        if (list.isEmpty()) return "no data — no gyroscope samples in this recording"
        // Integrate angular velocity over time. The angle drifts over a
        // long recording — the report says so rather than hiding it.
        var angle = 0.0
        for (i in 1 until list.size) {
            val dt = (list[i].tMs - list[i - 1].tMs) / 1000.0
            if (dt in 0.0..1.0) angle += list[i].values.getOrElse(0) { 0f }.toDouble() * dt
        }
        return "rotation about the x-axis totals ~${f(Math.toDegrees(angle))}° over the recording " +
            "(integrated from the gyroscope — drifts over long recordings, so treat it as approximate)"
    }

    private fun magStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_MAGNETIC_FIELD)
        if (list.isEmpty()) return "no data — no magnetic-field samples in this recording"
        val mags = list.map { magnitude(it.values) }
        return "mean field ${f(mags.average())} µT, range ${f(mags.minOrNull() ?: 0.0)}–${f(mags.maxOrNull() ?: 0.0)} µT"
    }

    private fun magRulerStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_MAGNETIC_FIELD)
        if (list.isEmpty()) return "no data — no magnetic-field samples in this recording"
        val mags = list.map { magnitude(it.values) }
        val max = mags.maxOrNull() ?: 0.0
        val min = mags.minOrNull() ?: 0.0
        return "strongest field ${f(max)} µT, weakest ${f(min)} µT — the closest approach is where the field peaked"
    }

    private fun lightStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_LIGHT)
        if (list.isEmpty()) return "no data — no light samples in this recording"
        val values = list.map { it.values.getOrElse(0) { 0f }.toDouble() }
        return "mean ${f(values.average())} lx, range ${f(values.minOrNull() ?: 0.0)}–${f(values.maxOrNull() ?: 0.0)} lx"
    }

    private fun pressureStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_PRESSURE)
        if (list.isEmpty()) return "no data — no pressure samples in this recording"
        val values = list.map { it.values.getOrElse(0) { 0f }.toDouble() }
        val delta = (values.lastOrNull() ?: 0.0) - (values.firstOrNull() ?: 0.0)
        return "mean ${f(values.average())} hPa, change over the recording ${f(delta)} hPa"
    }

    private fun elevatorStats(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_PRESSURE)
        if (list.isEmpty()) return "no data — no pressure samples in this recording"
        val values = list.map { it.values.getOrElse(0) { 0f }.toDouble() }
        val delta = (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
        if (abs(delta) < 0.3) return "no elevator ride detected — pressure stayed within 0.3 hPa"
        // Rough barometric rule: ~8.4 m per hPa near sea level.
        val height = delta * 8.4
        return "pressure changed ${f(delta)} hPa — roughly ${f(abs(height))} m of height change " +
            "(rough estimate, ${if (height < 0) "down" else "up"})"
    }

    private fun periodicStats(data: RecordingData, type: Int, label: String): String {
        val list = data.of(type)
        if (list.isEmpty()) return "no data — no samples for this experiment in this recording"
        val mags = list.map { magnitude(it.values) }
        val period = dominantPeriod(mags, data.durationMs)
        if (period <= 0.0) return "no periodic $label found in this recording"
        return "periodic $label found — period ~${f(period)} s (about ${f(1.0 / period)} swings per second)"
    }

    private fun motionStopwatch(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_ACCELEROMETER)
        if (list.isEmpty()) return "no data — no accelerometer samples in this recording"
        val mags = list.map { magnitude(it.values) }
        val median = mags.sorted()[mags.size / 2]
        val spikes = mags.mapIndexedNotNull { i, m ->
            if (m > maxOf(15.0, median * 2.0)) list[i].tMs else null
        }
        return stopwatchLine(spikes, "motion spike")
    }

    private fun lightStopwatch(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_LIGHT)
        if (list.isEmpty()) return "no data — no light samples in this recording"
        val values = list.map { it.values.getOrElse(0) { 0f }.toDouble() }
        val steps = mutableListOf<Long>()
        for (i in 1 until values.size) {
            val prev = values[i - 1]
            if (prev > 0 && abs(values[i] - prev) / prev > 0.5 && list[i].tMs - list[i - 1].tMs <= 200) {
                steps.add(list[i].tMs)
            }
        }
        return stopwatchLine(steps, "light step")
    }

    private fun proximityStopwatch(data: RecordingData): String {
        val list = data.of(Sensor.TYPE_PROXIMITY)
        if (list.isEmpty()) return "no data — no proximity samples in this recording"
        val changes = mutableListOf<Long>()
        for (i in 1 until list.size) {
            val prev = list[i - 1].values.getOrElse(0) { 0f }
            val cur = list[i].values.getOrElse(0) { 0f }
            if ((prev <= 0f) != (cur <= 0f)) changes.add(list[i].tMs)
        }
        return stopwatchLine(changes, "proximity change")
    }

    private fun audioStopwatch(data: RecordingData): String {
        if (data.mic.isEmpty()) return "no data — no microphone levels in this recording"
        val rms = data.mic.map { it.rms }
        val median = rms.sorted()[rms.size / 2]
        val spikes = data.mic.mapIndexedNotNull { i, m ->
            if (m.rms > maxOf(0.3, median * 3.0)) m.tMs else null
        }
        return stopwatchLine(spikes, "clap spike")
    }

    private fun stopwatchLine(times: List<Long>, what: String): String {
        if (times.isEmpty()) return "no $what found in this recording"
        val shown = times.take(5).joinToString(", ") { "${"%.1f".format(Locale.US, it / 1000.0)}s" }
        val extra = if (times.size > 5) " (+${times.size - 5} more)" else ""
        return "trigger${if (times.size > 1) "s" else ""} found at $shown$extra"
    }

    private fun audioStats(data: RecordingData): String {
        if (data.mic.isEmpty()) return "no data — no microphone levels in this recording (mic may not have been available)"
        val rms = data.mic.map { it.rms }
        val peak = data.mic.map { it.peak }
        val loudest = data.mic.maxByOrNull { it.rms }
        return "mean level ${f(rms.average())}, peak ${f(peak.maxOrNull() ?: 0.0)}, " +
            "loudest at ${"%.1f".format(Locale.US, (loudest?.tMs ?: 0L) / 1000.0)}s (levels only — no raw audio stored)"
    }

    private fun audioSpectrum(data: RecordingData): String {
        if (data.mic.isEmpty()) return "no data — no microphone levels in this recording"
        val sounding = data.mic.filter { it.rms > 0.01 && it.freq > 0 }
        if (sounding.isEmpty()) return "no sound loud enough to measure a frequency in this recording"
        val freqs = sounding.map { it.freq }
        return "dominant frequency ~${f(freqs.average())} Hz (estimate from zero crossings, loud parts only), " +
            "range ${f(freqs.minOrNull() ?: 0.0)}–${f(freqs.maxOrNull() ?: 0.0)} Hz"
    }

    private fun frequencyHistory(data: RecordingData): String {
        if (data.mic.isEmpty()) return "no data — no microphone levels in this recording"
        val perSecond = data.mic.filter { it.rms > 0.01 && it.freq > 0 }
            .groupBy { it.tMs / 1000 }
            .mapValues { (_, v) -> v.map { it.freq }.average() }
            .toSortedMap()
        if (perSecond.isEmpty()) return "no sound loud enough to track a frequency in this recording"
        val track = perSecond.entries.joinToString(", ") { (s, f) -> "${s}s: ${f(f)} Hz" }
        return "frequency track — $track"
    }

    private fun dopplerStats(data: RecordingData): String {
        if (data.mic.isEmpty()) return "no data — no microphone levels in this recording"
        val sounding = data.mic.filter { it.rms > 0.01 && it.freq > 0 }
        if (sounding.size < 2) return "no sound loud enough to measure a frequency shift in this recording"
        val first = sounding.first().freq
        val last = sounding.last().freq
        val shift = last - first
        return "frequency ${f(first)} Hz at the start, ${f(last)} Hz at the end " +
            "(${if (shift < -5) "falling — a source moving away" else if (shift > 5) "rising — a source moving closer" else "no clear shift"})"
    }

    private fun applauseStats(data: RecordingData): String {
        if (data.mic.isEmpty()) return "no data — no microphone levels in this recording"
        val max = data.mic.maxOfOrNull { it.rms } ?: 0.0
        val mean = data.mic.map { it.rms }.average()
        return "loudest ${f(max)} RMS, mean ${f(mean)} RMS (levels only)"
    }

    private fun gpsStats(data: RecordingData): String {
        if (data.gps.isEmpty()) return "no data — no GPS fixes in this recording (location permission or GPS may have been off)"
        var distance = 0.0
        var maxSpeed = 0.0f
        for (i in 1 until data.gps.size) {
            val a = data.gps[i - 1]
            val b = data.gps[i]
            distance += haversine(a.lat, a.lon, b.lat, b.lon)
            if (b.speed > maxSpeed) maxSpeed = b.speed
        }
        val first = data.gps.first()
        val last = data.gps.last()
        return "tracked ${f(distance)} m over ${data.gps.size} fixes, max speed ${f(maxSpeed.toDouble())} m/s, " +
            "from ${f(first.lat)}, ${f(first.lon)} to ${f(last.lat)}, ${f(last.lon)}"
    }

    // ---- helpers ----

    private fun magnitude(values: FloatArray): Double {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v.toDouble()
        return sqrt(sum)
    }

    private fun axisRange(list: List<Sample>, axis: Int): String {
        val values = list.map { it.values.getOrElse(axis) { 0f }.toDouble() }
        return "${f(values.minOrNull() ?: 0.0)} to ${f(values.maxOrNull() ?: 0.0)}"
    }

    private fun dominantFrequency(mags: List<Double>, durationMs: Long): Double {
        if (mags.size < 4 || durationMs <= 0) return 0.0
        val mean = mags.average()
        var crossings = 0
        for (i in 1 until mags.size) {
            if ((mags[i - 1] - mean < 0) != (mags[i] - mean < 0)) crossings++
        }
        val seconds = durationMs / 1000.0
        return crossings / (2.0 * seconds)
    }

    private fun dominantPeriod(mags: List<Double>, durationMs: Long): Double {
        val freq = dominantFrequency(mags, durationMs)
        return if (freq > 0.05) 1.0 / freq else 0.0
    }

    // Gravity estimate: a centred moving average over ~1 s of samples.
    // Subtracting it from the raw acceleration leaves the linear part.
    private fun removeGravity(list: List<Sample>): List<FloatArray> {
        if (list.isEmpty()) return emptyList()
        val window = (list.size / 10).coerceAtLeast(1)
        return list.mapIndexed { i, sample ->
            val from = maxOf(0, i - window)
            val to = minOf(list.size, i + window + 1)
            val gx = (from until to).map { list[it].values.getOrElse(0) { 0f }.toDouble() }.average()
            val gy = (from until to).map { list[it].values.getOrElse(1) { 0f }.toDouble() }.average()
            val gz = (from until to).map { list[it].values.getOrElse(2) { 0f }.toDouble() }.average()
            floatArrayOf(
                (sample.values.getOrElse(0) { 0f } - gx).toFloat(),
                (sample.values.getOrElse(1) { 0f } - gy).toFloat(),
                (sample.values.getOrElse(2) { 0f } - gz).toFloat()
            )
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * Math.asin(sqrt(a))
    }

    private fun f(value: Double): String = "%.2f".format(Locale.US, value)
}
