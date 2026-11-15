# Agent Fold architecture — v0.6.3 development

## Implemented boundaries

MainActivity is a native resizeable dashboard with insets, compact fixed status/navigation and STOP. Five native workspace buttons expose Activity, Task, Files, Control and History; selection is restored from saved activity state and never grants control. On narrow panes Activity contains the graph/audit; on wide windows it also displays the mission beside the graph. Single-panel selections hide unused columns so the active card gets the available width. The same card/input instances stay attached even while hidden; their state is restored before measurement. Navigation resets vertical scroll for the newly selected panel. A fixed polite accessibility status announces actual permission/task phase changes. It subscribes to events rather than polling. Android still controls which split-screen pane the user places it in.

AdaptiveWorkspace attaches cards before Android restores child state, then reparents the same inputs into one stack or two columns according to measured window width. Unsubmitted objective and coordinate fields have stable resource IDs. This handles window width, not physical hinge posture sensing.

TaskActivity records actual runtime phases, model-call counts, tool outcomes and verified-file state. The native ActivityNetworkView renders that telemetry in two switchable presentations: curved Signal ribbons/phase particles and concentric Network hubs/circuit paths. Four color lanes correspond to model, tools, files and manual control, with wrapping native text providing actual detail. Only the model/tool/verification phase renders particle motion; the stream shape and particles are visual activity, never measurements. Paths are cached on size changes, the active particle field is bounded to 72 dots with visual redraw capped at 30 fps, and no reference video or raster branding is decoded. Presentation preference persists separately from task history and cannot grant control. It never invents subagents, task percentages or completion estimates. Animation is conditional on a phase with rendered activity, host lifecycle, visibility, system animation settings and Battery Saver. Global layout and scroll events refresh visibility without polling; switching away from the graph pauses motion without cancelling the approved task. Flow uses linear timing. Plain-text state and accessibility descriptions accompany the canvas. Manual device authorization is a distinct node, separate from model capabilities.

AgentAccessibilityService serializes manual actions, approvals, expiry and revocation on the main looper. ControlSession is a pure Kotlin authority state machine. No authority is persisted. A ticket includes generation, sequence, package, window ID, window rectangle, display and a ten-second approval deadline. Dispatch requires an unchanged frame AND structural precondition. A stale callback cannot release a newer task.

Observation reads only bounded accessibility structure, never raw UI text. Password nodes and data-sensitive nodes are excluded; actions fail closed for protected/incomplete trees. It does not capture screenshots. Manual gestures are only on application windows of the default display. Security/settings/install interfaces are rejected. Resize/disappearance revokes pending authority.

Verification compares structural snapshots after Android reports delivered input. CHANGED, UNCHANGED and TARGET_LOST distinguish observed effects. There is no automatic retry and no claim that a gesture callback establishes semantic success. Global navigation is explicitly identified as global.

The overlay provides a persistent STOP while accessibility is alive. Notification STOP is an optional fallback; session creation fails when neither path is available. Process interruption and Android accessibility lifecycle loss revoke authority. Android does not guarantee normal human-touch observation; available touch-interaction events only provide best-effort yielding.

AgentRuntime is a single-threaded, bounded orchestration loop behind ModelProvider. CompatibleProvider supports configurable HTTPS Chat Completions endpoints; cloud/provider selection is owner-controlled. Redirects are disabled, network operations time out, responses and tool arguments are bounded, and STOP disconnects the current request. No auto-retry of a billed network request occurs.

AgentTools is a capability registry containing only read_selected_text and write_report. It holds an immutable owner-selected URI scope. Models cannot supply alternative URIs, endpoints, filesystem paths or device gestures. Tool JSON is checked again locally even when strict provider schemas are enabled. Reports never overwrite inputs and are byte-verified before being reported.

CredentialStore uses AES-GCM and an Android Keystore key. Endpoint/model metadata is plaintext; the API key is encrypted, never logged or embedded in source. Production should use a scoped user-controlled gateway or backend rather than distributable long-lived application credentials.

TaskDatabase uses private SQLite for objective, state and summary. A new process marks unfinished tasks INTERRUPTED. Nothing resumes control automatically. History is bounded and inspectable/deletable. This is task continuity, not implemented semantic memory. Draft objective is persisted separately. File contents are not inserted into the task journal.

## Next architecture milestones

1. Full lifecycle/device instrumentation for authorization, STOP races, sensitive windows and fold transitions.
2. Structured action types with explicit app/task scope, semantic postconditions, sensitive-operation gates, protected-content isolation and prompt-injection adversarial cases.
3. Optional MediaProjection with Android consent, visible capture indicator, protected-screen handling and memory-bounded vision frames.
4. Model-driven observer/plan/act/verify loop through that executor; no generic raw-tap autonomy until these gates exist.
5. SAF directory workspaces, versioned source edits, backups, PDF extraction, GitHub/Drive capability adapters and cloud-build workflows.
6. Durable jobs where Android permits; each resumable task gets a checkpoint. Restoring control always needs a new grant.
7. Narrow specialist roles with per-role tool capabilities, explicit completion contracts, bounded parallelism and shared expenditure accounting. Only one device-input lease at a time.
8. User-editable memory separated by instruction/project/fact/hypothesis, encryption policy and optional semantic retrieval.
9. Fold-aware two-column workspace, panel arrangement, per-window coordination and optional push-to-talk STOP.

## Delivery and rollback

v0.3.1 base: 8d9c555cbb07b74ef91c53e7a45cc86789c4ae72.
v0.4 safety changes are isolated in development/session-safety-v0.4, PR #2.
v0.5 model/file work is isolated in development/agent-runtime-v0.5.

Development changes merge only after the required automated gates pass. Source ZIPs and APKs include commit identity and SHA-256 hashes. The emulator installs and tests the exact build-job APK; it also records responsive-layout screenshots and rejects captures covered by another focused window. Error dialogs remain enabled and logcat/ANR diagnostics are retained.

The workflow_run pipeline records build results in Issue #1. Publication additionally requires a successful main push, owner-managed signing, an expected certificate matching the APK and explicit repository release enablement. Pull-request builds never receive signing secrets. Without provisioning, builds are marked ephemeral-ci-only and v0.5.1 stays the published installation recovery. A new persistent key cannot update that existing APK's unrelated certificate; a migration plan is still required. Existing tags/assets are preserved. No private signing key is in source or public build artifacts; identical signed bytes and signature continuity are not claimed.

## PDF text boundary

read_selected_pdf uses the same frozen per-task URI/consent scope as the UTF-8 reader. Only the owner process reads the content provider. It copies ≤4 MiB into an anonymous read-only snapshot descriptor and hashes actual source bytes. PdfTextService is non-exported, isolated and bound under a unique instance name for each request; it receives only that descriptor and a reply Messenger. Native PdfRenderer parsing runs on a worker and returns ≤24 KiB text with page markers/empty-page count, rejecting >24 pages and unsupported documents. Both process UID isolation and denied network permission are checked before accepting results. The owner waits ≤20 seconds, checks STOP every 50 ms and unbinds in finally. Service destruction kills only its isolated process, including native work that ignores Java interruption. Provider-copy cancellation is checked between reads; a blocked provider is not a hard real-time boundary. Android <35 keeps UTF-8 support and rejects PDF extraction explicitly. No PDF raster OCR or password handling is implemented.
