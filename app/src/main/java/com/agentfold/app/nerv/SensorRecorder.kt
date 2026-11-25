package com.agentfold.app.nerv

// MAGI EVOLVER — Record (record once, scan afterwards), 25 Nov 2026.
//
// What this file is: one recording session that captures timestamped raw
// data from all passive sensors plus microphone levels into a single
// on-device file inside the app's filesDir. Default length is short
// (10 seconds, adjustable up to 60). Recording starts and stops on
// demand only — there is no always-on recording.
//
// How it works: it reuses the SensorHub. The hub reads each sensor once;
// the recorder just subscribes to the hub's stream and writes it to the
// file. It never registers its own sensor listener. The microphone is
// the one exception: a mic is a stream, not a sensor event, and only one
// mic stream can run at a time — so the recorder owns the single mic
// stream while a recording runs, and no other mic user runs during it.
//
// File format (JSON lines, one object per line):
//   {"kind":"header","startedAt":<ms>,"durationMs":<ms>,"sensors":[1,4,...],"microphone":true}
//   {"kind":"sample","t":<elapsed ms>,"sensor":1,"v":[x,y,z]}
//   {"kind":"sample","t":<elapsed ms>,"sensor":10001,"lat":...,"lon":...,"acc":...,"alt":...,"speed":...}
//   {"kind":"sample","t":<elapsed ms>,"sensor":10002,"rms":0.12,"peak":0.5,"freq":120.0}
//   {"kind":"footer","endedAt":<ms>,"samples":412}
// Sensor numbers are the Android Sensor.TYPE_* values (1 = accelerometer
// and friends); 10001 is GPS and 10002 is the microphone, matching
// SensorHub's constants.
//
// Privacy — this is the whole point of recording on-device:
// - Recordings stay in filesDir/recordings and are never uploaded
//   anywhere. There is no network call in this file at all.
// - They are deleted after the scan (SensorScan does that by default)
//   or on user request (delete/deleteAll).
// - Audio is stored as levels/frequencies only (RMS, peak, zero-crossing
//   frequency per 100 ms window). Raw audio samples are never stored,
//   never transcribed, never sent anywhere.
//
// Limits: the file is small (a 10 s recording is a few hundred lines).
// Recording while another recording runs is refused. Stopping early is
// fine — the file keeps whatever was captured.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SensorRecorder(private val context: Context, private val hub: SensorHub) {

    data class Recording(
        val file: File,
        val startedAtMs: Long,
        val durationMs: Long,
        val samples: Int,
        val sensors: List<String>
    )

    data class RecordResult(val recording: Recording?, val error: String?)

    companion object {
        const val DEFAULT_DURATION_MS = 10_000L
        const val MAX_DURATION_MS = 60_000L
        private const val MIC_SAMPLE_RATE_HZ = 16000
        private const val MIC_WINDOW_SAMPLES = 1600 // 100 ms at 16 kHz
        private val SENSOR_NAMES = mapOf(
            Sensor.TYPE_ACCELEROMETER to "accelerometer",
            Sensor.TYPE_GYROSCOPE to "gyroscope",
            Sensor.TYPE_MAGNETIC_FIELD to "magnetic_field",
            Sensor.TYPE_LIGHT to "light",
            Sensor.TYPE_PRESSURE to "pressure",
            Sensor.TYPE_PROXIMITY to "proximity",
            SensorHub.SENSOR_TYPE_GPS to "gps",
            SensorHub.SENSOR_TYPE_MICROPHONE to "microphone"
        )
    }

    private val lock = Any()
    private val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }

    @Volatile
    private var session: Session? = null

    private inner class Session(
        val file: File,
        val writer: BufferedWriter,
        val startedAtMs: Long,
        val durationMs: Long,
        val sensors: List<Int>,
        val micOn: Boolean
    ) {
        @Volatile var stopped = false
        @Volatile var samples = 0
        val done = CountDownLatch(1)
        var audioRecord: AudioRecord? = null
        var micThread: Thread? = null
        var timerThread: Thread? = null
    }

    fun isRecording(): Boolean = session != null

    // Record once, synchronously: blocks until the recording finishes
    // (default 10 s). This is what the phyphox_record tool calls — the
    // MAGI background thread waits, then the scan runs as the next tool.
    fun record(durationMs: Long = DEFAULT_DURATION_MS): RecordResult {
        val error = begin(durationMs)
        if (error != null) return RecordResult(null, error)
        val current = session ?: return RecordResult(null, "error: recording did not start.")
        val finished = current.done.await(current.durationMs + 5000L, TimeUnit.MILLISECONDS)
        if (!finished) {
            stop()
            return RecordResult(null, "error: recording did not finish in time.")
        }
        // stop() returns the summary; it was already called by the timer.
        val file = current.file
        return RecordResult(
            Recording(
                file, current.startedAtMs, current.durationMs,
                current.samples, current.sensors.mapNotNull { SENSOR_NAMES[it] }
            ),
            null
        )
    }

    // Start a recording in the background. Returns an error message, or
    // null when it started. Stops itself after durationMs.
    fun begin(durationMs: Long = DEFAULT_DURATION_MS): String? {
        synchronized(lock) {
            if (session != null) return "error: already recording — stop it first."
        }
        val clamped = durationMs.coerceIn(1000L, MAX_DURATION_MS)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(recordingsDir, "rec-$stamp.nervrec")
        val writer = try {
            BufferedWriter(FileWriter(file))
        } catch (e: Exception) {
            return "error: could not create the recording file (${e.message ?: "unknown error"})."
        }
        val startedAt = System.currentTimeMillis()
        val sensors = mutableListOf<Int>()
        val current = Session(file, writer, startedAt, clamped, sensors, micOn = false)
        synchronized(lock) { session = current }
        // Subscribe to the hub: one shared listener per sensor, the
        // recorder just writes the stream to the file.
        val subscribed = mutableListOf<Int>()
        for (type in SensorHub.PASSIVE_SENSORS) {
            if (hub.subscribe(type, "recorder", SensorHub.ReadingListener { reading ->
                writeSample(current, type, reading.values)
            })) {
                subscribed.add(type)
            }
        }
        if (hub.subscribe(SensorHub.SENSOR_TYPE_GPS, "recorder", SensorHub.ReadingListener { reading ->
            writeGpsSample(current, reading)
        })) {
            subscribed.add(SensorHub.SENSOR_TYPE_GPS)
        }
        if (subscribed.isEmpty()) {
            cleanupFailed(current)
            return "error: no sensor could be read on this phone right now."
        }
        sensors.addAll(subscribed)
        val micOn = startMic(current)
        try {
            val sensorList = sensors.joinToString(",")
            writer.write(
                "{\"kind\":\"header\",\"startedAt\":$startedAt,\"durationMs\":$clamped," +
                    "\"sensors\":[$sensorList],\"microphone\":$micOn}\n"
            )
            writer.flush()
        } catch (_: Exception) {
        }
        current.timerThread = Thread {
            try {
                Thread.sleep(clamped)
            } catch (_: InterruptedException) {
            }
            stop()
        }.also { it.start() }
        return null
    }

    // Stop the current recording early (or let the timer stop it). Returns
    // the summary, or null when nothing was recording.
    fun stop(): Recording? {
        val current = synchronized(lock) {
            val s = session ?: return null
            session = null
            s
        }
        current.stopped = true
        try {
            current.audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            current.audioRecord?.release()
        } catch (_: Exception) {
        }
        current.audioRecord = null
        try {
            current.micThread?.join(1000L)
        } catch (_: Exception) {
        }
        for (type in current.sensors) {
            hub.unsubscribe(type, "recorder")
        }
        val endedAt = System.currentTimeMillis()
        try {
            current.writer.write("{\"kind\":\"footer\",\"endedAt\":$endedAt,\"samples\":${current.samples}}\n")
            current.writer.flush()
            current.writer.close()
        } catch (_: Exception) {
        }
        current.done.countDown()
        return Recording(
            current.file, current.startedAtMs, current.durationMs,
            current.samples, current.sensors.mapNotNull { SENSOR_NAMES[it] }
        )
    }

    fun latestRecording(): File? = listRecordings().maxByOrNull { it.lastModified() }

    fun listRecordings(): List<File> =
        recordingsDir.listFiles { f -> f.isFile && f.name.endsWith(".nervrec") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File): Boolean {
        if (file.parentFile != recordingsDir) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun deleteAll(): Int {
        var count = 0
        listRecordings().forEach { if (delete(it)) count++ }
        return count
    }

    private fun cleanupFailed(current: Session) {
        synchronized(lock) {
            if (session === current) session = null
        }
        current.sensors.forEach { hub.unsubscribe(it, "recorder") }
        try {
            current.writer.close()
        } catch (_: Exception) {
        }
        try {
            current.file.delete()
        } catch (_: Exception) {
        }
        current.done.countDown()
    }

    private fun writeSample(current: Session, sensorType: Int, values: FloatArray) {
        if (current.stopped) return
        val elapsed = System.currentTimeMillis() - current.startedAtMs
        val line = buildString {
            append("{\"kind\":\"sample\",\"t\":").append(elapsed)
            append(",\"sensor\":").append(sensorType).append(",\"v\":[")
            values.forEachIndexed { i, v ->
                if (i > 0) append(",")
                append("%.4f".format(Locale.US, v))
            }
            append("]}\n")
        }
        writeLine(current, line)
    }

    private fun writeGpsSample(current: Session, reading: SensorHub.SensorReading) {
        if (current.stopped) return
        val location = reading.location ?: return
        val elapsed = System.currentTimeMillis() - current.startedAtMs
        val line = "{\"kind\":\"sample\",\"t\":$elapsed,\"sensor\":${SensorHub.SENSOR_TYPE_GPS}," +
            "\"lat\":${location.latitude},\"lon\":${location.longitude}," +
            "\"acc\":${if (location.hasAccuracy()) location.accuracy else -1}," +
            "\"alt\":${if (location.hasAltitude()) location.altitude else 0}," +
            "\"speed\":${if (location.hasSpeed()) location.speed else 0}}\n"
        writeLine(current, line)
    }

    private fun writeLine(current: Session, line: String) {
        synchronized(lock) {
            if (current.stopped) return
            try {
                current.writer.write(line)
                current.samples++
            } catch (_: Exception) {
            }
        }
    }

    // Microphone: levels and frequencies only, never raw audio. One 100 ms
    // window at a time: RMS level, peak level, zero-crossing frequency
    // estimate. Returns true when the mic is running.
    private fun startMic(current: Session): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val minBuf = try {
            AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (_: Exception) {
            return false
        }
        if (minBuf <= 0) return false
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                MIC_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (_: Exception) {
            return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            try {
                audioRecord.release()
            } catch (_: Exception) {
            }
            return false
        }
        current.audioRecord = audioRecord
        val buffer = ShortArray(MIC_WINDOW_SAMPLES)
        current.micThread = Thread {
            try {
                audioRecord.startRecording()
            } catch (_: Exception) {
                return@Thread
            }
            while (!current.stopped) {
                val read = try {
                    audioRecord.read(buffer, 0, buffer.size)
                } catch (_: Exception) {
                    -1
                }
                if (read <= 0) continue
                var sumSquares = 0.0
                var peak = 0
                var crossings = 0
                for (i in 0 until read) {
                    val s = buffer[i].toInt()
                    sumSquares += s.toDouble() * s.toDouble()
                    val abs = kotlin.math.abs(s)
                    if (abs > peak) peak = abs
                    if (i > 0 && (buffer[i - 1] < 0) != (s < 0)) crossings++
                }
                val rms = Math.sqrt(sumSquares / read) / 32768.0
                val peakNorm = peak / 32768.0
                // Zero-crossing frequency estimate — only meaningful when
                // there is actual sound; silence reports 0.
                val freq = if (rms > 0.01) crossings * MIC_SAMPLE_RATE_HZ.toDouble() / (2.0 * read) else 0.0
                val elapsed = System.currentTimeMillis() - current.startedAtMs
                writeLine(
                    current,
                    "{\"kind\":\"sample\",\"t\":$elapsed,\"sensor\":${SensorHub.SENSOR_TYPE_MICROPHONE}," +
                        "\"rms\":${"%.4f".format(Locale.US, rms)}," +
                        "\"peak\":${"%.4f".format(Locale.US, peakNorm)}," +
                        "\"freq\":${"%.1f".format(Locale.US, freq)}}\n"
                )
            }
        }.also { it.start() }
        return true
    }
}
