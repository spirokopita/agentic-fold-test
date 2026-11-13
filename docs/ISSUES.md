# Permanent issue register

IDs are never reused. “Implemented” describes source; “Tested” requires linked evidence.

| ID | Area | Status | Next completion evidence |
|---|---|---|---|
| AF-001 | Cloud APK compilation | Tested | v0.6.3 PR run 38083487730; 12 unit/19 Android tests; merged application tree identical; latest published prerelease remains v0.5.1 pending signing/migration |
| AF-002 | Expiring device authorization and STOP | Unit tested; physical pending | 10 policy tests passed; physical STOP race tests still needed |
| AF-003 | Window-relative gestures and resize rejection | Unit tested; physical pending | Fold resizing during action and approval |
| AF-004 | Semantic action verification | Partially implemented | Structural effect only; explicit semantic postconditions needed |
| AF-005 | Floating bar | Partially implemented | Drag, compact, persisted position; panel rearrangement/notification denial hardware tests |
| AF-006 | Model/provider orchestration | Local fake-provider loop tested; live test blocked | Tool-capable provider credentials and real-model workflow |
| AF-007 | UTF-8 file/report workflow | Native report/tool validation tested; SAF physical pending | Real document-provider read/export tests |
| AF-008 | PDF investigation | Emulator tested; physical/live model pending | v0.6.3 run 38083487730: real PDF extraction/isolation, denied scope/limits/cancellation and actual read/report loop passed. OCR, live model, physical SAF export/Fold pending. |
| AF-009 | Model-driven cross-app workflows | Not started | Typed/safe action executor and adversarial tests |
| AF-010 | GitHub/Drive integrations in APK | Not started | Scoped authorization, API tools and cloud build workflow |
| AF-011 | Structured task history | Partially implemented | SQLite journal done; resumable checkpoints/encryption/search pending |
| AF-012 | Semantic/project memory | Not started | User correction/export/deletion and retrieval evidence |
| AF-013 | Specialist subagents | Not started | Narrow role grants, concurrency/input lease, cost controls |
| AF-014 | Background task persistence | Not started | Android-approved lifecycle tests; safe checkpoint resume |
| AF-015 | Voice / voice STOP | Not started | Visible push-to-talk permission, interruption tests |
| AF-016 | Capture/vision | Not started | MediaProjection consent, protected screens, vision tool |
| AF-017 | Simultaneous independent touch | Blocked by current public input model | Yield policy; never claim isolated touch channels |
| AF-018 | Premium fold-aware multi-column UI | Partially implemented | Responsive dashboard/STOP done; full UI acceptance pending |
| AF-019 | Secure production signing | Blocked on owner signing provision | Protected secrets/environment and upgrade verification |
| AF-020 | Dependency locks/SBOM/security audit | Partially implemented | Versions/action SHAs pinned; transitive verification/audit pending |
| AF-021 | Device performance/battery benchmarks | Not started | Physical traces across supported configurations |
| AF-022 | Competitor task evaluation | Partially implemented; authenticated tests blocked | Actual task recordings, no inferred scores |
| AF-023 | Protected-content and prompt-injection hardening | Partially implemented | Bounded tools/filtered trees; broad adversarial suite pending |
| AF-024 | Production readiness | Blocked | All categories ≥8.5 and mean ≥9.0 with recorded device evidence |
| AF-025 | Lint warning follow-up | Partially implemented | v0.6.0 PR run 37954805359: 0 errors, 26 warnings. Allocation/constructor/obsolete qualifier warnings fixed; target SDK, i18n/RTL, monochrome fallback, backup configuration, context references and touch accessibility remain. |
| AF-026 | Legacy debug APK update/install failure | Signing mismatch confirmed; recovery emulator tested; phone pending | v0.3.1, v0.4.0 and v0.5.0 certificates differ. New separate package retains legacy app. Protect a persistent signing key before future in-place updates. |
| AF-027 | Live network interface and Borg styling | Emulator tested; physical pending | PR #5, run 37954805359: native theme, runtime telemetry, rendered motion and unobscured narrow/wide/1.6 font-scale captures reviewed. Physical Fold/TalkBack/battery still required. |
| AF-028 | Persistent development signing/publication gate | Provisioning blocked | Source supports owner keystore via main-only secrets; release gate requires matching certificate and migration plan. Connector cannot administer signing secrets. |
| AF-029 | Unsubmitted inputs lost on activity recreation | Emulator tested | Regression failed in run 37949862591; attaching cards before hierarchy restoration preserves objective and X/Y inputs. Passed in run 37954805359. Physical fold/resize still required. |
| AF-030 | Connector rendering and visible chart motion | Emulator tested | Visual review rejected filled triangles and identical captures despite automated pass in run 37953503972. Explicit STROKE and three-frame chart pixel comparison passed in run 37954805359. |
| AF-031 | Emulator Quickstep bootstrap ANR | Test-environment reliability partially blocked | API 35 stock launcher ANR recorded before app tests. CI stops the unrelated launcher, retains error dialogs/logcat/last-ANR and rejects obscured captures. Clean app captures and passing instrumentation do not establish OEM stability. |
| AF-032 | Video-inspired Signal activity tab | Emulator tested; physical pending | PR #6, run 37962519304: Signal/Network switching, recreation, actual motion/STOP and narrow/wide/1.6-font captures passed; physical Fold, TalkBack and battery still required. |
| AF-033 | Animated fixture idle synchronization | Emulator tested | First run 37961292792 failed as idle waits outlasted the fake-provider fixture. Live actions use main-thread synchronization and bounded expected-state checks; redraw capped at 30 fps. All 12 Android tests pass in 37962519304. |
| AF-034 | Compact workspace navigation | Emulator tested; physical pending | PR #7, run 37994689158: 14 Android tests; real panel, narrow/wide/1.6-font captures; draft recreation, keyboard focus release, live hidden/visible graph and STOP pass. Physical Fold/TalkBack still required. |
| AF-035 | Native PDF fixture resource closure | Fixed; emulator tested | First run 38081962699 rejected Kotlin use() on non-AutoCloseable PdfDocument; explicit finally closure in the test fixture; final run 38083487730 passed. |
| AF-036 | Standalone PDF test-provider classpath | Fixed; emulator tested | Run 38082618977: test provider crashed on missing Kotlin runtime; main killed after provider death. Framework/Java-only provider retained all PDF assertions; final run 38083487730 passed all 19 Android tests. |

Rejected approaches: root-only access, hidden capture/microphone, accessibility as blanket consent, model-selected arbitrary URI/path, silent source overwrite, mock gestures presented as live, auto-resumption of device control after process death.
