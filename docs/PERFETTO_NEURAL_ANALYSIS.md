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

## Post-detail repeat — 2026-11-14

The existing no-capture harness was rerun three times on `emulator-5554`, with frame statistics reset
after instrumentation launched and collected during a bounded six-second window. Runs were strictly
sequential; no PNG capture, compression, overlapping Gradle/UTP session or process-command-line dump
was involved. All three focused workloads passed.

| Run | Frames | CPU median | CPU P95 | Deadline misses | GPU median | GPU P90 | Test time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 209 | 53 ms | 150 ms | 104/209 (49.76%) | 18 ms | 25 ms | 15.213 s |
| 2 | 144 | 93 ms | 150 ms | 128/144 (88.89%) | 17 ms | 24 ms | 15.784 s |
| 3 | 137 | 113 ms | 150 ms | 130/137 (94.89%) | 17 ms | 23 ms | 15.919 s |
| median | 144 | 93 ms | 150 ms | 88.89% | 17 ms | 24 ms | 15.784 s |

This is materially worse than the prior post-trigonometry median (40 ms CPU median, 150 ms P95,
34.87% misses), while GPU median remains effectively unchanged. It therefore confirms the earlier
single noisy detail-pass result as an emulator regression signal, but does not identify whether the
cause is renderer recording, emulator scheduling, or HWUI synchronization.

The standalone `record_android_trace` and `trace_processor_shell` executables are absent from PATH and
the SDK. This statement was subsequently narrowed by the installed-resource investigation below:
the emulator itself provides `perfetto`, and Android Studio bundles a gRPC trace-processor daemon.
No scene detail was removed. Representative Fold profiling remains required before accepting or
rejecting design-preserving renderer changes.

## Installed-resource trace investigation — 2026-11-15

The interrupted processor launch was audited before any restart. The IDE reported no owned shell
session, and a name-only process check found no running `trace_processor_daemon`; the earlier attempt
had not survived. Its startup failure was then reproduced exactly: Android Studio's daemon requires
an explicit LLVM symbolizer path.

Installed resources were sufficient to start the processor:

- daemon: `C:\Program Files\Android\Android Studio\plugins\android\resources\trace_processor_daemon\trace_processor_daemon.exe`;
- symbolizer: `C:\Program Files\Android\Android Studio\plugins\android-ndk\resources\lldb\bin\llvm-symbolizer.exe`;
- verified startup: the daemon listened on `127.0.0.1:65341` and shut down normally after a
  one-second inactivity timeout.

The emulator's built-in Perfetto recorder captured a 20-second app-scoped trace using
`sched gfx view wm am freq` while the no-capture instrumentation workload ran. The focused workload
passed 1/1 in 18.412 seconds. Perfetto reported a normal connection to `traced` and wrote the trace:

- ignored local path:
  `app/build/reports/perfetto/neural-current-2026-11-15.perfetto-trace`;
- size: 1,197,430 bytes;
- SHA-256: `14e86446a8b9f28e7b2d749a0841ebc459c6062417492fe8be19c1427cba9deb`.

Analysis is not yet complete. No installed `grpcurl` or `grpc_cli` is available, Python is absent,
and the bundled daemon exposes gRPC rather than a standalone SQL CLI. Therefore the trace has not
been submitted to the daemon and none of the validated SQL queries above has been run against it.
No claim is made about current `Record View#draw()`, `postAndWait`, VRI or frame-timeline counts.

Exact next performance action: open this trace through Android Studio's profiler client or provide a
compatible installed gRPC/trace-processor client, run the recorded SQL queries, and only then decide
whether a renderer change is justified. Physical Fold tracing remains mandatory before an FPS claim.
