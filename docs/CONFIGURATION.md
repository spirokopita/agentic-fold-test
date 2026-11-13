# Configuration

In v0.6.3, open **Task** to configure the provider, enter an objective and approve execution. **Files** opens the document picker and log export. **Control** contains Android permission links and manual device tests. **History** contains the local task journal controls and audit trail. **Activity** provides the Signal/Network view; on wide windows the task remains alongside it. The fixed status header and STOP remain visible. Workspace switching keeps the approved task alive; leaving the app cancels it.

Configure an HTTPS OpenAI-compatible API base URL, exact tool-capable model ID, and your API key in the app. The URL cannot contain embedded credentials, query parameters or fragments. Changing endpoint requires entering a key again, preventing silent reuse of another provider's credential.

The initial adapter uses Chat Completions, strict function schemas, parallel_tool_calls=false and max_completion_tokens=2048. Some “compatible” providers do not support these fields; compatibility must be tested with the owner's chosen model. Native Anthropic/Gemini and embedded local inference adapters are not implemented. A user-controlled HTTPS inference gateway can expose the same protocol.

No provider has been live-tested without owner credentials. CI does not contain or require any cloud API key. The instrumentation fake validates orchestration and local effects, not model intelligence or real API availability.

Each task requires explicit approval of its objective, provider and optional selected-file transmission. Choosing a file is not permission to send it. The agent cannot transmit other files or screen contents. Never place secrets in an objective or selected file unless intentionally sharing them with that provider.

Tasks have eight-call and content-size limits. A three-minute deadline is checked between operations; an ongoing request can run until its network timeout. The 24,000-token threshold uses reported usage or a heuristic fallback; it is not a guaranteed monetary cap. Set a provider-side spending limit.

The API key is encrypted with Android Keystore. “Forget key” cancels running work and removes the key. Private tasks/reports remain in app-private storage with Android backup disabled; task metadata is not separately encrypted. Clearing history removes the journal and saved draft; reports are separate files.

PDF investigation: select a text-based PDF in Files, enter the objective in Task and explicitly approve sending selected-file text to the configured provider. Android 15+ is required; input ≤4 MiB/24 pages and extracted text ≤24 KiB. Native parsing is local and isolated; only approved extracted text/metadata go to the model. Scanned pages have no OCR, and protected PDFs are unsupported. A file selection alone does not approve model access. Use Run without file to deny document access. Export uses the existing verified-report document picker.
