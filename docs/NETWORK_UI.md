# Network interface — v0.6.0 development

Spiros requested a mixture of live agent activity and whole-app styling using his Borg/network GIF references. Native Kotlin surfaces use near-black backgrounds, restrained green/lime rings, circuit paths and clear text. No copyrighted logos, decorative fake tasks or heavyweight looping GIFs are bundled.

The central task hub displays actual runtime stages. MODEL, TOOLS and FILES nodes indicate model calls, validated dispatch and actual output readback. CONTROL is a separate manual authorization indicator: the model still cannot control apps. Call counts are a usage bound, never task percentage or an estimated completion time. Tool rejection and errors are explicitly labeled. STOP suppresses late activity updates and future dispatch.

Runtime events update immutable telemetry through the existing observable event path. Moving packets appear only during work while the chart is visible. They pause when offscreen, backgrounded, animations are disabled or Battery Saver is enabled. The view unregisters observers/receivers and cancels animation on detach. Plain text summaries and an accessibility description expose the same state without relying on colors or canvas text. Large font settings use numbered nodes with native wrapping text underneath.

Measured window width chooses a single card stack or two columns at 720dp. Narrow ordering is activity, mission, files, manual controls and audit trail. Reparenting preserves existing inputs during width changes. Fixed top STOP remains outside scrolling content; native controls retain at least 48dp height.

CI records actual narrow, wide and enlarged-text Android dashboard screenshots after testing the exact delivered APK. Emulator fake-provider tests exercise the real runtime/tool implementation without claiming real inference. Physical Fold, TalkBack, visual comfort and battery measurements remain required.

PR #5 run 37954805359 passed 12 unit and 11 delivered-APK Android tests, with zero lint errors and 26 warnings. Three busy-chart captures include a genuine packet-position pixel difference. Reviewed screenshots have no covering system dialog; the trace records a stock Quickstep bootstrap ANR before tests. Screenshot backups are in the approved project folder, named with this run ID. Source was merged as 499cca0ecd3f2c722ea768e30081f124ec913489; its tree exactly matches tested synthetic checkout e979bbf2d03d205e196f659204de4700403cb1c5. v0.5.1 source is preserved in backup/v0.5.1-before-network-ui.

After a signing-compatible installation is available, validate real task state, STOP during a provider request, unsent input preservation, narrow Chrome split-screen, fold transitions, scrolling the chart offscreen, Battery Saver, system animation disabling and TalkBack on the owner's Fold. Enlarged-text captures are visual checks, not a complete screen-reader audit.

## Signing before publication

CI-only APKs are ephemeral-signed and cannot reliably update installed Recovery builds. v0.5.1 remains the latest published installation-recovery APK. No v0.6.0 APK update is advertised until owner-managed signing and a migration plan are supplied.

Main builds can load an encrypted keystore from GitHub secrets `AGENTFOLD_SIGNING_STORE_BASE64`, `AGENTFOLD_SIGNING_STORE_PASSWORD`, `AGENTFOLD_SIGNING_KEY_ALIAS` and `AGENTFOLD_SIGNING_KEY_PASSWORD`. The decoded file has private permissions in RUNNER_TEMP and is never included in source/artifacts. Pull-request builds do not receive these secrets. No secrets are provided by the current connection.

Publication additionally requires repository variable `AGENTFOLD_RELEASE_ENABLED=true` and `AGENTFOLD_SIGNING_CERT_SHA256` matching the APK signature report. Leave publication disabled until a migration has been agreed: a newly created key cannot update an old Recovery installation signed by a discarded runner key. Secrets/variables administration is unavailable through this GitHub connector. Never paste signing secrets into chat, source or reports.

References: https://developer.android.com/reference/android/animation/ValueAnimator and https://developer.android.com/guide/topics/ui/accessibility/views/custom-views
