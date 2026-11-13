# Acceptance and readiness evidence

## Installation gate

v0.5.0: owner reported “App not installed”; exact phone installer cause remains unconfirmed. The archived legacy APKs have incompatible signing certificates. v0.5.1 Recovery: main run 37902959993 installs the exact delivered APK alongside pinned v0.5.0, verifies same-APK reinstall retains private data, and passes 12 unit + 8 packaged instrumentation tests. Signature/package metadata and downloaded checksums were inspected. Physical Recovery installation is PENDING. Future independently built updates remain blocked until protected persistent signing is provisioned.

## Ten required scenarios

| Test | Current outcome | Remaining evidence |
|---|---|---|
| 01 Split-screen collaboration | Physical UNTESTED; dashboard smoke gates added | Chrome + dashboard on Fold, both usable |
| 02 Floating continuation | Physical UNTESTED; overlay source implemented | Leaving split-screen, OS lifecycle, fallback denial |
| 03 Authorised navigation | PARTIAL: manual input and structural effects | Real multi-step autonomous test-app workflow |
| 04 Immediate interruption | Unit authorization gates added; physical UNTESTED | Gestures during STOP and p95 latency |
| 05 PDF investigation | Native PDF extraction and real report tool workflow emulator tested (run 38083487730) | Live-model summary + real SAF export + physical Fold; no OCR/passwords |
| 06 Coding workflow | NOT IMPLEMENTED in APK | Actual repo edit + test + compile + artifact |
| 07 Interrupted execution | PARTIAL: no persisted control, interrupted journal | Kill/restart/process recreation recordings |
| 08 Permission denial | PARTIAL: fail-closed source and dashboard smoke | Actual accessibility, notification and SAF refusal tests |
| 09 Prompt injection | PARTIAL: limited registry/adversarial fixtures | Real malicious document/model evaluations |
| 10 Fold transition | Unit geometry gates + emulator rotation smoke | Physical fold/unfold/multi-window during approval/input |

Main run 37899308890 records 12 unit tests and 8 Android 15 emulator tests passing, with zero failures/errors/skips, plus successful lint/compilation. Downloaded XML was inspected and checksums verified. Tagged prerelease v0.5.0 includes the test evidence. A fake provider tests the actual runtime's dispatch and local file effects; it does not establish real-model reasoning capability. APK compilation never substitutes for a physical device test. Use docs/DEVICE_TESTS.md for the physical validation card.

## Release scoring rule

v0.6.0 PR run 37954805359 passed 12 unit tests and 11 packaged instrumentation tests against the delivered APK. Objective/coordinate recreation, runtime stages, verified output, rendered busy-chart motion, visual STOP cancellation and control remaining off were checked. Actual narrow, 1600x1200 and 1.6 font-scale emulator captures were reviewed. This is responsive-layout evidence, not Chrome split-screen or physical hinge testing. The fake provider is visibly identified; live inference remains untested. The emulator recorded a Quickstep bootstrap ANR before app tests, retained in diagnostics; no Agent Fold ANR was recorded. Signing/publication and physical installation remain blocked/pending as described above.

Main run 37955748516 repeats these gates against the exact main-build APK and passes 12 unit plus 11 instrumentation tests. Downloaded XML/logs, checksums, signature/package reports, window-focus checks and changing chart pixels were inspected. Release job 37956452126 is skipped: the certificate is ephemeral and incompatible with v0.5.1 Recovery. This does not satisfy physical installation acceptance or production scoring.

Scores represent demonstrated readiness, not optimism or code volume. Each category needs a task suite, recorded outcomes and device/API evidence. Unmeasured categories remain UNTESTED and no production average is asserted.

| Category | Initial audit readiness (0–10) | Evidence limitation |
|---|---:|---|
| Agent reasoning/task completion | 0 | No live-provider task evidence |
| Device-control reliability | 2 | Manual implementation; physical checks pending |
| UI/UX | 2 | Native responsive dashboard; no usability study |
| Foldable multitasking | 1 | No physical Fold evidence |
| Memory/continuity | 2 | Bounded task journal; no project memory |
| File/tool integration | 3 | Bounded file/report tools; broader workflows absent |
| Security/privacy | 3 | Enforced capability boundaries; audit incomplete |
| Performance/stability | 0 | Physical profiling absent |
| Autonomy/recovery | 2 | Bounded loop; durable recovery absent |
| Transparency/user control | 3 | Visible action log/STOP; hardware latency unmeasured |

Provisional audit average: 1.8/10. This is a conservative readiness score from available implementation evidence, not a comparative product score or proof that implemented controls pass on-device. Production target is mean ≥9.0, every category ≥8.5; currently NOT MET.

## Measurement targets — not achieved results

- Local STOP authorization revocation: p95 ≤50 ms; no new dispatch after revocation.
- UI frame duration: p95 ≤16.7 ms at 60 Hz in standard navigation.
- Cold startup: p95 ≤1.5 s on supported Fold.
- Idle session: no polling; incremental battery drain target ≤1%/hour under a controlled baseline.
- App PSS target ≤150 MiB excluding Android/model external processes.
- Pre-production soak: ≥99.5% crash-free sessions over at least 1,000 recorded sessions.
- Verified output rate: 100%; “completed” requires successful readback.

## v0.6.1 Signal activity evidence

PR #6, run 37962519304: 12 unit and 12 delivered-APK Android 15 tests pass. Actual Signal/Network renders, visible particle motion, STOP cancellation, selected-tab recreation and unsubmitted inputs are tested. Reviewed narrow/wide/1.6-font captures have clean window-focus checks; last-ANR reports none since boot. Canonical merge 58f82b5c230f913da5ae539e515348c9d7d4a395 has the same tree as the tested checkout. Fake-provider evidence does not validate live inference. Physical acceptance scenarios and readiness scores remain pending/unchanged. Signing mode is ephemeral-ci-only; there is no compatible v0.6.1 phone update.

## v0.6.2 workspace evidence

PR run 37994689158 and main run 37995442593 both completed SUCCESS. Downloaded/visually reviewed PR evidence confirms 12 unit and 14 Android tests, 0 lint errors/26 warnings, with narrow/wide/1.6-font and every workspace capture. Main status is confirmed separately; reviewed detailed evidence remains tied to the PR APK. No physical validation is inferred.

## v0.6.3 PDF evidence

PR #8, run 38083487730: 12 unit + 19 delivered-APK Android tests pass, lint 0 errors/26 warnings. Five PDF regressions include actual native text extraction in an isolated no-network process, consent/schema denial, malformed/blank/mixed/oversized/page/text limits, copy cancellation, and actual report readback with a fake model. Source hash/page counts are retained in pdf-tool-result.json. Model output and hostile document wording are synthetic fixtures, not live reasoning evidence. Physical SAF export, live provider, older Android compatibility and Fold checks remain pending; readiness scores are unchanged. Merged application tree exactly matches the tested checkout.
