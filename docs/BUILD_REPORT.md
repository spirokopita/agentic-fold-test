# Build report

Baseline: commit 8d9c555cbb07b74ef91c53e7a45cc86789c4ae72, run 37894696379, compilation SUCCESS. No automated unit/lint/device evidence was present in that baseline.

v0.4 attempt cf1be6219b880f7f84bbb9c34fb98bfd5b59cb9b, run 37896374548: FAILED. Root cause: instrumentation AndroidX dependencies required android.useAndroidX=true. Corrected in be3eb152726604dbe127314d93dbe1cfb24f387f.

v0.4 attempt be3eb152726604dbe127314d93dbe1cfb24f387f, run 37896672824: FAILED at lint. Root cause: existing document-picker grant variable failed WrongConstant inspection. Corrected using explicit read-grant constant in f69c40dd9196e6a567c5c979a744161cea88fdc9.

v0.4 f69c40dd9196e6a567c5c979a744161cea88fdc9, run 37897116075: SUCCESS. The pull-request checkout was synthetic merge commit 3a4ac274cba3006d2515eb4cb35a833b63c42c01. Downloaded XML confirms 10 unit tests and 3 Android 15 emulator smoke tests, zero failures/errors/skips. Android lint and both APK compilations succeeded.

Verified v0.4 APK SHA-256: d9b5ccb7366712911bd09a82cc446e4c8b74a7596729ff6633143ac6d7cc0a25 (2,452,826 bytes).
Verified v0.4 source ZIP SHA-256: fe2aa6d17641536237d633d393536feb5c755686c7240d9f068f9333c2c476c8 (27,638 bytes).

APK and source were uploaded to the approved Drive project folder; metadata readback confirmed filenames, sizes and destination. PR #2 merged after these automated gates passed. The old main commit remains in backup/v0.3.1-before-agent-runtime.

v0.5 ce4ec1e6b87a0ea3028d847020bf4917f2d1972d, run 37897752929: unit/lint/compilation SUCCESS; emulator subsequently cancelled by the next revision. No pass is inferred from that cancelled job.

Final PR run 37898521387, head 43bea249bd3279585bcfe1e396f4fbf2089757f9 and synthetic merge checkout 065528b211aad5e65fdefeea4fad5a3763ae399d: SUCCESS. Downloaded reports confirm 12 unit tests and 8 Android 15 emulator tests, zero failures/errors/skips.

Main code/release commit: 7789e6c10f8264ea191b96fc1154cb9b691e8567. Comparing its tree to the tested PR checkout returned zero changed files. Main validation run 37899308890: SUCCESS; downloaded reports independently confirm 12 unit tests and 8 emulator tests, zero failures/errors/skips. Lint: zero errors, 14 warnings. Warnings include target/dependency age, data-extraction configuration, static-context inspection, application icon, touch accessibility, localization and RTL; they are not suppressed or treated as proof of production quality.

Verified v0.5 APK SHA-256: 2288ca638f27da23d5b5dabf61a4c33bc7ef117d0fea031886d7dc631fd05bba (2,511,806 bytes).
Verified v0.5 source ZIP SHA-256: 7c2dff1e1a531e31fbc22ae3501f68c35ce0112cb4ae160f30b672ada383e212 (57,872 bytes).
ZIP integrity, expected APK contents and signing-block presence were checked. Physical installation is still untested.

Release workflow 37899926347: SUCCESS. It checked SHA256SUMS, source commit identity and test artifacts before creating tagged prerelease v0.5.0, with APK, source, test evidence, checksum manifest and commit identity. Release: https://github.com/spirokopita/agenticfoldapk/releases/tag/v0.5.0

Main APK/source were backed up in the approved Drive folder and read back by metadata (matching filename, size and parent). PR #2 and PR #3 are merged; the old baseline and v0.4 remain recoverable. The runtime test uses a fake model provider but REAL native tools and disk outputs. Live-provider reasoning and physical Fold behavior remain UNTESTED. No competitor superiority or production readiness is claimed.


## v0.5.1 installation incident and recovery

The owner reported “App not installed” on v0.5.0. The physical installer cause is unconfirmed because no detailed installer diagnostic is available. Archived v0.3.1, v0.4.0 and v0.5.0 signing certificates differ under the same package ID, a confirmed update incompatibility. Earlier emulator tests compiled their own APK and covered fresh installs. See INSTALL_RECOVERY.md for certificate fingerprints and recovery rationale.

PR #4 run 37902414381 passed unit/lint/build, signed-APK validation, installation alongside pinned legacy v0.5.0, same-APK reinstall/private data preservation and eight instrumentation tests against the delivered APK. The later release-only revision fixes the checksum manifest to cover all published assets; Android source and the installation check are unchanged.

Main commit 89b06878027154a466c176ed665cad0fecc98837, validation run 37902959993: SUCCESS. Downloaded XML records 12 unit tests, zero failures/errors/skips. Downloaded instrumentation log records OK (8 tests). Downloaded installation evidence records both packages present, successful Recovery reinstall and preserved private data marker. Android lint reports zero errors and 13 warnings. apksigner cryptographically verifies the delivered APK's v2 signature, using RSA 2048 certificate SHA-256 efe9b7504af37125852722883f48089b92c3af37dcee85d8272a7653f15c7890.

Published prerelease: https://github.com/spirokopita/agenticfoldapk/releases/tag/v0.5.1
Release target matches the validated main commit; the release API's APK/source asset digests match downloaded and locally recomputed hashes.

APK: AgentFold-v0.5.1-Recovery.apk, 2,512,614 bytes, SHA-256 b21f58976736fbf9bb64c93ca42946fe6cd2d186c3a359a7d6eec9c0b8d268b9.
Source ZIP: 64,620 bytes, SHA-256 4b273e7d8aee8c0eaeef559bd0c514d482f9ae536bceece7b6a868cdb6546c36.
Application ID: com.agentfold.app.recovery. Launcher/service identify Agent Fold Recovery. minSdk 29, targetSdk 35. This preserves the legacy app; it does not migrate credentials, private history or file/permission grants.

Physical Fold installation of v0.5.1 remains PENDING. The owner's v0.5.0 installation failure is recorded, not rewritten as a pass. Protected persistent signing for the next in-place Recovery update remains BLOCKED on signing-key administration; no private signing key is stored in source or artifacts.

## v0.6.0 network interface — development history

PR #5: https://github.com/spirokopita/agenticfoldapk/pull/5. The owner requested live activity plus whole-app Borg/network styling. Native Kotlin telemetry, canvas rendering, theme, responsive cards and icon resources preserve the existing bounded agent, file, manual control and STOP callbacks.

Run 37948785754: compilation/unit/lint passed; instrumentation failed one of ten tests because a fake-provider fixture expired during screenshot capture. Only the fixture wait was increased; the production runtime timeout was not extended.

Run 37949862591: compilation/unit/lint passed; instrumentation failed one of eleven tests. The new recreation regression revealed lost draft text: AdaptiveWorkspace attached children during measurement, after Android restored hierarchy state. Cards now attach during initialization; later width changes reparent the same views. Its screenshot captures contained a Quickstep ANR dialog and were rejected for visual review.

Runs 37951811228 and 37952117794 were superseded by reviewed STOP fixes. No completed emulator pass is inferred from cancelled jobs.

Run 37952513951: 12 unit tests passed, APK/test APK compilation passed, lint had zero errors and 26 warnings. Ten of eleven instrumentation tests passed, including objective/coordinate restoration. The screenshot obstruction assertion received a null accessibility root immediately after UiAutomation initialization. Downloaded logcat and last-anr.txt record no ANR since boot. Test automation now connects before activity launch, requests interactive-window events and uses a bounded wait for the expected active package; it still fails on a persistent covering window. The API 35 emulator uses a smaller Pixel 2 profile and stops its unrelated launcher before tests; system error dialogs remain enabled.

Source review also corrected two STOP ordering races: device authority is revoked before potentially blocking provider I/O cancellation, and immutable telemetry metadata copies are serialized with stage updates so stale worker snapshots cannot overwrite STOP. Hardware dispatch latency is not established by these changes.

Run 37953503972: automated gates passed (12 unit, 11 instrumentation tests; zero lint errors). Visual review still rejected the build. Reused Paint switched to FILL while drawing labels, leaving subsequent connector paths filled as triangles. The two captured busy frames were pixel-identical; a conditional animator assertion did not establish visible motion. Each route now explicitly selects STROKE. CI starts with animations enabled, passes requireAnimations=true, and checks three captured chart regions for actual pixel changes while the provider is waiting. Screenshots retain a visible test-fixture label; they are not evidence of real inference.

Final PR run 37954805359 passed compilation, 12 unit tests and 11 delivered-APK instrumentation tests. Downloaded results confirm zero unit failures/errors/skips and eleven successful instrumentation completion codes. Lint: zero errors, 26 warnings. Reviewed actual narrow, 1600x1200 and 1.6 font-scale captures show clean connector paths and native wrapping controls. Busy-frame pixels differ inside the chart. Diagnostics record a Quickstep bootstrap ANR before app tests, not an Agent Fold ANR; error dialogs were not globally hidden, and package/focus checks reject obscured captures.

PR #5 merged as 499cca0ecd3f2c722ea768e30081f124ec913489. Comparing this main tree with tested synthetic checkout e979bbf2d03d205e196f659204de4700403cb1c5 returns zero changed files. Main run 37955748516: SUCCESS. Downloaded evidence independently confirms 12 unit tests (zero failures/errors/skips), 11 passing instrumentation completions, install coexistence, preserved reinstall marker and real busy-chart pixel changes. Lint remains zero errors, 26 warnings. The stock launcher again recorded an ANR before tests; all captured app windows passed obstruction checks.

Main CI-only APK: 2,559,058 bytes, SHA-256 7057330f414aa8daaf774d2d7e990d45cebbad30e75d78f8a0b44edc4f85ef0a. Main source ZIP: 82,356 bytes, SHA-256 bb4a579e560e8ac183564563e28071c6280a6eeac618f132700ce6d458ecc0f2. Signature verification succeeds with certificate SHA-256 4aa084cf020e1bde929bcb3edcfb0b310c071debd62d50a71059e8ec61322ae0; signing-mode.txt explicitly records ephemeral-ci-only. This certificate differs from published v0.5.1, so this is not a compatible Recovery update.

Workflow 37956452126 records the validation result successfully and SKIPS the release job. No v0.6.0 release is advertised. v0.5.1 stays available; source rollback is also preserved in backup/v0.5.1-before-network-ui. Physical Fold installation/behavior, live-provider tasks, TalkBack, background execution and battery/performance remain unverified. Persistent signing administration and an explicit migration plan remain required.

## v0.6.1 Signal activity — development history

PR #6 adds native curved ribbons/particles to the activity panel alongside the retained Network tab. Version code 8, version 0.6.1.

Run 37961292792: compilation and 12 unit tests passed; lint zero errors, 26 warnings. Eleven of twelve delivered-APK instrumentation tests passed. The live fixture failed its initial MODEL assertion after an idle synchronization waited until the 60-second fake provider timed out; no active screenshots were generated (empty pulled files are rejected). Last-ANR reports none since boot. The test now checks expected telemetry with a bounded ten-second deadline and avoids waitForIdleSync during live motion. The fixture and production task deadlines are unchanged. Native visual invalidation is capped at 30 fps. All motion, STOP, window-obstruction and pixel-change assertions remain.

Source inspection of AndroidX ActivityScenario confirms onActivity itself calls waitForIdleSync off the main thread. Live assertions/actions now execute through runOnMainSync and invoke onActivity on the main thread; this preserves actual state assertions without needing the moving UI to become idle. Run 37962250460 was superseded before emulator validation; no pass is inferred from that run.

Final PR run 37962519304: SUCCESS. Downloaded XML confirms 12 unit tests with zero failures/errors/skips; exact delivered-APK instrumentation confirms 12 successful completion codes and OK (12 tests). Lint zero errors, 26 warnings. Actual Signal and Network captures reviewed, along with narrow/wide/1.6-font layouts. Busy frame differences are inside the Signal plot; all idle dashboard focus checks pass. Last-ANR reports none since boot. The waiting model is a labeled fake-provider fixture.

PR #6 merged as 58f82b5c230f913da5ae539e515348c9d7d4a395; its tree equals tested checkout c97310acffec9454d1ee3b467a19e3eb5ed59559 (48a8d64040a03f84ce1963d234753931c9181839). Tested PR APK: 2,568,198 bytes, SHA-256 2966e738efb134c9b59edfe7fbf7c082cc94330dbfece906a3fac82c9aa71551. Source ZIP: 93,116 bytes, SHA-256 87a857021d61d66fb557d531cb2bcdc84cc47bcab317095ae60e0b54481f1d41. Certificate 92b421f23c1e096354eaa03c37eadb2a80b7099f1ec1178f11b23bca3f991647; ephemeral-ci-only. No compatible phone update is published. Source backup branch backup/v0.6.0-before-signal-ui retains the previous canonical snapshot.

Main v0.6.1 run 37963491434 completed SUCCESS after the previous session. This run's pass is confirmed by GitHub status; the 12+12 downloaded/visually reviewed evidence above still refers specifically to PR run 37962519304. No separate physical/live-provider result is implied.

## v0.6.2 compact workspace

A phone-usability review found the repeated header and full card stack buried task controls under the graph. Compact fixed status/navigation now exposes focused Activity, Task, Files, Control and History panels; wide Activity retains task/graph collaboration. Existing callbacks and input views are preserved. Workspace/hierarchy restoration, hidden-chart pause/resume and real panel captures are included in delivered-APK tests. Final results below.

First v0.6.2 PR run 37993634780: SUCCESS. Downloaded XML/logs confirm 12 unit and 14 Android 15 tests; lint 0 errors, 26 warnings. Actual Task screenshot shows the objective and Run action visible without vertical scrolling on the 1080×1920 cover-sized emulator. All panel captures, wide Activity and 1.6-font layouts reviewed; last-ANR none since boot. Additional refinement dismisses the editor keyboard/releases focus when switching panels, preserving text. CI now selects the wide Task tab from observed accessibility bounds, verifies the resulting selected state and requires the Run action width to span ≥85% of the viewport before capturing it. A new build is required for these changes; no previous pass is substituted.

Final refinement run 37994689158: SUCCESS. Exact delivered APK passed 14 Android 15 tests; XML confirms 12 unit tests, no failures/errors/skips. Lint zero errors, 26 warnings. SHA256SUMS recomputed for APK, test APK and source. APK v2 signature verifies. Actual wide Task, narrow Task and 1.6-font screenshots reviewed; observed UI confirms Task selected and Run button [52,489][1566,537] spanning 94.625% of 1600px width. Last-ANR none since boot. Keyboard focus regression passed; unsubmitted inputs retained after recreation. Model activity remains a labeled fixture, not live inference.

PR #7 merged as ba8e3cd3df74a556ecd4cb9d37258fd15b8c05a4. Merged tree 0b651b27c84726cbb3aeaf8f04443b9f845323e7 equals tested synthetic checkout aa7e51bdeb548e8be95650b08443c96075e53886. Main run is independently triggered; reviewed evidence refers to the PR run. CI-only APK 2,582,102 bytes, SHA-256 545bfe25184455da3f98358720a4278f9eab14db5bf80d90080cac5606642566; source 102,359 bytes, SHA-256 87a370b1383f9d8e67861437fbdfb7ffbb88032e33a7ff56763e7fecd2e0367d. Certificate 1be490f1491633daac2dfd61861647b6066da90300b65a9b272e3cc955e052a8 is ephemeral-ci-only and differs from v0.5.1; no phone update is published. Signing/migration and physical tests remain blocked/pending.

## v0.6.3 approved PDF investigation

Run 38081962699 failed compiling the Android test APK: PdfDocument has close() but does not implement the AutoCloseable interface used by Kotlin use(). The production APK, unit tests and lint had already progressed; no emulator pass is inferred. The synthetic fixture now closes PdfDocument explicitly in finally. A new build is required. Native PDF parsing itself remains isolated; tests exercise real provider descriptors and renderer output, with fake model responses.

Run 38082235176 compiled both APKs and passed 12 unit tests with no failures/errors/skips. Downloaded lint XML recorded 0 errors, 28 warnings; two PDF API-annotation warnings are replaced by actual SDK guards. Emulator validation was superseded by a lifecycle refinement; no complete pass is inferred. Each read now uses bindIsolatedService with a unique valid instance name, so an old parser teardown cannot kill the next request. New validation required.

Run 38082618977 compiled and passed 12 unit tests; downloaded lint XML confirms 0 errors/26 warnings and APK/source checksums match. Fourteen existing Android tests passed before the first PDF fixture read. The test provider process then crashed with NoClassDefFoundError: kotlin.jvm.internal.Intrinsics, because Gradle excludes target-provided Kotlin classes from the standalone test APK. Android later killed the dependent instrumented app; instrumentation reported Process crashed. Last-ANR none since boot. This is a genuine failed test run, not a parser pass. The test-only provider now uses framework/Java classes and explicit resource closure; native production isolation and all assertions are retained. New validation required.

Final run 38083487730: SUCCESS. Head 323a87811615841305e9ccb340d091b46cbb72b5; tested synthetic checkout 41461d5eb270f53b3f3fa92d81620f0fbee98b6e. Downloaded XML/logs confirm 12 unit tests with zero failures/errors/skips, 19 delivered-APK Android 15 instrumentation tests, and lint 0 errors/26 warnings. Five PDF regressions pass: real text extraction/isolation, denied scope and schema, malformed/blank/mixed/size/page/text limits, cancellation during provider copying, and real read → report → readback with a fake model. The Java/framework standalone fixture resolves the recorded missing-Kotlin crash without dropping assertions.

Actual pdf-tool-result.json records 2 pages, 0 pages without text, 188 extracted UTF-8 bytes, source 18,535 bytes and SHA-256 7e3c44b117f0f72941682dbe030e5507264ec6ca7326f4cf87655acdcdfeb582. parser_isolated=true and network_allowed=false. Embedded hostile instructions remain untrusted content; scoped tools cannot send files. The synthetic report exists and reads back; copied evidence SHA-256 d06341cb7a2b6ef783a0a4a7b14e2e5c29491916258283662d2c6c0918de4ec3. This does not establish real-model resistance to answer bias.

Actual narrow Task, wide Task and activity/large-font captures reviewed. Wide Task action remains 1514/1600 pixels (94.625%). Delivered-APK coexistence and same-certificate reinstall preserve the marker. Last-ANR none since boot. PR #8 merged as 124e704bb89239ae0a038345d5ab520148357066; its tree equals the tested checkout: e9ecfdf02cc524403804661bf3296efc10191b42. Main validation is separately triggered; detailed reviewed evidence refers to this PR run.

CI-only APK: 2,602,714 bytes, SHA-256 ac20e7a298c525cba601797fd031aec4b3647376c5b965cab0744d9ef24fa6d0. CI source: 115,853 bytes, SHA-256 5640d7f5be43b8d9f35226b3194f18b61da2e03b1927c8084d32ff330a5965c3. Test APK: 2,246,559 bytes, SHA-256 872b57fedc938ea2cc8f3ff119222ca4dd9feae529f2ea446776ce1893fd3ee8. All SHA256SUMS recomputed and ZIP integrity inspected. APK v2 signature verifies with certificate 993d45a8256ef9dac04bacbdff524e6c1103b1926551ca3e890e44df8c4bd84a, ephemeral-ci-only. It differs from published v0.5.1; no compatible v0.6.3 Recovery update is released. Protected signing/migration, real provider/SAF export, physical Fold, TalkBack, battery and live model evidence remain pending.
