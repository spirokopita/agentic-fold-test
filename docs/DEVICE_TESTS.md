# Phone-only validation card — v0.5.1

The cloud tests are automated evidence. These checks need the owner's real Fold, Android permissions and chosen model. No desktop is required.

1. Install AgentFold-v0.5.1-Recovery.apk. Open Agent Fold Recovery; the old Agent Fold must remain installed with its original data. Record the installer result before granting permissions. Configure the new app separately. Disable the old accessibility service before enabling the recovery service.
2. Open Agent Fold beside Chrome. Test both narrow and wide panes; the fixed STOP must stay visible. Rotate, fold and unfold; pending gesture authority must be revoked.
3. Enable accessibility yourself, choose the pane containing a harmless test page, then TAKE CONTROL. Confirm ONE scroll. Read DISPATCH and VERIFY in the log; a delivered gesture is not proof of task completion.
4. Start a confirmed scroll, press STOP immediately, and try another action. No new action may be dispatched until a new session is authorized. The original injected stroke may finish.
5. Move and minimize the floating bar. Open the dashboard again. Refuse notifications and confirm the visible overlay remains the STOP path; if all persistent paths are unavailable, starting control must fail.
6. Configure your chosen provider/model/key. Choose a small UTF-8 crash log or sample source, then approve file transmission for a task such as “Inspect this log, identify likely causes and create a diagnosis report.” Check the provider endpoint shown before approving.
7. Inspect the actual report, export it using Android's picker and open the exported file. Its bytes must have passed readback before the app reports success.
8. Run without file permission and ask to read another URI. The native tool must reject it. Test an unsupported PDF/binary file: it must fail clearly, never pretend to summarize it.
9. Disconnect the network or use an invalid model ID. The task must stop with an informative error. STOP must remain usable while a request is pending.
10. Kill/reopen the app. Control stays OFF; unfinished tasks are marked INTERRUPTED; the last existing report can be restored. No task or gestures may resume automatically.

Record test ID, exact APK commit/hash, Android/One UI version, folded/unfolded state, pane geometry, result and observed failure. A phone screen recording is useful for STOP/fold timing. Do not include passwords or private communications in recordings.

Live-provider completion, true on-device STOP latency, fold behavior, OEM restrictions, battery use and crash-free stability are still unmeasured until these checks are recorded. Report any failure with the exported action log and the APK version.

## v0.6.3 PDF check on the phone (pending)

After signing/migration permits a compatible installation, select a small text-based PDF through Files. Configure a real provider, request a summary with page references, approve selected-file transmission, inspect the returned evidence/limitations and export the verified report through the system picker. Reopen the exported file and compare it to the PDF. Repeat with Run without file, a scanned-only PDF, a mixed text/scanned PDF, a password-required PDF, a document >4 MiB or >24 pages, and STOP during reading. Confirm no subsequent tool dispatch or unexpected control grant. Android <15 should reject PDF extraction and keep UTF-8 tools usable. Do not treat synthetic-provider emulator results as a pass for these cases.
