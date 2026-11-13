# Security review — development, not certification

## Enforced in source

- Main-looper device dispatch; memory-only authorization generations; five-minute sessions.
- Per-action confirmation, ten-second ticket expiry, exact window/package/geometry preconditions, default-display restriction.
- STOP invalidates queued work before UI callbacks; stale callbacks cannot affect a newer session.
- Dashboard, overlay and notification STOP revoke device authority before model network disconnection. Telemetry copies share a lock with phase changes, so late worker metadata cannot restore an active graph phase after cancellation. Physical STOP latency remains unmeasured.
- No autonomous model access to device input. System/settings/install windows blocked for manual gestures.
- No ambient screen-text collection; password/sensitive/incomplete trees fail closed.
- Task scope immutable; model cannot choose alternate files, URI, network endpoint or filesystem path.
- Owner confirms cloud transmission for every objective and optional file.
- HTTPS only, certificate verification remains enabled, redirects disabled, bounded responses and timeout limits.
- API key encrypted with Keystore; no key or raw provider error body in logs.
- File reports are created as new files and byte-verified. No source overwrite/delete tool.
- Exported receiver is disabled; notification PendingIntent immutable; Android backup disabled.
- CI signing credentials are loaded only for main builds when provisioned. Publishing requires owner-managed signing, a matching public certificate fingerprint and explicit release enablement; ephemeral runner APKs remain validation artifacts.

## Material limitations and required work

Accessibility structure does not reliably identify every sensitive screen, and raw manual taps have no semantic risk classifier. Use manual controls only for non-sensitive test actions. Protected nodes may be unavailable entirely. UI fingerprints intentionally omit text, so text-only changes can remain UNCHANGED.

Ordinary human touches are not reliably observable without changing touch behavior. Injected gestures can cancel existing touches; true independent input is not provided. STOP cannot recall a stroke already accepted by Android.

Prompt injection cannot expand the two native tool capabilities, but may bias answers or report contents. Additional adversarial evaluations, outbound information controls, typed trust labels and taint tracking are needed before broad connectors or UI actions.

Private SQLite objectives/summaries and private reports are sandboxed but not separately encrypted. Keys can still be used by a compromised unlocked app process. Long-lived third-party API keys are appropriate only for owner-controlled development; production requires a scoped credential service.

No PDF extraction, screen capture, microphone or hidden observers are implemented. No runtime installs apps, changes security settings, sends messages, spends money or publishes content.

Automated authorization and tool tests are not a physical STOP timing audit, penetration test, Play Protect assessment or store-policy approval. Production is blocked until these are evidenced.

PDF parser boundary (v0.6.3): owner-selected URI and explicit per-task transmission approval are validated before access. Only an anonymous read-only snapshot FD reaches a non-exported isolated process. No credentials, app data, networking, URI/path dispatch, forms, links or scripting are exposed. UID/network isolation is checked in actual tests. Input ≤4 MiB, ≤24 pages and ≤24 KiB text; parser wait ≤20 seconds. Copy/parser outputs are not silently truncated. STOP unbinds the parser; service destruction kills its isolated process. The document-provider read boundary and native/parser/OEM implementation still require adversarial/device testing. This is defense in depth, not a claim that native PDF parsing is vulnerability-free. Tool contents remain untrusted data; malicious text can still bias an answer.
