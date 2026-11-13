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
4. Tests: controller boundary tests and seven-state Android capture test added; not yet run.
5. Local environment: Android Studio installed by owner; setup wizard downloading SDK components.
   Bundled Java is 25.0.4. Existing Minecraft Java 17.0.8 can launch Gradle 8.14.3, but is not a
   conventional full JDK installation. Use a dedicated supported JDK for repeatable builds.
6. Build/regression/visual evidence: pending. Never claim an APK, FPS result or comparison exists.
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
