# Agent tool reference

| Tool | Scope | Verification | Unavailable operations |
|---|---|---|---|
| read_selected_text | One user-selected content URI, explicitly approved for this task | Provider read succeeds, bounded size, valid UTF-8, binary rejection | Other URIs, binary extraction, directory scan |
| read_selected_pdf | One approved selected content URI, Android 15+ | Read-only anonymous snapshot; actual source SHA-256; isolated native parser; page markers/empty-page count; ≤4 MiB/24 pages/24 KiB text | Other URIs, OCR/images/passwords, arbitrary paths; older API levels |
| write_report | One new private report, safe txt/md/json/csv filename, ≤64 KiB | Exact byte readback and SHA-256 | Input overwrite, arbitrary path, deletion, publication |

Tool definitions use strict schemas. Native validation rejects unknown tools, extra keys, incorrect types, replayed call IDs, excessive sizes, invalid filenames and denied URI scope. File/tool content is labeled untrusted data. This reduces available effects; it does not make malicious document content incapable of biasing a model's answer.

Manual device actions are a separate owner-operated executor and are NOT tools available to the model. Input delivery, observed UI change and objective success are distinct events.

Reports are generated in app-private storage and remain inspectable/exportable. Export uses a user-chosen SAF document and verifies byte readback before reporting success. JSON/CSV outputs are text reports; full semantic validation of their formats is a future gate.

PDF operational errors are fixed messages; native exception details and paths are not returned. The parser has no app/network permissions. Wait timeout 20 seconds, cancellation checked every 50 ms during the wait and between provider input chunks. A provider blocked inside read() can still delay cancellation. Native parser unbind terminates its isolated process. The snapshot is unlinked before reading, so process death leaves no named PDF copy. Extracted content and reports remain untrusted data; a bounded registry is not proof of immunity to answer bias.
