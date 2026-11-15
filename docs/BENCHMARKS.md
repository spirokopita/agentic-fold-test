# Competitive benchmark — 2026-10-09

## Primary evidence reviewed

- Meta Muse newsroom: https://about.fb.com/news/2026/09/introducing-muse-personal-ai-agent/
- Muse product/download pages: https://ai.meta.com/muse/ and https://ai.meta.com/muse/download/
- Gemini current Android automation help: https://support.google.com/gemini/answer/16940971?hl=en
- Claude Android integration help: https://support.claude.com/en/articles/11869629-use-claude-with-android-apps
- Android gesture API: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

Muse documents persistent cloud computer/browser execution, connectors, memory, approval for sensitive operations and an activity trail. It can continue work after its client closes. Those are documented capabilities, not Agent Fold parity.

Gemini documents selective Android screen automation, per-app permissions, progress/stop controls, and a virtual phone environment. Current help includes Z Fold 8 among supported devices, with region and language restrictions. Australia is not among the listed screen-automation regions in the reviewed page.

Claude documents Android app integrations for messaging, email, calendar, alarms, timers and navigation. These integrations are distinct from arbitrary cross-app visual automation.

## Independently accessible test

Opened Muse's public web interface on 2026-10-09. It rendered its landing/features page and a required mobile/email sign-in flow; Continue was disabled without identity input. No authenticated task could be run. No account was created, credentials supplied or regional restriction bypassed. Native competitor apps and a physical Fold were unavailable in this environment. Therefore comparative completion rate, latency, cost and reliability remain UNTESTED.

## Proposed advantages to measure — not superiority claims

| Candidate advantage | Reproducible measure | Current evidence |
|---|---|---|
| Local app-window control | Verified non-sensitive workflow in an actual Android test app | Manual executor source; hardware pending |
| Fold workspace | Chrome and dashboard coexist at narrow/wide pane sizes | Dashboard source and emulator smoke gates |
| Transparent portable outputs | Every output exists, byte readback succeeds, hash matches | Local tool/export implementations; test evidence tracked |
| Owner chooses model | Same task across two compatible providers | Adapter implemented; live credentials unavailable |
| Immediate interruption | No dispatch after STOP; physical p95 stop-to-revocation | Pure authorization tests; device timing pending |
| Scoped privacy | Malicious tool request cannot select another URI or endpoint | Native capability registry and adversarial test fixtures |

Run 20 repetitions per workflow on the same Fold, record completion rate, verification failures, time, user interventions, API usage and battery delta. Keep local-only and connected-cloud tasks distinct. Competitors with unavailable features are marked unavailable, not assigned a fabricated failure or score. A performance advantage requires a measured difference and confidence interval.
