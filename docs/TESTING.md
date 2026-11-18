# Testing on an Android phone

Stride Local is an early debug build. Test it with a short, low-risk run in a familiar area. Do not rely on it for navigation, emergency location sharing, or as your only copy of a route.

## Before installing

- Download the APK only from the project's GitHub Releases page or build it yourself from this repository.
- Confirm the filename is `StrideLocal-debug.apk` when using the direct download link in the README.
- The APK is debug-signed. It is for testing and is not a production release.

## Install

1. Download the APK to the Android phone.
2. Open the APK from Downloads or Files.
3. If prompted, allow that app to install unknown apps, then confirm **Install**.
4. Open Stride Local. Allow precise location and notifications for the tracking trial. Notifications keep Android's ongoing GPS recording visible.

## First run

1. Go outdoors in a familiar, open area and wait for GPS reception.
2. Tap **Start run**. Check that the distance and timer appear and the ongoing notification is visible.
3. Lock the screen for a few minutes, then reopen the app and check that the timer and distance advanced.
4. Try **Pause**, **Resume**, and **Lap**. Finish the short run.
5. Open the saved run. Review the route trace, splits, lap, pace, and elevation summary.
6. Optionally save a GPX file. It contains precise location history; keep it private.

## Optional checks

- Toggle auto-pause and distance/lap beeps, then try a short walk/run.
- Pair a Bluetooth LE heart-rate strap if available. Verify that readings appear and are saved for the run.
- Import heart-rate data from Health Connect only for a run you choose.
- Download an offline map only if you agree that OpenFreeMap receives the requested region and your network address. Confirm the pack appears as ready, then view a route offline.

## Send useful feedback

Include the phone model, Android version, app version, which step you tried, and what happened. Avoid posting GPX files, screenshots with exact routes, health details, or other private data publicly. Uninstalling the app removes its local run data, so export any runs you want to keep before uninstalling.
