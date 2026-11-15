# Agent Fold v0.6.2 — compact operating workspace

- Fixed compact brand, permission/execution status and scrollable 48-dp-minimum workspace navigation.
- Direct Activity, Task, Files, Control and History panels use the existing working callbacks. STOP remains fixed across all panels. History now contains the actual local task-history inspection/deletion actions and audit log.
- Cover/narrow panes show focused content; wide Activity view retains the live graph and mission side by side. Single-panel views occupy the available width rather than leaving an empty column.
- Selection and draft/coordinate fields survive activity recreation. All inputs attach before Android restores their state, even when hidden.
- Switching panels does not cancel an approved task. Hidden graph motion stops and resumes only while visible, with event-driven layout visibility checks. Only phases with rendered activity start an animator; flow uses linear timing and a 30-fps invalidation cap.

Code 9; version 0.6.2. Final PR run [37994689158](https://github.com/spirokopita/agenticfoldapk/actions/runs/37994689158) passed: 12 unit tests, 14 delivered-APK Android 15 tests, zero failures, lint zero errors/26 warnings. Actual narrow, wide, large-text and all workspace captures reviewed. Wide Task Run bounds [52,489][1566,537] span 1514/1600 pixels. Switching tabs releases editor focus/hides the keyboard; the draft is preserved. No emulator ANR since boot. Physical Fold, TalkBack and battery/performance are untested. Persistent signing and migration remain required before an in-place phone update; published v0.5.1 remains preserved. No AAA/production quality score is claimed.

PR [#7](https://github.com/spirokopita/agenticfoldapk/pull/7) merged as ba8e3cd3df74a556ecd4cb9d37258fd15b8c05a4. Its tree 0b651b27c84726cbb3aeaf8f04443b9f845323e7 exactly equals tested checkout aa7e51bdeb548e8be95650b08443c96075e53886. Main post-merge validation was triggered; its outcome is separate from this reviewed PR evidence.

CI-only APK: 2,582,102 bytes; SHA-256 545bfe25184455da3f98358720a4278f9eab14db5bf80d90080cac5606642566. APK v2 signature verifies, certificate SHA-256 1be490f1491633daac2dfd61861647b6066da90300b65a9b272e3cc955e052a8; ephemeral-ci-only. This cannot update v0.5.1 Recovery. Source ZIP: 102,359 bytes; SHA-256 87a370b1383f9d8e67861437fbdfb7ffbb88032e33a7ff56763e7fecd2e0367d. Source rollback: backup/v0.6.1-before-workspace-navigation. Busy animation uses a labeled fake provider; no live model result is claimed.
