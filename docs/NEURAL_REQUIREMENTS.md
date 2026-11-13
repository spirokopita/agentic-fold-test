# Neural interface requirement ledger

Status vocabulary:

- **verified** — source and current emulator evidence were inspected for the stated behaviour.
- **implemented / unverified** — source exists and focused checks pass, but motion, reference or hardware evidence is incomplete.
- **incomplete** — a concrete requirement remains absent or only partly implemented.
- **blocked** — completion requires an unavailable external asset or authorised device/integration.

This ledger distinguishes implementation evidence from comparison with the original design references.

| Requirement | Status | Implementation and evidence | Missing / next repair or blocker |
| --- | --- | --- | --- |
| Visual-only controller boundary | verified | `NeuralController.kt`; control API unit tests; previews contain no provider/tool/permission dependency | Owner integrations remain separate by design |
| Thinking field: black centre, two blue rings | verified | `NeuralDialView.scene`; inspected `docs/neural-evidence/thinking_field.png` | Original-reference comparison blocked |
| Independent white packet speed, length and spacing | verified | Cached `packetPhase`, `packetSpeed`, `packetSweep`, and radial lanes; fresh capture shows nonuniform clusters and counter-motion sources | GIF timing comparison blocked |
| Internal thinking: deforming projected sphere | implemented / unverified | 24 sampled meridians, depth-dependent nodes, clock-driven twist/fold and branches; inspected state and transition captures | Record representative motion and compare with reference GIFs |
| Independent smaller thought nodes | verified | Six independently sized/orbiting nodes; visible in internal-thinking capture | Reference placement/timing blocked |
| External exploration central orb and peripheral nodes | verified for simulation | Six explicit `sim …` endpoints around the field; inspected capture | No authorised real endpoint adapter exists |
| Endpoint truth states | verified | `available`, `launchable`, `connected`; controller forbids impossible connections; renderer labels unavailable/installed/launchable/integrated | Real application inventory/integration intentionally not inferred |
| Selectively growing branching connections | verified for simulation | Only connected endpoints receive sampled growing cubic paths and branches; current capture shows two of six connected | Motion fidelity comparison blocked |
| Peaceful blue/red spheres and braided strands | implemented / unverified | Independently detailed spheres, 12 braided strands, white travelling packets and endpoint pulses; inspected capture | Reference motion/timing blocked |
| Conflict pressure and colour control | implemented / unverified | Balance-keyed blue/purple/red strand shader plus moving per-packet pressure colours; inspected conflict capture | Capture and inspect multiple owner-supplied balance values |
| Local infection retains blue exterior | verified for preview intensity | Dull blended blue shell and smoothly spreading red meridians/branches; inspected capture | Full intensity sweep and reference comparison pending |
| Peer takeover retains red exterior | verified for preview intensity | Red shell retained with smoothly spreading blue internals; inspected capture | Full intensity sweep and reference comparison pending |
| Firewall candles | verified for simulation | Two coloured candle bodies, animated flame curves, white flame cores, configurable percentage and simulation labels; inspected pair-state captures | Real metrics intentionally absent; reference comparison blocked |
| Seven-state transitions | verified for visibility/interruptions | All 42 directed pairs sampled at 220 ms with nonblank geometry; rapid seven-state interruption settled on final request; seven transition PNGs inspected | Reference transition timing blocked |
| Smooth activity/balance/infection parameters | implemented / unverified | Frame-time exponential following; zero activity clears immediately | Multi-value visual capture remains useful |
| Preview isolation and STOP reset | verified | Explicit simulation labels; dashboard STOP and process-local notification/accessibility STOP signal both reset preview; focused instrumentation passed | Physical notification/overlay interaction remains owner-device validation |
| Reduced motion and lifecycle scheduling | verified historically | Animation gated by attachment, host activity, visibility and Android animator scale; historical focused checks | Fresh physical Fold lifecycle validation blocked |
| Manual legacy renderer switch | verified historically | Existing neural/fallback control preserved | None |
| Automatic renderer failure recovery | implemented / unverified | Renderer failure posts `onRendererFailure`; Activity selects legacy renderer while functional controls remain outside the View | A deterministic fault-injection UI check is not implemented |
| Narrow cover-like layout | verified on emulator dimensions | 904×2316 inspection: circular renderer visible, controls stacked, STOP fixed | Not physical cover display |
| Conventional phone layout | verified on emulator | 1080×2400 state/capture evidence | Not physical phone/Fold |
| Large inner-like layout | verified on emulator dimensions | 2208×1840 inspection: adaptive two-pane workspace and enlarged circular renderer | Fold posture/display transition unavailable |
| Performance boundedness | implemented / unverified | Cached trig arrays, bounded 24 meridians/12 strands/12 endpoints, lifecycle-aware frames | Single detail-pass sample was noisy (85 ms CPU median, 150 ms P95, 67.41% deadline misses); repeat on representative hardware |
| Original 16 PNG / 8 GIF fidelity | blocked | Manifest only; no binary reference was inspected | Download or attach `AgenticFold-AI-UI-All-Chat-Images-2026-11-13.zip` into this project-accessible environment |
| Physical Fold behaviour/performance | blocked | No authorised physical Fold attached | Connect explicit serial; run non-destructive cover/inner/fold/lifecycle and profiler checklist |
