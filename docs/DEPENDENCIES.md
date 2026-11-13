# Dependency inventory

| Component | Pinned version |
|---|---|
| Android Gradle Plugin | 8.9.2 |
| Kotlin Android | 2.0.21 |
| Gradle (CI) | 8.11.1 |
| Java target | 17 |
| Compile/target SDK | 35 |
| Build tools installed in CI | 35.0.0 |
| Minimum Android SDK | 29 |
| JUnit | 4.13.2 |
| AndroidX test runner/core | 1.6.2 / 1.6.1 |
| AndroidX test JUnit | 1.2.1 |

Runtime UI, accessibility, network, SQLite, JSON and cryptography use Android platform APIs; no cloud SDK or private credential is bundled. CI actions are pinned to resolved Git commit SHAs in the workflow. Transitive dependency locking, verified dependency checksums, a generated SBOM and vulnerability scanning remain required for production. A pinned build is not proof that every artifact byte is reproducible.

PDF extraction in v0.6.3 uses Android 15 PdfRenderer.Page.getTextContents in an isolated service. No third-party PDF dependency or native library is bundled. Android platform implementations/patches remain device-specific.
