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

API 35 Medium_Phone measurements:

- Cold launch TotalTime samples: 4370, 1539, 1837, 1948, 1902 ms (median 1902 ms).
- Warm launch WaitTime samples: 98, 205, 144, 103, 105 ms (median 105 ms).
- Hot launch WaitTime samples: 26, 34, 27, 21, 30 ms (median 27 ms).
- During all-seven-state capture: 40 ms median / 109 ms P95 CPU frame time; 16 ms median / 22 ms P95 GPU time; 178/644 modern deadline misses (27.64%).
- In-flight memory: 59,928 KiB total PSS; 9,332 KiB Java heap PSS; 29,472 KiB native heap PSS.
- No 60 FPS claim is made. Physical Fold profiling and allocation tracing remain outstanding.

## Rotation-stall diagnosis

The earlier apparent rotation hang was not reproduced as an application deadlock or lifecycle failure. UTP continued after the IDE RPC timed out, and a later invocation waited behind that process. The isolated rotation test completed normally in about ten seconds.

## Validation

- Focused rotation test: 1 passed, 0 failed, 0 skipped, 0 unfinished.
- Diagnostic full instrumentation: 20 passed, 0 failed, 0 skipped, 0 unfinished.
- Final post-change instrumentation: 20 passed, 0 failed, 0 skipped, 0 unfinished.
- Unit tests: 19 passed, 0 failed, 0 skipped.
- All five PDF instrumentation tests passed, including the corrected runtime-tool registry assertion.
- Fresh seven-state capture test: 1 passed; captures retained in `docs/neural-evidence/`.

## Known limitations and outstanding validation

- The 24 manifest-listed reference PNG/GIF assets and their ZIP are not present in the repository or accessible local storage. Two loose files in Pictures were inspected and are unrelated dashboard/flow images, not valid seven-state neural-orb references. Reference-image fidelity remains unverified.
- The transition capture workload is janky on the API 35 x86_64 emulator; representative physical Fold CPU/GPU, allocation, battery, fold/unfold, and stability measurements remain required.
- Owner-driven voice, widget, TalkBack/accessibility, live-provider, SAF export, permission, and device-automation checks remain outstanding.
- Production signing/migration remains outside this PR.
- This PR must remain draft until the outstanding device and integration gates are completed.

## Evidence

See `docs/NEURAL_UI.md`, `docs/neural-evidence/`, and `app/build/outputs/androidTest-results/connected/debug/`.
