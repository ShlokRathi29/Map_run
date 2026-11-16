# Architecture

## Product boundaries

Stride Local is a single-user phone app. Activities are private local records. There are no accounts, follows, comments, social feed, advertising, analytics, or app-owned backend. The first client is Android; the iPhone client will be native Swift/SwiftUI after Android behavior is settled.

## Android layers

- **UI:** Jetpack Compose screens and system permission flows.
- **Tracking:** A location foreground service owns GPS collection during an active run. It remains visible through the Android ongoing notification.
- **Local data:** Room stores completed runs and ordered track points in the app-private database.
- **Domain:** Local Kotlin calculations for pace, splits, personal records, goals, challenges, and saved-route comparison.
- **Maps:** MapLibre offline regions backed by OpenFreeMap, downloaded only after a user confirmation. Cached maps and route overlays are rendered with networking disabled.
- **Integrations:** Optional Bluetooth LE heart-rate sensors for live readings and Health Connect read access for explicit per-run import. Both paths are permission-gated and write samples to the local database.

## Data flow

```text
Phone GPS -> RunTrackingService -> Room run + ordered track points -> local history and analysis
Explicit map-pack confirmation -> OpenFreeMap -> MapLibre local offline store -> route rendering with MapLibre networking disabled
Bluetooth / Health Connect -> explicit sensor or health permission -> local heart-rate samples
```

The app must not send run records, GPS track points, health samples, routes, goals, or activity statistics to any network service. Map downloads are the only network operation. They are started by the user, and the requested geographic area and network address are visible to the map provider.

## Storage and backup

The Room database is in Android's private app storage. Android cloud backup and device-to-device app data transfer are excluded in the manifest rules. GPX import/export is explicit through Android's file picker; it is not automatic. Database encryption at rest is not yet implemented; Android's app sandbox is the current storage boundary.

## Android recording lifecycle

1. The user taps Start.
2. The app requests precise location permission through Android's system UI.
3. The foreground service creates a local run row, then requests GPS fixes.
4. Accurate fixes are appended in order to the local database and used to calculate distance and elevation gain.
5. Pause removes GPS updates and excludes paused time. Resume requests GPS again.
6. Finish updates the local run summary and stops the foreground service.

If the process is killed during recording, recovery of an unfinished run is not implemented yet. That needs to be addressed before treating this as a dependable daily tracker.
