# Feature checklist

Status describes the current Android project, not the final product goal.

| Feature | Status | Notes |
| --- | --- | --- |
| Start, pause, resume, finish | Foundation added | Requires precise location permission. |
| GPS while screen is off | Foundation added | Uses a location foreground service and ongoing notification. Device behavior still needs on-device verification. |
| Local run history | Foundation added | Room entities and DAO; current screen shows distance, duration, and average pace. |
| Route track storage | Foundation added | Ordered latitude/longitude/elevation samples stay in the local database. |
| Distance and average pace | Foundation added | Calculated from accepted GPS points. |
| Elevation gain | Foundation added | Basic GPS altitude accumulation; needs filtering and validation. |
| Splits and manual laps | Added | Manual laps are recorded locally; run detail shows kilometer splits and laps. |
| Pace and elevation charts | Partial | Local pace-by-kilometer chart and route trace are shown; elevation profile remains. |
| Auto-pause and audio cues | Added | Auto-pause and kilometer/lap beeps have on-device settings; behavior still needs device verification. |
| Routes and route comparison | Partial | GPX routes can be imported and stored locally; route map and endpoint-matched efforts compare against the user's own past runs. Turn-by-turn guidance remains. |
| Segments and personal records | Partial | Local route efforts are matched by start/end GPS points; personal-record summaries remain. |
| Training calendar, goals, challenges | Partial | Weekly distance goals and 30-day distance challenges are local; calendar views and other target types remain. |
| Offline map packs | Added | Explicit opt-in OpenFreeMap download, 100 km radius, locally stored region, cached-only map rendering, and pack deletion. Zoom is bounded to keep tile/resource downloads near the provider's cap. |
| Heart rate | Partial | Bluetooth LE sensors provide live run samples; a per-run Health Connect import adds phone-health samples locally. Device behavior still needs verification. |
| GPX import and export | Added | GPX route import and user-triggered GPX run save use Android's file picker. FIT support is not implemented. |
| iPhone app | Not implemented | Android-first decision; planned native Swift client with local storage. |

## Important limits before daily use

- This project is a source foundation, not yet a verified installable release. It has not been compiled or tried on Android hardware in this environment.
- An interrupted run is not yet recovered after Android kills the process. The UI and service state can also reset if the process is terminated.
- Database encryption is not implemented. Data is in the private app sandbox and Android backup is disabled, but a device owner with privileged access may still inspect app storage.
- Map downloads involve a third-party map provider and expose the selected region plus the device network address. All run history remains local; maps can also be left unused.
- GPX import is bounded to one million points. GPX exports contain the route's precise location history, so only save/share files when intended.

## Map-pack size constraint

OpenFreeMap permits offline downloads up to 3,000 tiles or 50 MB per download, with at most four parallel requests and a unique User-Agent. Stride Local limits the zoom range to stay within a conservative tile budget, then stops the download near 48 MB. The provider sees the requested area and the device's network address. OpenFreeMap and OpenStreetMap attribution is supplied by the map style. The public OpenStreetMap tile server is not used for offline bulk downloads. See the provider's [mobile quick start](https://openfreemap.org/quick_start/) for current limits and attribution.
