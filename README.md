# Stride Local

Stride Local is a personal Android running tracker. Run history is stored on the phone. There are no accounts, social feed, analytics, advertisements, or app server. The app is an early test build and has not yet been validated during a real run.

## Download for Android

[Download the latest test APK](https://github.com/ShlokRathi29/Map_run/releases/latest/download/StrideLocal-debug.apk)

This direct link works after a maintainer publishes a GitHub Release containing an asset named exactly `StrideLocal-debug.apk`. Until that release exists, use the build instructions below or ask the maintainer for the APK.

## Install and try it

1. On the Android phone, open the download link above and download the APK from the project's GitHub release.
2. Open the downloaded APK. If Android asks, allow your browser or Files app to install this APK, then tap **Install**. Only install a build you trust.
3. Open **Stride Local**. Allow precise location and notifications when prompted. Bluetooth and Health Connect permissions are optional and requested only when you use those features.
4. Go outside, tap **Start run**, and confirm the ongoing run notification appears. Try a short run, pause/resume, add a lap, and finish.
5. Open the saved run to review its route and splits. Use **Save GPX file** if you want a copy of its location data.

For a fuller checklist, see [Testing on a phone](docs/TESTING.md). This is a debug build for testing; it is not a Play Store release or a dependable daily training tool yet.

## Features in this Android build

- GPS run recording with start, pause, resume, finish, manual laps, auto-pause, and distance/lap beeps.
- Local run history, route trace, distance, elapsed time, pace, kilometer splits, and basic personal records.
- GPX route import and explicit GPX export through Android's file picker.
- Local weekly distance goals and 30-day distance challenges.
- Optional Bluetooth LE heart-rate sensor and per-run Health Connect heart-rate import.
- Optional offline map pack around the phone's current location. The rectangular bounds extend roughly 100 km from the center in each cardinal direction; the corners extend farther.

## Privacy and permissions

- Run records, GPS tracks, laps, routes, goals, and imported heart-rate samples are kept in the app's private on-device database. The app has no run-upload service or cloud sync.
- The app requests location for run recording and shows an ongoing notification while tracking.
- Network access is used for map downloads only after the user confirms the download. OpenFreeMap receives the requested map area and the device's network address. Map requests are disabled outside that download flow. The map is stored locally for offline viewing.
- Bluetooth and Health Connect are optional. Health Connect data is read only for a run the user chooses to import.
- GPX exports contain precise route locations. Save or share them only when intended. Android backup is disabled; uninstalling the app removes its local app data.
- The database is not encrypted at rest. This project has not had a security audit.

Read [Privacy notes](docs/PRIVACY.md) for details and limits.

## Build from source

1. Install Android Studio and its Android SDK 36 platform/build tools.
2. Open this repository in Android Studio and let Gradle sync. The included Gradle Wrapper pins Gradle 9.5.0; Android Gradle Plugin is 8.13.0.
3. To build a debug APK from Windows PowerShell:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

4. Run static checks:

   ```powershell
   .\gradlew.bat :app:lintDebug :app:testDebugUnitTest
   ```

The test APK is debug-signed and intended for testing. Do not use it as a production release. Rename it to `StrideLocal-debug.apk` when attaching it to a GitHub Release so the download link above works.

## Current verification and remaining work

Verified locally on 2026-11-19: `assembleDebug`, `lintDebug`, and `testDebugUnitTest` all pass; the three calculation unit tests pass, and the APK signature verifies. Lint still reports non-blocking advisories, including the target API level and available dependency updates. These checks do not replace installation and testing on real Android phones. Background GPS, process interruption, Bluetooth sensors, Health Connect, map downloads, and data retention still need device testing.

Still to build or validate: recovery of interrupted runs; turn-by-turn route guidance; elevation profile; training calendar and additional goal types; more robust route/segment matching; device testing; and the iPhone app.

See the [feature checklist](docs/FEATURES.md), [architecture](docs/ARCHITECTURE.md), and [phone testing checklist](docs/TESTING.md).
