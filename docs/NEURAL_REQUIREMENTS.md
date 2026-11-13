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
| Internal thinking: deforming projected sphere | verified for temporal deformation | 24 sampled meridians, depth-dependent nodes and clock-driven twist/fold visibly change across inspected frames 280 ms apart | Reference GIF timing/deformation comparison blocked |
| Independent smaller thought nodes | verified | Six independently sized/orbiting nodes; visible in internal-thinking capture | Reference placement/timing blocked |
| External exploration central orb and peripheral nodes | verified for simulation | Six explicit `sim …` endpoints around the field; inspected capture | No authorised real endpoint adapter exists |
| Endpoint truth states | verified | `available`, `launchable`, `connected`; controller forbids impossible connections; renderer labels unavailable/installed/launchable/integrated | Real application inventory/integration intentionally not inferred |
| Selectively growing branching connections | verified for simulation | Only connected endpoints receive sampled growing cubic paths and branches; current capture shows two of six connected | Motion fidelity comparison blocked |
| Peaceful blue/red spheres and braided strands | verified for temporal movement | Independently detailed spheres, 12 braided strands, travelling packets, flame sway and changing neural geometry are visible across inspected frames 280 ms apart | Reference motion/timing blocked |
| Conflict pressure and colour control | verified for visual control range | Balance 0/50/100 captures and colour-count assertions verify the braid and moving pressure shift between red and blue while retaining both endpoint identities | Real peer telemetry intentionally absent; reference motion comparison blocked |
| Local infection retains blue exterior | verified for 0/50/100 intensity | Inspected sweep shows progressively spreading red internal paths while the cyan shell remains distinct; rendered colour assertions passed | Reference comparison blocked |
| Peer takeover retains red exterior | verified for 0/50/100 intensity | Inspected sweep shows progressively spreading blue internal paths while the red shell remains distinct; rendered colour assertions passed | Reference comparison blocked |
| Firewall candles | verified for simulation | Two coloured candle bodies, animated flame curves, white flame cores, configurable percentage and simulation labels; inspected pair-state captures | Real metrics intentionally absent; reference comparison blocked |
| Seven-state transitions | verified for visibility/interruptions | All 42 directed pairs sampled at 220 ms with nonblank geometry; rapid seven-state interruption settled on final request; seven transition PNGs inspected | Reference transition timing blocked |
| Continuous motion in every settled state | verified on API 35 emulator | Two frames 280 ms apart for every state exceeded the visible-change threshold; seven side-by-side pairs were inspected under `docs/neural-evidence/motion-pairs/` | Source GIF comparison and physical-device cadence blocked |
| Smooth activity/balance/infection parameters | verified for rendered endpoints and midpoint | Nine 0/50/100 captures confirm distinct balance and infection output; frame-time exponential following remains bounded; zero activity clears immediately | Reference timing comparison blocked |
| Preview isolation and STOP reset | verified | Explicit simulation labels; dashboard STOP and process-local notification/accessibility STOP signal both reset preview; focused instrumentation passed | Physical notification/overlay interaction remains owner-device validation |
| Reduced motion and lifecycle scheduling | verified historically | Animation gated by attachment, host activity, visibility and Android animator scale; historical focused checks | Fresh physical Fold lifecycle validation blocked |
| Manual legacy renderer switch | verified historically | Existing neural/fallback control preserved | None |
| Automatic renderer failure recovery | verified | Deterministic invocation of the same private failure handler used by the draw exception path hides the neural View, shows the legacy activity View and leaves STOP visible; focused instrumentation passed | Physical fault/device recovery remains pending |
| Narrow cover-like layout | verified on emulator dimensions | 904×2316 inspection: circular renderer visible, controls stacked, STOP fixed | Not physical cover display |
| Conventional phone layout | verified on emulator | 1080×2400 state/capture evidence | Not physical phone/Fold |
| Large inner-like layout | verified on emulator dimensions | 2208×1840 inspection: adaptive two-pane workspace and enlarged circular renderer | Fold posture/display transition unavailable |
| Performance boundedness | implemented / unverified | Cached trig arrays, bounded 24 meridians/12 strands/12 endpoints, lifecycle-aware frames; three fresh no-capture workloads all passed | API 35 emulator repeat regressed to 93 ms CPU median, 150 ms P95 and 88.89% median deadline misses while GPU stayed 17/24 ms median/P90; obtain a current trace and repeat on representative Fold hardware before another renderer change |
| Original 16 PNG / 8 GIF fidelity | blocked | Manifest only; no binary reference was inspected | Download or attach `AgenticFold-AI-UI-All-Chat-Images-2026-11-13.zip` into this project-accessible environment |
| Physical Fold behaviour/performance | blocked | No authorised physical Fold attached | Connect explicit serial; run non-destructive cover/inner/fold/lifecycle and profiler checklist |
