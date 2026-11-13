# Neural dial implementation and handoff

## Source recovery

The `main` branch at 93aaf0d contained only a source ZIP, README and image manifest.
The source ZIP blob 1e574719a9dac270fa6dfea53714523d67366558 was recovered byte-for-byte
through GitHub's base64 file API and extracted as the Android project at the repository root.
The integration branch da1a7a7 adds TapTarget.kt and TapTargetTest.kt; both were preserved.
No service, permission declaration, provider, widget, phone-tool gate or STOP implementation
was replaced. The previous ActivityNetworkView remains selectable as a fallback.

## Architecture

`ui/neural/NeuralController.kt` is the signal boundary. Its immutable snapshots contain
visual parameters only. Setters accept calls from backend threads; the View marshals updates
to the UI thread. No controller action dispatches a tool, changes permissions or contacts a peer.
The Activity owns its controller. A future longer-lived backend should restore current signals
on attachment; preview data deliberately does not survive Activity recreation.

`NeuralDialView.kt` draws procedural vector paths into a circular 1000-unit coordinate system,
scaled to the smaller view dimension. Native hardware Canvas acceleration is used when Android
provides it, with software-compatible layered strokes for bloom. No Compose or shader dependency
was added to this native-View application. Spherical projection uses sampled deformed meridians;
it is a projected procedural scene, not a volumetric ray marcher.

## State specification

| State | Geometry and motion | Input / truth boundary |
| --- | --- | --- |
| thinking field | two cyan boundaries, 32 independent white arc packets, black centre | idle public runtime state; motion is decorative |
| internal thinking | twisting projected sphere, neural branching, orbiting thoughts | busy public execution stage, never private model reasoning |
| external exploration | small central orb; curved branching paths to peripheral endpoints | only supplied available endpoints connect; no app inventory or permission inference |
| peaceful communication | distinct blue/red spheres; sinusoidal braided strands with travelling white packets | preview until an authorised integration supplies peer signals |
| conflict | denser moving interference along blue/purple/red colour boundary | visual only; no attack mechanisms |
| our ai infected | dull blue shell, red internal paths and meridians | explicitly supplied intensity; not a security diagnosis |
| other ai taken over | red shell, blue internal paths and meridians | explicitly supplied intensity; not a security diagnosis |

States share one scene and interpolate seven weights using a one-second smoothstep transition.
Interrupted transitions start from current weights. Local position/radius, sphere detail and peer
visibility evolve with those weights. Pair-only elements fade into the same viewport.

Firewall candles use configurable label/value/simulated fields. Missing values display a dash;
they are never treated as zero security. Preview metrics have a visible simulation label.
Percentages are clamped and nonfinite inputs rejected. The default API does not define what a
percentage measures. Platform sans-serif-condensed typography requires no bundled font license.

## Lifecycle and performance

Frame scheduling uses display-synchronised callbacks. It stops when detached, host-stopped,
hidden, offscreen or reduced-motion settings disable animation. Power Saver reduces drawing
detail and schedules at about 30 FPS. Normal mode targets display refresh; 60 FPS has not yet
been measured. Geometry is bounded (12 endpoint nodes, 24 meridians, 12 strands). CPU/GPU and
allocation profiling on a physical Fold remain required. Draw failures stop the renderer and
leave functional controls and the selectable old activity view outside its failure boundary.

## Reference and visual acceptance ledger

The GitHub reference manifest explicitly says binary assets are pending upload. Tree inspection
confirmed zero PNG/GIF references in either audited branch. The 49,149,556-byte reference ZIP
was resolved from its supplied Library identity, but transfer could not run because local
Python is unavailable. No source image or GIF has been visually inspected in this implementation
session. Reference comparison is therefore UNVERIFIED for all seven states. Do not substitute
the manifest or synthetic captures for this comparison. Recover the ZIP, inspect all 16 PNGs
and 8 GIFs, organise them under assets/ai-ui/chat-2026-11-13, and compare emulator captures.

## Validation status and exact next actions

1. Source recovery and inspection: completed; preserve the original source ZIP in Git history.
2. Rendering foundation and seven states: source implemented, compilation and appearance pending.
3. Transition engine and Activity integration: source implemented; native fallback retained.
4. Tests: all three controller boundary tests passed using Kotlin 2.0.21 and JUnit 4.13.2.
   The renderer and controller also compiled directly against the installed Android 37.0 API.
   This is not an APK build or Android runtime verification. Seven-state capture tests have not run.
5. Local environment: Android Studio installed by owner; setup wizard downloading SDK components.
   Bundled Java is 25.0.4. Existing Minecraft Java 17.0.8 can launch Gradle 8.14.3, but is not a
   conventional full JDK installation. Use a dedicated supported JDK for repeatable builds.
6. Full build/regression/visual evidence: pending. Gradle starts but its daemon connection is
   blocked in this execution environment. Direct Kotlin compilation works. Never claim an APK,
   FPS result or reference comparison exists. Added a Gradle wrapper pinned to distribution
   8.11.1 (bootstrap JAR from cached official Gradle 8.14.3, Apache license included), plus a
   dedicated GitHub validation workflow with compilation, lint, tests and emulator captures.
7. Next: finish SDK setup, install Android platform 35 and Build Tools 35.0.0, select JDK 17,
   run testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest, then run instrumentation.
8. Pull neural-evidence PNGs from app external files after NeuralRenderingTest, inspect each,
   compare to recovered references, repair verified failures and rerun affected checks.
9. On physical hardware verify STOP, voice ON/OFF, widget, accessibility approval/expiry,
   provider configuration, file tools, resize, rotation, backgrounding and reduced motion.

## Changelog

- Recovered complete v0.7.0 application source and integration utility/tests.
- Added bounded visual controller, explicit preview mode and procedural seven-state engine.
- Integrated neural live activity alongside legacy fallback; retained all functional workspaces.
- Changed shared visual palette to OLED black/cyan; preserved danger colours and readable controls.
- Added controller and Android bitmap-capture tests. Reference, build and runtime verification pending.

## Remaining visual limitations

Canvas sphere geometry and bloom are an initial procedural implementation; high-detail reference
fidelity is unverified. Infection setter updates are currently immediate within a state; state
transitions progressively expose infection, but continuous parameter smoothing still needs work.
External exploration has no live endpoint adapter yet, so live mode never invents connected apps.
Conflict colour pressure is controllable but is not tied to real peer telemetry. Preview selection
is a native dialog outside the circular scene. Functional task panels remain available for safety
and compatibility rather than being rewritten as a new app shell.

## Local API 35 validation and performance follow-up

The stalled validation was recovered without terminating unrelated processes. Host diagnostics found
no remaining project Gradle, UTP, Java test, or emulator process; only the shared ADB server remained.
The retained UTP log recorded five passes and `DashboardSmokeTest.rotationPreservesStopPath` starting,
but no failure. `Medium_Phone` was started cleanly, reported boot complete, was woken, and the debug
app installed and visibly opened.

Evidence is retained under `app/build/outputs/androidTest-results/connected/debug/`, including
`Medium_Phone(AVD) - 15/testlog/test-results.log`, `Medium_Phone(AVD) - 15/utp.0.log`, and
`TEST-Medium_Phone(AVD) - 15-_app-.xml`. Android Studio also retained detailed focused/full build
transcripts as `shell-11.log` and `shell-12.log` in its session shell-log directory.

With Java 21.0.11 selected explicitly, the focused command was:

`gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agentfold.app.DashboardSmokeTest#rotationPreservesStopPath --info --stacktrace --no-daemon`

It completed in 1 minute 7 seconds: 1 passed, 0 failed, 0 skipped, 0 unfinished. The test itself
completed about ten seconds after instrumentation began. There was no deadlock, lifecycle defect,
UI-idleness wait, graphics starvation, ANR, or assertion failure. The prior symptom was Gradle/UTP
continuing after the IDE tool's five-minute RPC timeout, followed by another invocation waiting behind
it. No rotation or STOP-path code change was justified.

The diagnostic full instrumentation suite completed in 2 minutes 14 seconds, and the final post-change
suite completed in 1 minute 59 seconds: 20 passed, 0 failed, 0 skipped, 0 unfinished in each run. This
includes all five PDF tests, the corrected runtime tool registry assertion, seven-state capture,
workspace navigation, rotation and fixed-STOP checks. Unit tests also pass: 19 passed, 0 failed,
0 skipped. Manual checks confirmed fallback selection, background/resume,
reduced-motion rendering, and a visible fixed STOP control.

Cold `am start -W -S` samples on `Medium_Phone` were 1458 ms before the allocation cleanup and
2140 ms afterwards. These two emulator samples are noisy and do not demonstrate a startup-time
improvement. Five-second `dumpsys gfxinfo` windows reported 24-25 ms median CPU frame time,
18 ms median GPU time, and 1.24-1.55% modern frame-deadline misses after launch. The legacy jank
percentage is not used because it applies a 16.7 ms threshold inconsistent with the emulator's
observed cadence. No 60 FPS claim is made.

Source inspection found avoidable draw-loop allocations rather than a lifecycle scheduling defect:
`sliceArray()` allocated a temporary weight array every frame, `Typeface.create()` ran for every
label, and pair-state gradients and their colour/position arrays were rebuilt for every strand on
every frame. `NeuralDialView` now sums the four pair weights directly, caches the condensed typeface,
reuses a balance-keyed `LinearGradient`, and moves an invariant arc bound update outside its loop.
Geometry, seven states, transitions, black negative space, neural paths, firewall candles, preview
simulation labels, reduced-motion handling and frame scheduling are unchanged.

Reference-image fidelity remains UNVERIFIED until the real assets are recovered, inspected and
compared. Physical Fold profiling and owner-driven voice, widget, accessibility, provider, permission
and device-automation checks also remain outstanding; their implementations and security gates were
not changed.

## Integration-readiness cycle — 2026-11-13

Branch `feature/neural-dial-ui` was inspected clean at commit
`f69c3cce3ba2cc0a9c7247e059cb79c43f2a78cb`. The branch is pushed and the verified comparison URL is
<https://github.com/spirokopita/agentic-fold-test/compare/main...feature/neural-dial-ui?expand=1>.
No existing PR could be queried and no draft PR could be created because GitHub CLI has no authenticated
session and the browser access host is unavailable. The exact draft body is retained in
`docs/DRAFT_PR.md`; PR creation is not claimed complete.

### Fresh visual evidence and fidelity matrix

The all-state capture test was rerun directly on `Medium_Phone` (API 35, x86_64) and passed 1/1 in
18.664 seconds. Seven PNGs are retained under `docs/neural-evidence/`. Each capture is visibly labelled
`simulation`; preview teardown and Activity recreation restore a non-preview snapshot.

| Acceptance point | Implementation evidence | Actual reference comparison |
| --- | --- | --- |
| Pure black background / negative space | Present in all seven captures; thinking-field centre assertion passed | UNRESOLVED — source reference absent |
| Extremely thin neon-blue circular boundaries | Present in thinking field and orb shells | UNRESOLVED — source reference absent |
| Moving white thought packets | Packet positions render in thinking field, internal orbit and peer strands | UNRESOLVED — GIF source absent; static PNGs do not prove motion fidelity |
| Internal 3D neural-sphere transformation | Projected meridians and neural branches visible in `internal_thinking.png` | UNRESOLVED — source reference absent |
| External exploration and app-node connections | Authorised demo endpoint and curved connection visible in `external_exploration.png` | UNRESOLVED — source reference absent |
| Blue local / red remote relationship | Distinct spheres visible in four peer-state captures | UNRESOLVED — source reference absent |
| Neural connection strands | Braided blue/purple/red paths visible in peer-state captures | UNRESOLVED — source reference absent |
| Separate firewall candle gauges | Separate blue/red simulated gauges render in peer-state captures | UNRESOLVED — source reference absent |
| All seven states | Seven named implementation captures inspected | UNRESOLVED — none of the 24 manifest-listed binaries is accessible |

The only loose candidates found were `C:\Users\spiro\Pictures\ai fold ref 1.png` (a human-brain
dashboard) and `ai fold ref 5.png` (a multicolour flow diagram). Both were visually inspected and are
not the manifest-listed seven-state neural-orb references, so they were not used to claim fidelity.

### Bounded performance measurements

Five API 35 emulator launch samples were recorded without clearing app data:

- cold process starts, `TotalTime`: 4370, 1539, 1837, 1948, 1902 ms; median 1902 ms;
- warm launches from Home, `WaitTime`: 98, 205, 144, 103, 105 ms; median 105 ms;
- hot foreground launches, `WaitTime`: 26, 34, 27, 21, 30 ms; median 27 ms.

During the seven-state transition/capture workload, `dumpsys gfxinfo` reported 644 frames, 178 modern
deadline misses (27.64%), CPU frame time 40 ms median / 109 ms P95, and GPU time 16 ms median / 22 ms
P95. In-flight `dumpsys meminfo` reported 59,928 KiB total PSS, 9,332 KiB Java-heap PSS and 29,472 KiB
native-heap PSS. Renderer-reported graphics memory was 111.46 KiB CPU and 104.00 MiB GPU, of which
73.94 MiB was purgeable. These emulator results demonstrate transition-load jank and do not establish
60 FPS or physical-device performance. A per-frame allocation trace was not available, so source-level
allocation removals are not presented as measured allocation counts.

The capture test demonstrates every transition settled within its bounded 1.3-second observation
window. The previously verified full suite remains the evidence for background/resume, navigation,
rotation, reduced motion, fallback selection and STOP visibility. Fold/unfold cannot be measured on
this fixed Medium_Phone emulator.

### Safety and integration audit

Search of all preview call sites confirms previews only call `NeuralController.preview` or
`endPreview`. The controller owns visual data only and has no service, network, provider, permission
or tool-dispatch dependency. The dialog title explicitly says `visual simulation — no network actions`.
Live integration maps only public busy/idle runtime stages into thinking visuals. Voice, widget,
accessibility service, phone-tool approvals, provider/file tools and fixed STOP remain separate and
unchanged.

Completed: API 35 build/install/launch, seven preview modes, focused rotation, 20-test final
instrumentation suite, 19 unit tests, five PDF tests, fallback/reduced-motion/background checks,
fresh capture inspection, emulator launch/frame/memory sampling, and preview isolation audit.

Outstanding: authenticated draft PR creation; recovery of all 24 real reference assets; physical Fold
CPU/GPU/allocation/battery/fold testing; owner voice/widget/TalkBack/live-provider/SAF/permission and
device-automation checks; production signing/migration.

**Exact next engineering action:** authenticate GitHub in an authorised browser or GitHub CLI, create
the draft PR using `docs/DRAFT_PR.md`, then recover
`AgenticFold-AI-UI-All-Chat-Images-2026-11-13.zip` and perform an asset-by-asset visual/motion
comparison before any renderer fidelity changes.
