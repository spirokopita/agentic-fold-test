# Agent Fold v0.6.3 — approved PDF investigation

Adds read_selected_pdf to the genuine bounded model/tool loop. Explicit per-task approval remains mandatory. The selected content URI is fixed by the owner, with no model-selected path or URI. Provider MIME metadata is a format hint, never an authorization.

Android 15+ native PDF text extraction runs in a non-exported isolated process with no app/network permissions. The owner process creates a read-only anonymous snapshot (unlinked before reading), hashes the actual source bytes and transfers only its descriptor. Parser results include page markers, pages without text, source SHA-256 and extraction limitations. No credentials, model instructions or other files enter the parser. The parser wait is bounded to 20 seconds; STOP is checked every 50 ms while waiting and between input chunks. Unbind ends the isolated process. A blocked external document-provider read is not a guaranteed hard real-time cancellation boundary.

Limits: 4 MiB input, 24 pages, 24 KiB extracted UTF-8 text. Documents beyond these limits are rejected without silent truncation. Scanned-only, malformed, password-required/unsupported-security documents and older Android versions get operational errors. Mixed text/scanned pages expose the empty-page count; OCR, image interpretation, perfect reading order and table structure are unavailable.

Tests generate actual synthetic PDFs through a test-only content provider and exercise extraction/isolation, consent denial, malformed/blank/oversized/page/text limits, cancellation during provider read, and the real PDF-read → report-write → readback runtime with a fake model. This is not live-provider reasoning, real SAF selection/export or physical Fold evidence. PR #8 merged; run 38083487730 passed 12 unit and 19 Android tests, with lint 0 errors/26 warnings. Downloaded PDF output verifies 2 real pages, isolated parser and no network permission; report bytes read back. Actual APK, source and test-APK checksums and v2 signature verified. See BUILD_REPORT.md for exact provenance and failed attempts.

Version code 10; 0.6.3. APK publication remains gated on persistent signing and a data-preserving migration plan. v0.5.1 remains the latest published APK. Rollback: backup/v0.6.2-before-pdf. No production/AAA readiness or comparative superiority claim.

Primary references:
- https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
- https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page#getTextContents()
