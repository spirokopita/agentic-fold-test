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

## Real event integration and delivery build

Public runtime stages and voice-service phases now pass through a small visual-only adapter. Listening
uses the thinking field; planning, provider calls, verification and validated tool execution use
internal thinking; completion, cancellation and errors return to the inactive thinking field. STOP
also exits development preview before revoking work. No peer, firewall, infection or external-endpoint
telemetry is invented; those scenes remain explicitly simulated.

Focused checks added after the historical 21/21 instrumentation and 19/19 unit runs:

- signal-adapter unit tests: 3/3 passed;
- STOP exits preview: 1/1 instrumentation passed;
- rotation preserves STOP: 1/1 instrumentation passed;
- debug assembly, install, cold launch and background/hot resume passed on Medium_Phone API 35.

Git-ignored delivery APK: `delivery/AgentFold-Recovery-0.7.0-debug.apk`

SHA-256: `8cc1acc16c470b4a7f483a8859b4723dcd4d85e15ee4cb41311f73988defad53`

Package/version: `com.agentfold.app.recovery`, 0.7.0 (11), min API 29, target API 35.

Signer certificate SHA-256:
`4303c489827fc98c319be33083de6f83199e017bfbf09b9bccf08295e7496718`.

The package updates only an installation with the same recovery application ID, signing certificate
and compatible version code. It does not replace legacy `com.agentfold.app` or owner-signed builds.
Physical Fold, real reference assets and owner-dependent integration checks remain outstanding.

## Requirement reconciliation and visual completion pass

A source/render audit found and repaired concrete gaps beyond the earlier capture check:

- independently varied thought-packet phase, speed, length and radial spacing;
- six explicitly simulated endpoint nodes with unavailable/installed/launchable/integrated states and
  selectively growing branches only for integrated endpoints;
- moving local conflict-pressure packets over the retained blue/purple/red braid;
- progressive smoothed infection and colour-balance inputs;
- adaptive cover/phone/inner renderer height and narrow stacked controls;
- automatic legacy fallback callback after renderer failure;
- process-local visual reset across dashboard, notification and accessibility STOP paths.

Fresh seven-state captures and seven representative mid-transition frames are committed under
`docs/neural-evidence/`. All 42 directed state-pair transitions retained visible geometry; rapid
interruptions settled on the final requested state. Focused checks passed: 4 controller unit tests,
seven-state capture, all-pairs transition/interruptions, notification-style STOP reset, and debug/test
APK assembly. Complete suites were not repeated; historical 21/21 instrumentation and 19/19 unit
results remain unchanged evidence.

Cover-like 904×2316 and inner-like 2208×1840 emulator layouts were inspected, then the emulator was
restored to 1080×2400. These are layout checks, not physical Fold validation.

The original archive is still inaccessible: this session has no ChatGPT Library file API and policy
prohibits automating the ChatGPT desktop app. The exact required transfer is
`AgenticFold-AI-UI-All-Chat-Images-2026-11-13.zip`. Reference fidelity, physical Fold profiling and
owner integrations remain outstanding; keep this PR draft.
