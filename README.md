# Stride Local

A personal running tracker designed around device-only run storage. There are no accounts, social feed, ads, analytics, or application backend. Android is the first implementation; an iOS client is planned as a separate native app after the Android foundation is settled.

## Current Android foundation

- Foreground GPS recording with a persistent notification while the screen is off.
- Start, pause, resume, and finish controls.
- Local run history backed by Room, with a route trace, kilometer splits, manual laps, and explicit GPX save/import.
- Personal weekly distance goals and 30-day distance challenges, calculated on-device.
- Offline route-map packs with a 100 km radius, downloaded only after confirmation and rendered without map network access.
- Audio beeps for kilometer marks and laps, with an auto-pause and audio setting.
- Optional Bluetooth heart-rate chest straps for live readings, plus user-triggered heart-rate import from Health Connect.
- Distance and elapsed-time pace summary.
- Runtime location permission requests; recording cannot begin without location permission.
- No run upload or cloud sync.

This is the first Android milestone. Turn-by-turn route guidance, personal-record summaries, elevation charts, richer goal types, and interrupted-run recovery remain to be implemented. The current app has not been built or tested on an Android device. See the [feature checklist](docs/FEATURES.md), [architecture](docs/ARCHITECTURE.md), and [privacy notes](docs/PRIVACY.md).

## Privacy model

- Run data and route points are intended to remain in the app's private local database.
- The app has no server, account, social functionality, or telemetry.
- Internet permission is used only for an explicitly user-started offline map-pack download. MapLibre networking is disabled at app startup, during route-map display, and during run tracking.
- Map packs use OpenFreeMap. The provider receives the requested region and network address. Map data is retained locally for later offline use. The downloader caps map tiles and stops near the provider's 50 MB limit; map detail can be reduced to keep the requested area within the provider's published download limits.
- Android may display a foreground-service notification during recording. The user grants location permission through Android's system prompt.
- Bluetooth scanning starts only after the user taps Find sensor and grants Bluetooth permission. Health Connect heart-rate data is read only after the user chooses to import it for a specific run and grants Health Connect access.
- GPX export is an explicit user action through Android's file picker. There is no automatic backup or upload.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 36 installed, then allow Gradle to sync. The project uses Kotlin, Jetpack Compose, Room, and Android's platform GPS provider. A Gradle wrapper is not included yet; create one from Android Studio or an installed Gradle distribution before using command-line builds.

The current development environment did not have Java, Gradle, or Android Debug Bridge available, so this project has not been compiled or run on a device here.

## Project layout

```text
app/src/main/java/app/stridelocal/
  data/local/       Room entities and DAO
  tracking/         foreground GPS service
  MainActivity.kt   initial Compose screens
```

## Next development steps

1. Build and validate on Android hardware, including screen-off GPS, pause/resume, auto-pause, Bluetooth heart rate, Health Connect import, GPX handling, and map downloads.
2. Recover or safely finalize an interrupted run after process termination.
3. Add remaining analysis views: elevation profile, richer personal-record windows, and improved route guidance.
4. Add calendar views and additional training goal types.
5. After Android behavior is stable, build the iPhone client with native Core Location, SwiftUI, and on-device persistence.

## Security notes

The source has no network client for run data. Before a release, review every dependency and network code path, add database encryption only if the threat model requires protection against someone with filesystem access, and verify Android backup/device-transfer behavior. `allowBackup` is currently disabled to prevent automatic platform backup of the local run database.
