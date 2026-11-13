# Agent Fold v0.7.0 — Voice Widget & Approved Phone Tools

**Current source release:** Standard Android app (not a launcher replacement), tiny 1×1 home-screen Voice ON/OFF widget, microphone foreground service with speech recognition and speech output, user-approved Accessibility phone tools, existing model provider and selected-file tools. See [`docs/RELEASE_0_7_0.md`](docs/RELEASE_0_7_0.md) for exact scope, Android 17 microphone limitations, on-device setup and build/testing status. The older v0.6.3 notes below are retained for history. **This zip is source, not a verified APK.**

---

# Agent Fold — development v0.6.3 — approved PDF investigation

Native Android work companion for foldable and conventional phones. The repository is canonical; stable source snapshots and installable APKs are backed up in the project Drive folder.

## What this build does

- Compact fixed status and workspace navigation: Activity, Task, Files, Control and History. Wide Activity mode keeps the task beside the graph; narrow panels open directly. Fixed STOP remains available across navigation. Native green-and-black dashboard and matching floating controls. The live activity panel offers a video-inspired curved particle Signal tab and the Borg concentric-hub Network tab; selection survives recreation.
- Live runtime stages, model-call counts, tool outcomes and file-readback state; animation pauses when idle, hidden, backgrounded, reduced motion or Battery Saver is active.
- Runs a genuine bounded model/tool loop using an owner-configured HTTPS, OpenAI-compatible tool-capable model.
- Reads ONE explicitly approved UTF-8 document (text, source, JSON or CSV, up to 48 KiB), or text-based PDF on Android 15+ (4 MiB, 24 pages, 24 KiB extracted text; no OCR/passwords). Creates one new private text report (up to 64 KiB), verifies its bytes by readback and exports through Android's document picker.
- Stores bounded local task history and the last objective. Process interruption never grants or restores device-control authority.
- Preserves manual accessibility tap, scroll, Back and Home controls with five-minute sessions, single-use approvals, observed-window preconditions and structural UI-effect verification.
- Provides a draggable four-panel control bar, compact mode, saved position, dashboard return, fixed dashboard STOP and optional notification STOP.
- Tests authorization, expiry, stale callbacks, geometry, tool validation and real local report creation. Android instrumentation uses a fake provider to test the actual tool loop without spending money.

## Phone-only installation

**Latest published APK remains v0.5.1.** v0.6.3 source adds approved PDF text investigation, compact workspace navigation and Signal / Network activity tabs; update publication is gated on owner-managed signing and migration. Do not install an ephemeral CI build over Recovery expecting an update to succeed. See docs/RELEASE_0_6_3.md for the latest validation and docs/NETWORK_UI.md for the original layout evidence.

Download **AgentFold-v0.5.1-Recovery.apk** from release v0.5.1 or the project Drive folder. It installs as **Agent Fold Recovery**, a separate package (`com.agentfold.app.recovery`). Keep the old Agent Fold installed: its files and data remain untouched. The recovery app has fresh private storage; configure its provider, reselect files, and grant its permissions separately. Disable the old app's accessibility service before enabling the recovery service to avoid two controllers.

The older APKs used different CI-generated signing certificates. Android cannot update an installed package with an unrelated signing key. This recovery package bypasses that legacy conflict without deleting the old app. Independent future builds still need a protected persistent signing key before they can update this recovery app in place; this milestone does not solve key persistence. See docs/INSTALL_RECOVERY.md.

Android settings are necessarily controlled by the owner. If Samsung displays “Allow restricted settings”, inspect the source/APK first, then use Settings → Apps → Agent Fold Recovery → ⋮ → Allow restricted settings, followed by Accessibility → Installed apps → Agent Fold Recovery. The app never enables accessibility itself.

For AI tasks, configure your own HTTPS API base URL (normally ending in /v1), model ID and API key. ChatGPT subscriptions do not include this API usage. See docs/CONFIGURATION.md. Grant file transmission separately for EACH task, or choose “Run without file”. Review the result, then use “Export verified agent report”.

For gestures, put an ordinary test application beside Agent Fold or in front of its floating bar. Choose LEFT/RIGHT/ACTIVE APP and TAKE CONTROL. Each action displays the package and actual app bounds for “Allow once”. Coordinates are relative to that app window. Move/minimize the bar if it intersects the intended gesture.

STOP blocks future tool and gesture dispatch. Android may finish an already injected stroke or a file write already started. Sessions expire after five minutes; rotation revokes pending authority. Leaving the dashboard cancels AI file tasks; this version does not promise background model execution.

## Current limits

This is a tested development milestone, not a production or AAA release. Physical Fold validation and live provider testing remain required. No autonomous model-driven cross-app actions, screenshot/MediaProjection interpretation, OCR, GitHub/Drive connectors inside the APK, source overwrite, cloud compilation from the APK, specialist subagents, voice, semantic memory or durable background execution are implemented. Read docs/ISSUES.md and docs/ACCEPTANCE.md before relying on it.

Global Back/Home act on Android's focused window, not an independently selected split-screen pane. Simultaneous human/agent touch cannot be guaranteed: Android gesture injection may cancel other touches. Structural UI change indicates an effect, not completion of the user's objective. Password/sensitive/incomplete trees are rejected, but accessibility cannot reliably identify every private or banking screen. Use the manual controls only for non-sensitive test interactions.

## Project links

- Source and development PRs: https://github.com/spirokopita/agenticfoldapk
- Validated development releases: https://github.com/spirokopita/agenticfoldapk/releases
- Build diagnostics: https://github.com/spirokopita/agenticfoldapk/issues/1
- APKs/backups: https://drive.google.com/drive/folders/1M1AdHnfpcXtv9MVsUxxGrNLtDDanlpbC
- Architecture: ARCHITECTURE.md
- Tool reference: docs/TOOLS.md
- Security review: docs/SECURITY.md
- Benchmarks: docs/BENCHMARKS.md
