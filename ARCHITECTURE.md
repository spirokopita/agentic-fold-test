# Architecture

`app` contains the NERV activity, MAGI orchestration, stores, widgets, Android integrations and the original Agentic Fold runtime. NERV is the launcher; the legacy activity remains in the project.

`phyphox` is an Android library module built from the official phyphox 1.2.1 source. The Java/Kotlin experiment engine, sensor acquisition, graph rendering, recording, export and C++/FFTW analysis are retained. The integration changes Android library packaging, theme resources, typography and host-relative file sharing. Experiment activities are private in-app destinations opened from TOOLS.

The host application inherits phyphox's small application object so the engine can retain the active experiment. A lifecycle callback applies NERV styling only to phyphox activities. It does not change sensor calculations or Agentic Fold behaviour.

Provider configuration belongs to the user. Existing encrypted stores hold shared and per-node credentials. Signing keys, runtime app data and local SDK paths are not source assets.

CLASP is an empty navigation destination. No AI merging, model composition or shared-memory implementation is provided by that placeholder.
