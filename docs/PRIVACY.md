# Privacy behavior

## Always local

- Run summaries, GPS points, laps, routes, goals, and health samples are stored on the phone.
- The app has no sign-in, cloud sync, social feed, advertising identifier, or analytics SDK.
- The app has no social sharing. A user can explicitly save a run as a GPX file through Android's file picker.
- Cloud backup and device-to-device transfer are excluded for app data.

## Network use

The Android manifest declares Internet access for map packs. MapLibre is set offline by default. Map downloading starts only after the user confirms and chooses to download an area. The provider can observe the device's network address and the requested region. The app does not include run activity or user identity in those requests.

Map downloading uses OpenFreeMap and follows its published offline tile, size, concurrency, and attribution requirements. A route map is displayed from the local offline store with map networking disabled. Run tracking cannot start while a map download is active; starting a run also disables map networking and pauses map packs.

## Permissions

- Precise location: requested only when the user starts a run.
- Notifications: requested on Android versions that require it for visible tracking status.
- Bluetooth scan/connect: requested only when the user taps the sensor search action; selected sensor samples are stored locally during a run.
- Health Connect: requested only when the user taps import for a specific completed run. Only the heart-rate data type is read and imported into the local database.

## Known security limit

Local app storage is protected by Android's application sandbox, but the Room database is not encrypted at rest yet. This project has not been security-audited and has not been built or tested on a device. Do not describe it as leak-proof or production-ready until storage, permissions, network behavior, and interrupted-run recovery have been reviewed on Android hardware.
