# Installation incident — v0.5.1

Owner reported “App not installed” for v0.5.0 on 2026-10-09. No installer diagnostic or device package list is available, so the exact physical-device failure remains unconfirmed.

## Confirmed packaging defect

The archived APK signing blocks contain these SHA-256 certificate fingerprints:

| APK | Signing certificate SHA-256 |
|---|---|
| v0.3.1 | `74b2b44717dd453e842f9aabe840d4adbb48da31d3a59e714bbca7819a7c58fa` |
| v0.4.0 | `13b88e9820d11a9d6cff3ec98c59920777c33d7f1667a8059cf6ca0ab444f661` |
| v0.5.0 main release | `5205f7abf558e6bd7eb515f70b8335602abdbd721e2c1644cf90cc96cbc7c5d3` |

All use the same legacy package identity. Android requires compatible signing certificates for updates. These unrelated keys provide no rotation lineage. CI generated a new debug keystore on each fresh runner. Earlier emulator tests rebuilt their own APK and performed fresh installs; they did not validate an upgrade on a device with a previous build.

Reference: https://developer.android.com/studio/publish/app-signing

## Recovery

v0.5.1 debug APK uses `com.agentfold.app.recovery`, displayed as **Agent Fold Recovery**. Install it alongside the old application; no uninstall or data deletion is required. Private reports, history, API keys, SAF permissions and accessibility grants do not migrate. Configure the new app separately. Disable the old accessibility service before enabling the new one.

CI checks the delivered APK's signature and package metadata, installs pinned v0.5.0 first, installs Recovery alongside it, checks both packages remain installed, reinstalls the same Recovery APK with a private data marker preserved, and runs the packaged instrumentation tests against that exact APK. This does not prove a future independently signed APK can update Recovery, nor does it substitute for the owner's physical installer result.

## Remaining signing work

No legacy private signing key has been recovered. A public certificate cannot reconstruct its private key. Do not commit/upload a private signing key with source or APK artifacts. Future in-place updates require an owner-controlled protected keystore and CI secrets/environment. Signing-key administrative access is not available through the current GitHub connector. A debug-key cache exposed to pull-request jobs is not a production signing solution. The recovery identity is a one-time workaround; do not repeatedly change the package identity to hide this unresolved requirement.

If this recovery APK still produces “App not installed”, record the exact installer detail and Android/One UI version. The signing workaround does not bypass Android/Samsung security settings, installation permissions or other device restrictions.
