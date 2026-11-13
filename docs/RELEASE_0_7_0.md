# Agent Fold v0.7.0 — regular app + 1×1 voice widget

## Product change
The app remains a standard Android LAUNCHER activity. It does NOT register as a HOME app or replace the user's launcher. The old adaptive dashboard remains accessible as a diagnostic/task UI.

A **1×1 Voice ON/OFF home-screen widget** starts a visible, user-initiated microphone chat; tapping again ends it. Starting chat presents a disclosure and requires microphone permission. A microphone foreground service, Android microphone indicator and STOP notification remain visible. The user must configure their own tool-capable OpenAI-compatible HTTPS model/endpoint first, and the phone's Android speech recognition service must be available. The recognition provider may process speech remotely. AI provider billing is separate.

## Phone actions
The model's tools are now:
- `phone_observe` — bounded UI tree with labels/clickability/bounds; no passwords/edit-field contents or system settings.
- `phone_tap` — active-app percent coordinate tap.
- `phone_scroll` — one scroll gesture.
- `phone_type` — replace text in a focused, ordinary editable field; limited to 500 characters.
- `phone_back`, `phone_home` — owner-approved navigation; their tool responses explicitly do **not** claim verified completion.
- Existing `read_selected_pdf`, `read_selected_text`, `write_report` retained.

Phone tools work only after the user enables Android Accessibility and separately presses `TAKE CONTROL`; authorization expires after 5 minutes. Taps, scrolls, text entry and navigation require individual one-time approvals and change/verification where available. The model can read approved visible UI labels only while this session is active; provider disclosure calls this out. The service intentionally refuses protected windows, password fields and ambient non-approved screen monitoring. STOP immediately revokes control and cancels agent work; it also ends voice chat.

## Limitations / build status
- This is a **source change**, not a compiled or signed APK. Android SDK and Gradle are unavailable in the current workspace, so on-device behaviour, instrumentation tests and compilation are **not verified**.
- Typical screen interaction works through Android Accessibility only; secure UI and system prompts remain manually operated.
- Built-in Android `SpeechRecognizer` is utterance-based, not an always-listening custom wake-word engine. The service listens again after it speaks; device speech services may impose rate/time restrictions.
- Voice chat keeps only the preceding three speech/reply pairs in memory while the mic service is active; it does not save a persistent voice history. The underlying provider is still called separately for every utterance. Actions share the existing eight-call and three-minute task limits.
- Reading labels may disclose sensitive personal information to a user-configured remote provider. `phone_observe` omits password and editable contents but is not guaranteed to detect every form of sensitive text. Use control sessions only in intended apps.
- Device unrestricted shell, arbitrary filesystem paths, automatic purchases/messages, silent screen recording and root are intentionally absent.
- Android Play Store distribution of general-purpose Accessibility automation requires careful Play policy review and consent; sideloading is possible, subject to Android's app/install controls.
- A full Android instrumentation, package install, 1×1 widget, background microphone, TTS, UI action and cancellation regression run remains required on a physical Z Fold running Android 17.

## On-device setup
1. Build the `app` Gradle module with Android SDK 35 and a compatible Kotlin Gradle plugin.
2. Install the APK signed with a key appropriate for a new install; original release signature is unknown, so **do not assume in-place update compatibility**.
3. Open Agent Fold, configure a tool-capable HTTPS model endpoint and API key.
4. Android home screen → Widgets → Agent Fold → Voice ON/OFF (1×1).
5. Tap widget → grant mic → accept visible disclosure → speak a short question. Tap widget to stop.
6. For app operation, enable Agent Fold Accessibility in system Settings, open Agent Fold and tap TAKE CONTROL (5 minutes), then use the widget/voice. Approve each action from the on-screen dialog. STOP revokes control and voice.
