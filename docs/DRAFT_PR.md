# Neural dial UI overhaul

## Summary

This draft replaces the live Activity visualization with a bounded, procedural neural-dial renderer while retaining the legacy renderer as a selectable fallback. It preserves the fixed STOP path, voice and widget entry points, accessibility approval gates, provider/file tooling, permissions, lifecycle recovery, and existing workspaces.

The renderer exposes seven explicitly labelled development simulations:

1. thinking field
2. internal thinking
3. external exploration
4. peaceful communication
5. conflict
6. our AI infected
7. other AI taken over

Preview data is visual-only. It cannot dispatch tools, contact providers or peers, grant permissions, start autonomous actions, or bypass STOP.

## Renderer and performance work

- Hardware-accelerated native Canvas renderer with bounded scene complexity.
- One-second smoothstep transitions that can be interrupted from current weights.
- Animation pauses when detached, backgrounded, hidden, offscreen, or reduced motion disables animators.
- Cached condensed typeface and balance-keyed gradient.
- Removed per-frame temporary weight-array allocation.
- Hoisted invariant arc bounds out of the packet loop.
- Precomputed immutable sphere latitude and tendril-envelope trigonometric terms.
- Replaced repeated shifted-angle calculations with angle-addition identities.
- Advanced evenly spaced tendril samples with a sine/cosine recurrence while retaining 81 points per strand.

API 35 Medium_Phone measurements:

- Cold launch TotalTime samples: 4370, 1539, 1837, 1948, 1902 ms (median 1902 ms).
- Warm launch WaitTime samples: 98, 205, 144, 103, 105 ms (median 105 ms).
- Hot launch WaitTime samples: 26, 34, 27, 21, 30 ms (median 27 ms).
- Historical all-state PNG-capture sample: 40 ms median / 109 ms P95 CPU; 16/22 ms GPU; 178/644 deadline misses. PNG drawing/compression occurs on the Activity thread and is not a clean renderer-only benchmark.
- Three-run no-capture median before → after: CPU median 53 → 40 ms; CPU P95 200 → 150 ms; deadline-miss rate 49.07% → 34.87%; rendered frames 108 → 151; GPU median 18 → 17 ms.
- Perfetto shows emulator/HWUI `postAndWait` and buffer stuffing dominate the capture workload. A single post-change traced capture was noisier, so it is retained as variability evidence rather than claimed as improvement.
- No 60 FPS claim is made. Physical Fold profiling and allocation tracing remain outstanding.

## Rotation-stall diagnosis

The earlier apparent rotation hang was not reproduced as an application deadlock or lifecycle failure. UTP continued after the IDE RPC timed out, and a later invocation waited behind that process. The isolated rotation test completed normally in about ten seconds.

## Validation

- Focused rotation test: 1 passed, 0 failed, 0 skipped, 0 unfinished.
- Diagnostic full instrumentation: 20 passed, 0 failed, 0 skipped, 0 unfinished.
- Historical post-allocation-cleanup instrumentation: 20 passed, 0 failed, 0 skipped, 0 unfinished.
- Current post-trigonometry instrumentation: 21 passed, 0 failed, 0 skipped, 0 unfinished.
- Current unit tests: 19 passed, 0 failed, 0 skipped.
- All five PDF instrumentation tests passed, including the corrected runtime-tool registry assertion.
- Fresh seven-state capture test: 1 passed; captures retained in `docs/neural-evidence/`.

## Known limitations and outstanding validation

- The 24 manifest-listed reference PNG/GIF assets and their ZIP are not present in the repository or accessible local storage. Two loose files in Pictures were inspected and are unrelated dashboard/flow images, not valid seven-state neural-orb references. Reference-image fidelity remains unverified.
- The transition capture workload is janky on the API 35 x86_64 emulator; representative physical Fold CPU/GPU, allocation, battery, fold/unfold, and stability measurements remain required.
- Owner-driven voice, widget, TalkBack/accessibility, live-provider, SAF export, permission, and device-automation checks remain outstanding.
- Production signing/migration remains outside this PR.
- This PR must remain draft until the outstanding device and integration gates are completed.

## Evidence

See `docs/NEURAL_UI.md`, `docs/PERFETTO_NEURAL_ANALYSIS.md`, `docs/neural-evidence/`, and `app/build/outputs/androidTest-results/connected/debug/`. Draft PR: https://github.com/spirokopita/agentic-fold-test/pull/2
