# NERV / Agent Fold

Native Android interface for the Agentic Fold prototype, with a three-node MAGI workspace and an embedded phyphox experiment lab.

## Features

- NERV lock screen, clock, horizontal navigation and configurable themes.
- MAGI debates and voting across independently configured AI nodes.
- Chat history, notes, taught skills, reminders and timers.
- Android voice input and speech output.
- User-approved Agentic Fold phone-control and selected-file tools.
- Embedded phyphox 1.2.1 with 40 bundled experiment definitions, live graphs, recording and export. Availability depends on the phone's sensors.
- A reserved, empty CLASP tab for future AI composition work.

The pixel background uses 90% opacity. Buttons have solid fills; selected controls retain their accent colours. MAP currently uses a native globe/GPS view; external terrain and imagery layers are not included.

## Build

| Component | Version |
| --- | --- |
| Compile / target SDK | 35 |
| Minimum SDK | 29 |
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.9.2 |
| Gradle wrapper | 9.6.0 |
| Build JDK | 21 |
| Android NDK | 28.0.13004108 |
| CMake | 3.22.1 |

Open the project in Android Studio and install the listed SDK, NDK and CMake packages through SDK Manager. Then run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Its application ID is `com.agentfold.app.recovery`. The first native build compiles phyphox's FFT library and takes longer than subsequent builds.

## Configuration

Enter your provider endpoint, model and API key in SETUP. Provider credentials are not included in the source. Saved MAGI node keys are encrypted using Android Keystore.

Accessibility-based phone control requires an owner-started, approved session. Experiments may require microphone, camera, Bluetooth or location access depending on the measurement. phyphox remote access is opt-in.

Local SDK settings, signing stores and runtime app data are excluded from version control. Keep your signing key private: APKs signed with different keys cannot update one another in place.

## Project layout

- `app/`: NERV screens and the retained Agentic Fold runtime.
- `phyphox/`: upstream sensor/analysis engine, experiments and NERV styling.
- `docs/`: current architecture and feature notes.

## Licensing

The combined application includes GPL-3.0 phyphox code. Licence text is included in `LICENSE` and `phyphox/LICENSE`; upstream component notices remain in the source and `phyphox/README-upstream.md`. See `NOTICE` for provenance. This modified integration is independent of the official phyphox release.

The Japanese font is stored losslessly compressed under app/font-source. Gradle restores the original TTF before building; no network font download is required.
