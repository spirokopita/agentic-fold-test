# Agent Fold v0.6.0 — development validation, 2026-10-09

The requested Borg/network style and live activity view are merged into canonical source. This is a verified development milestone; APK update publication remains blocked on protected signing and a migration plan.

## Changes

- Native green/black dashboard and matching floating controls, circular hubs, circuit paths and adaptive icon.
- Actual runtime phases, bounded model-call counts, tool outcomes and verified report state. Manual device authority remains separate from the model's two file tools.
- Single-column/two-column layouts based on measured width. Plain-text status, numbered large-text fallback and fixed STOP.
- Unsubmitted objectives and coordinates survive activity recreation. STOP revokes device authority before provider I/O cancellation; late worker telemetry cannot restore an active stage.
- Event-driven animation pauses when inactive, hidden, backgrounded, system animations are disabled or Battery Saver is enabled. No decorative GIF loop or invented progress percentages.

## Recorded evidence

| Gate | Outcome |
|---|---|
| Canonical code commit | `499cca0ecd3f2c722ea768e30081f124ec913489` |
| PR | https://github.com/spirokopita/agenticfoldapk/pull/5 — merged |
| Main validation | https://github.com/spirokopita/agenticfoldapk/actions/runs/37955748516 — SUCCESS |
| Unit tests | 12 passed; zero failures/errors/skips |
| Delivered-APK Android 15 tests | 11 successful completions; real runtime/native file tools, fake provider |
| Installation | Delivered APK coexists with legacy v0.5.0; reinstall of the same APK retains the private-data marker |
| UI review | Actual narrow, 1600x1200 and 1.6 font-scale emulator captures; no covering window |
| Animation | Three busy-chart captures; chart pixels genuinely change; STOP cancels animation |
| Lint | Zero errors; 26 warnings remain |
| Release workflow | 37956452126: validation report succeeds; release job SKIPPED |
| Physical Fold / live provider | NOT RUN |

The emulator's stock Quickstep launcher recorded an ANR before tests. CI stops that unrelated launcher, retains system error dialogs and diagnostics, and rejects obscured captures. No Agent Fold ANR appears in these traces. Emulator success does not establish OEM stability or physical STOP latency. Earlier compiler/test/visual failures and their fixes remain in BUILD_REPORT.md.

## Build identity and signing

Application ID `com.agentfold.app.recovery`; version code 7; version 0.6.0; min SDK 29; target SDK 35.

- CI-only APK: 2,559,058 bytes; SHA-256 `7057330f414aa8daaf774d2d7e990d45cebbad30e75d78f8a0b44edc4f85ef0a`.
- Exact compiled source ZIP: 82,356 bytes; SHA-256 `bb4a579e560e8ac183564563e28071c6280a6eeac618f132700ce6d458ecc0f2`.
- Certificate SHA-256: `4aa084cf020e1bde929bcb3edcfb0b310c071debd62d50a71059e8ec61322ae0`.
- Signing mode: **ephemeral-ci-only**. This is **not an in-place update** for v0.5.1 Recovery, whose certificate differs.

Latest published installation APK remains v0.5.1. A persistent new key will protect future continuity but cannot reconstruct the old discarded private key. Existing Recovery installations need an explicit migration plan; no uninstall, data deletion or repeated package-identity workaround is authorized by this milestone. The GitHub connector cannot administer repository signing secrets. Browser administration needs separate permission before proceeding.

## Outputs and rollback

Source is canonical on main. The previous main snapshot is preserved in `backup/v0.5.1-before-network-ui`; published v0.5.1 assets remain unchanged. Build artifacts include APK/test APK, exact source, checksums and signature/package/commit reports. A CI-only backup archive clearly identifies the incompatible update limitation. A source snapshot with the final documentation and a validation evidence archive are saved in the approved project folder:

https://drive.google.com/drive/folders/1M1AdHnfpcXtv9MVsUxxGrNLtDDanlpbC

Reviewed PR screenshot backups (same application tree as main):

- Wide: https://drive.google.com/file/d/1atlASOIdZOWIsGyu7od_DhCUThuBz5II/view
- Narrow: https://drive.google.com/file/d/1SjdaPU1PHHjzhYl4f7qWTSZLVdnO4d6K/view
- Large text: https://drive.google.com/file/d/1eDuXPy9Slfj8aIybSKiACfTEhibi2Z-S/view
- Active **test fixture**: https://drive.google.com/file/d/1H7-S_zgXG4QDJYwlSCvUIMy3oQ1mcxT7/view

Physical installation, Chrome split-screen, folding/hinge behavior, TalkBack, Battery Saver behavior on the Fold, live reasoning and measured performance/battery remain required. Model-driven cross-app tasks, PDF extraction, voice, project memory and durable background execution are not implemented. No production score or competitor superiority is claimed.
