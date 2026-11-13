# Agent Fold v0.6.1 — Signal activity view

## Changes

The owner’s 720×898, 10-second particle-stream reference is translated into a native Signal tab in the live activity panel. No reference video, branding, tiny code text or synthetic metrics are bundled. Four curved ribbon lanes correspond to MODEL, TOOLS, FILES and manual CONTROL; only the current model/tool/verification phase emits moving particles. The original Borg Network tab remains available.

Both tabs use the same real runtime snapshot and wrapping textual legend. Tab selection persists across recreation without starting work or granting device authority. Native visual invalidation is capped at 30 fps; motion stops on STOP, hidden/background state, disabled system animations and Battery Saver. Static ribbons remain for reduced motion. API credentials, file consent, model limits and existing device controls remain operational.

Version code 8; version 0.6.1. Latest published install remains v0.5.1. Development APK signing/publication is still guarded: ephemeral CI certificates cannot update an installed Recovery app. No physical Fold or live-model result is claimed.

## Validation

- PR #6 merged: https://github.com/spirokopita/agenticfoldapk/pull/6
- Canonical application commit: `58f82b5c230f913da5ae539e515348c9d7d4a395`. Its tree `48a8d64040a03f84ce1963d234753931c9181839` is identical to tested checkout `c97310acffec9454d1ee3b467a19e3eb5ed59559`.
- Validation: https://github.com/spirokopita/agenticfoldapk/actions/runs/37962519304 — SUCCESS.
- 12 unit tests, zero failures/errors/skips. 12 delivered-APK Android 15 instrumentation tests, all completion codes successful. Lint zero errors, 26 existing warnings.
- APK coexistence with legacy v0.5.0 and same-APK reinstall preserving private data passed. This is not cross-version certificate compatibility or physical installation evidence.
- Tab recreation and selection, runtime-preserving live switching, rendered Signal pixel changes, STOP animation cancellation and control remaining off passed.
- Actual Signal and Network captures, narrow, 1600×1200 wide and 1.6 font-scale layouts reviewed. All idle captures show this app’s focused window. Busy-frame difference bounds lie inside the Signal plot. Evidence PNGs are unchanged.
- Last-ANR: none since boot; no physical device result is inferred.

The model-waiting task is explicitly labeled **UI test fixture: provider waiting**. It runs the actual runtime with a fake provider; it is not evidence of cloud inference or successful real-world task completion. First-run failure, superseded run and idle-wait fix remain in BUILD_REPORT.md. Native redraws are capped at 30 fps; hardware frame timing and battery targets are unmeasured.

## Verified build identity

Exact tested PR APK: 2,568,198 bytes; SHA-256 `2966e738efb134c9b59edfe7fbf7c082cc94330dbfece906a3fac82c9aa71551`. Exact compiled source ZIP: 93,116 bytes; SHA-256 `87a857021d61d66fb557d531cb2bcdc84cc47bcab317095ae60e0b54481f1d41`.

Certificate SHA-256: `92b421f23c1e096354eaa03c37eadb2a80b7099f1ec1178f11b23bca3f991647`; signing-mode.txt: **ephemeral-ci-only**. APK package remains `com.agentfold.app.recovery`, code 8, min SDK 29, target SDK 35. This certificate cannot update published v0.5.1 Recovery.

## Backups and remaining work

Previous canonical source is preserved at `backup/v0.6.0-before-signal-ui`. Versioned source with final documentation, test evidence, a clearly labeled CI-only build archive and checksums are backed up to the approved Agent Fold project folder:

https://drive.google.com/drive/folders/1M1AdHnfpcXtv9MVsUxxGrNLtDDanlpbC

Source is merged and the normal main CI pipeline is configured. Only run 37962519304 is claimed as completed validation here. No v0.6.1 phone-update release is published. Persistent signing and an explicit migration plan, physical Fold/split-screen/fold-transition checks, TalkBack, live provider tasks and battery/performance profiling remain pending. Model-driven device workflows, PDF, voice and durable background execution remain unimplemented.
