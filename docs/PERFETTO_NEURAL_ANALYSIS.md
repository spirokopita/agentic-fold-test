# Neural transition Perfetto analysis — API 35 emulator

## Question

Which part of the seven-state workload is responsible for the reported frame misses, and does the
renderer optimization reduce comparable renderer-only cost?

## Hardware and traces

- Device: Android Studio `Medium_Phone` AVD, `sdk_gphone64_x86_64`, Android 15 / API 35.
- Package: `com.agentfold.app.recovery`.
- Pre-change trace: `app/build/reports/perfetto/neural-transition-before.perfetto-trace`
  (22,925,660 bytes).
- Post-change trace: `app/build/reports/perfetto/neural-transition-after.perfetto-trace`
  (20,775,410 bytes).
- Recorder: official Perfetto `record_android_trace`, 30 seconds, 64 MiB, categories
  `sched gfx view wm am freq`, app-scoped atrace.
- Trace processor: Perfetto v58.3.
- Trace integrity: no positive drop/loss/failure stats were returned.

Raw traces are retained locally under the ignored Gradle build directory and are not committed because
they total about 43 MiB. Re-run commands and validated queries are recorded below.

## Findings

- [SQL] In the pre-change capture trace, main-thread `Record View#draw()` accounted for 897.64 ms
  across 201 calls (4.47 ms average), whereas `postAndWait` accounted for 14,321.72 ms
  (71.25 ms average). `draw-VRI[MainActivity]` totalled 15,498.19 ms.
- [SQL] Frame-timeline jank was dominated by SurfaceFlinger CPU deadline misses and buffer stuffing.
- [INFERRED] The emulator/HWUI synchronization path, not Kotlin geometry alone, dominates the
  screenshot workload. PNG capture additionally performs `view.draw(Canvas(bitmap))` and
  `Bitmap.compress` on the Activity thread, so the original 40/109 ms result is not a clean
  animation-only benchmark.
- [SQL] The post-change traced capture sample was noisier: `Record View#draw()` averaged 7.01 ms and
  `postAndWait` 81.56 ms. It is not evidence of capture-workload improvement.
- [SQL] Three comparable no-capture runs provide the useful A/B signal. Their median CPU-frame median
  improved from 53 ms to 40 ms; median P95 improved from 200 ms to 150 ms; median deadline-miss rate
  improved from 49.07% to 34.87%; median rendered frames in the eight-second sample rose from 108 to
  151. GPU median remained 17–18 ms.
- [GAP] The emulator reports 4950 ms GPU sentinel buckets during some runs. They are not interpreted
  as literal GPU execution time.
- [GAP] No physical Fold, representative hardware GPU trace, or per-frame Java allocation trace was
  available.

## Implemented optimization

The renderer retains the same lane, step, strand and glow counts. Immutable sphere latitude sine/cosine
terms and tendril envelopes are precomputed. Angle-addition identities replace repeated latitude,
shifted-longitude and tendril sample trigonometry. A recurrence advances the evenly spaced tendril
phase. Visual captures confirm that both boundaries, packet field, sphere deformation, branches,
braids, infection progression and candles remain present.

## Validated queries

```sql
INCLUDE PERFETTO MODULE slices.with_context;
SELECT *
FROM thread_or_process_slice
LIMIT 0;

SELECT stats.name, stats.severity, stats.value
FROM stats
WHERE stats.value > 0
  AND regexp('drop|loss|fail', stats.name, 'i')
ORDER BY stats.value DESC
LIMIT 20;

SELECT
  thread_or_process_slice.name,
  count(*) AS calls,
  round(sum(thread_or_process_slice.dur) / 1e6, 2) AS total_ms,
  round(avg(thread_or_process_slice.dur) / 1e6, 2) AS avg_ms,
  round(max(thread_or_process_slice.dur) / 1e6, 2) AS max_ms
FROM thread_or_process_slice
JOIN process ON thread_or_process_slice.upid = process.upid
JOIN thread ON thread_or_process_slice.utid = thread.utid
WHERE process.name = 'com.agentfold.app.recovery'
  AND thread.is_main_thread = 1
  AND thread_or_process_slice.name IN (
    'Record View#draw()', 'draw-VRI[MainActivity]', 'postAndWait', 'traversal'
  )
GROUP BY thread_or_process_slice.name
ORDER BY thread_or_process_slice.name;

SELECT actual_frame_timeline_slice.jank_type, count(*) AS frames
FROM actual_frame_timeline_slice
JOIN process ON actual_frame_timeline_slice.upid = process.upid
WHERE process.name = 'com.agentfold.app.recovery'
GROUP BY actual_frame_timeline_slice.jank_type
ORDER BY frames DESC;
```

## Remaining questions

Repeat the no-capture harness and Perfetto trace on an authorised physical Fold. Capture allocation
counts with Android Studio Memory Profiler or a supported ART allocation source, then test fold/unfold,
background/resume and thermal/battery behaviour. Do not claim 60 FPS from the emulator results.
