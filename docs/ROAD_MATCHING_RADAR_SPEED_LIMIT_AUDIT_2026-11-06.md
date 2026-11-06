# NEXT NAVI PRO — Alpha 13 road matching, radar and speed-limit engineering audit

Date: 2026-11-06
Base under audit: `feat/alpha13-device-heading-fusion-2026-11-06`
Audit branch: `audit/alpha13-road-matching-radar-2026-11-06`

## Executive decision

Do not ship another navigation APK until the road-identity and radar-alert path has been corrected and replay-tested. The observed false radar warning on the TF-1 is consistent with a concrete defect in the no-active-route alert path: it selects by a 250 m radial distance and a 40-degree forward cone, without proving that the camera belongs to the road/carriageway occupied by the motorcycle.

The current route-active path is also insufficiently strict: route camera matching uses a 90 m corridor and a permissive direction rule when OSM direction metadata is absent. A nearest point on a route polyline is not proof of road identity.

## Evidence inspected

### Repository and release state

- Repository: `MotoRicTenerife/NEXT-NAVI-PRO`.
- Alpha 13 source branch: `feat/alpha13-device-heading-fusion-2026-11-06`.
- Android package version in `app/build.gradle`: `0.3.0-alpha13`, versionCode 13.
- The alpha13 branch is 10 commits ahead of `feat/alpha12-arrow-telemetry-2026-11-05`; the diff adds `DeviceHeadingFusion.kt`, corresponding tests and GPS/heading telemetry changes.
- The README explicitly says current route-progress matching is a local probabilistic candidate scorer, not a full road-graph/window HMM, and that parallel carriageways still require field validation.
- The README states automatic legal speed limits come from TomTom through Render; OSM is not an automatic legal-speed-limit fallback.
- Render Blueprint is pinned to `fix/jarvis-render-backend`; this audit must not change Render configuration or secrets.

### Radar false-positive path (confirmed in source)

File: `app/src/main/java/com/nextnavipro/app/MainActivity.kt`, `checkRadarProximityAlert()`.

When there is no active route:
- GPS accuracy must be <=18 m and speed >=6 m/s.
- Every cached camera within 250 m is considered.
- A camera is accepted if its bearing from the phone is within 40 degrees of the GPS course.
- The camera's road membership, carriageway, OSM way, enforcement relation's from/to geometry and lane identity are not checked.
- The camera's own direction metadata is not used in this no-route branch.

This is enough to explain how a radar on a parallel/complanar road can be announced as if it belonged to the TF-1 carriageway.

When a route is active:
- `RouteRadarPlanner.match()` projects each camera to the nearest route segment.
- Default corridor is 90 m.
- Missing or unrecognised camera direction is accepted.
- This corridor can include adjacent carriageways and ramps; direction alone cannot distinguish parallel roads travelling in the same direction.

### OSM relation parsing gap (confirmed in source)

Files: `RadarService.kt`, `RouteRadarService.kt`, `RouteRadarPlanner.kt`.

- Overpass queries retrieve enforcement relations and recursively returned objects.
- `RadarService.parseOverpassResponse()` currently extracts device nodes and relation-level kind/direction/maxspeed.
- It does not preserve the relation's `from` and `to` members as road-association geometry, nor reconstruct the referenced way/road identity.
- OSM's approved `type=enforcement` relation model specifically uses `device`, `from` and, when the device is not on the road or affects directions differently, `to` members. The OSM wiki even documents cameras physically mounted across the opposite carriageway.
- Therefore, a camera point and its direction tag alone are not a sufficiently reliable road association.

## GPS trace supplied for this audit

Files: `Viaggio registrato.csv` and `Viaggio_registrato.gpx`.

CSV:
- 693 samples, from 2026-11-06 11:43:44 UTC to 12:09:29 UTC (about 25 min 45 s).
- Horizontal accuracy median: 2.0 m; 75th percentile: 3.2 m.
- 91 samples report accuracy worse than 20 m.
- Maximum reported accuracy is 6,326.82 m; other extreme values include 300 m and 800 m.
- 87 samples have no bearing.
- Match status: 614 `NO_ACTIVE_ROUTE`, 79 `REJECTED_STALE_OR_ACCURACY`.
- No route segment index was recorded because the trace was captured without an active route.

Interpretation:
- Most samples have useful GNSS precision, but there are severe outliers.
- A 68th-percentile Android horizontal accuracy estimate is an uncertainty radius, not a hard guarantee; do not treat a nominal 2 m fix as exact.
- The supplied trace is useful for GNSS filtering/replay and marker analysis, but cannot retrospectively prove which road segment the app selected because no road-candidate identity, matched way/segment ID, lateral residual, or camera association decision was logged.
- Do not use IP geolocation for navigation, road matching, legal speed limits or radar warnings.

## Speed-limit auto-discovery audit

File: `MainActivity.kt`, `refreshSpeedLimitIfNeeded()`.

Observed behavior:
- A one-point startup lookup is attempted only while stationary (<=1.5 m/s), with accuracy <=35 m, and only once per app state.
- The flag `speedLimitInitialPositionLookupAttempted` is set before the network request. If the startup lookup returns no limit or fails, stationary updates do not automatically repeat this one-point lookup.
- While moving, the app waits for a trace (at least two valid points) and uses `SpeedLimitPolicy.shouldQuery()`: nominal refresh after 250 m or 20 seconds while moving, with a 3-second minimum interval.
- Provider backoff is applied for rate limits/timeouts/network errors.
- The manual “Riprova ricerca” action resets the initial-attempt flag and last-query state. This explains why a manual retry can force a new attempt when the automatic state has stopped trying.
- A late result is checked for spatial/course compatibility before display, and a previous limit is cleared when it no longer applies. Keep these safety properties.

Required change:
- Make lookup lifecycle state explicit: `NOT_STARTED`, `WAITING_FOR_GNSS`, `IN_FLIGHT`, `CONFIRMED`, `NO_DATA`, `BACKOFF`.
- Retry after transient/no-data responses using bounded exponential/backoff and movement/road-change triggers; do not permanently suppress retries after the first startup attempt.
- Keep the current TomTom-only source policy and Render-held key. Never invent statutory limits or use OSM as an automatic fallback.
- Do not show a provisional nearest-road result as confirmed on a multi-carriageway road.
- Reuse route-prefetched TomTom samples only after a high-confidence match to the correct route segment and direction.

## Recommended target architecture

### 1. Road candidate generation

Generate multiple candidate road segments around each accepted GNSS fix, not just the nearest one. Each candidate must carry:
- provider/map road identifier where available;
- road geometry and direction;
- road class and form of way;
- carriageway/parallel-road metadata where available;
- projection point, signed lateral offset and distance;
- heading delta and confidence.

### 2. Sliding-window map matching

Use a sliding-window probabilistic matcher (HMM/Viterbi or equivalent) over consecutive timestamped GNSS fixes:
- emission score from cross-track distance normalized by GNSS uncertainty;
- heading/course agreement weighted by bearing accuracy and speed;
- transition score from network connectivity and physically possible travel distance/time;
- penalties for implausible jumps, U-turns and disconnected road transitions;
- hysteresis before switching road identity;
- explicit `UNKNOWN/AMBIGUOUS` result when two parallel candidates cannot be distinguished.

Do not simply snap every fix to the closest line. The literature demonstrates why nearest-road matching fails under GPS noise and dense/parallel networks.

### 3. Heading and display

Keep display heading separate from road identity:
- GPS course is preferred while moving when valid.
- Device compass is a fallback for low-speed/standstill orientation only after calibration/accuracy checks.
- Landscape/portrait changes display axes, not geographic road bearing.
- A visual smoothing/interpolation layer must never feed a fabricated position back into the road matcher.

### 4. Radar association and alert gating

Extend radar records to preserve:
- OSM camera node ID;
- relation ID and enforcement type;
- device/from/to member references and coordinates;
- associated OSM way ID(s) / geometry when available;
- direction and whether direction is explicit or inferred;
- association confidence and source completeness.

Alert policy:
- Route active: alert only if the camera is associated with the same directed road/corridor as the matched route, not merely within a wide geometric buffer.
- No route: alert only if the current road identity is confidently matched and the camera's associated road/direction matches it.
- If a camera is only a standalone point with no defensible road association on a parallel-road layout, keep it as an optional visual community marker but suppress the voice/proximity alert.
- Unknown direction is not proof of same direction.
- Do not classify a radar as “on the user's lane” if source data only identifies a carriageway, not an individual lane. The correct guarantee is same directed carriageway/road segment, not exact lane unless lane-level data exists.
- Apply a short ahead-distance and along-road progression test; avoid radial-only 250 m tests.
- Keep alert de-duplication, but key it by camera + road identity + direction so the same OSM device cannot be incorrectly carried across parallel roads.

### 5. Speed limits

- Use TomTom Snap to Roads trace matching where the account/backend entitlement and quota permit; request road IDs, projected points, road geometry, speed limits, formOfWay, roadUse, laneInfo, confidence and timestamps as supported.
- TomTom documents `formOfWay=DualCarriageway`, `ParallelRoad`, `MajorSlipRoad` and lane information, which can help reject a wrong road candidate.
- Do not assume the existing Render `/speed-limit` endpoint exposes all these attributes; inspect the backend implementation and live response schema before relying on them.
- Retain the current explicit-limit-only policy; no road-class inference.
- Cache by road-segment identity/direction, not only by GPS coordinate or distance travelled.

## Test matrix required before alpha14

1. TF-1 parallel/complanar road: radar on adjacent road must not alert.
2. TF-1 two carriageways: opposite-direction camera must not alert.
3. Same carriageway, valid OSM enforcement relation: camera must alert once, ahead of the rider.
4. Standalone camera node with no road association: no voice alert in a parallel-road ambiguity zone.
5. Road split/merge, on-ramp/off-ramp and junction: no cross-road carryover.
6. Low GNSS accuracy (20–100 m), stale fix and gross outlier (>100 m): road identity becomes unknown; no radar or speed-limit misassociation.
7. Good GNSS trace with route inactive: matcher converges to the correct road using several fixes.
8. Route active then off-route: route identity is not forced onto the rider; actual road matching takes over.
9. Portrait and landscape: same geographic heading and road identity.
10. Speed-limit startup while stationary, startup while already moving, temporary network outage, 429 Retry-After, empty TomTom response, road change and parallel carriageways: automatic bounded retries, no stale limit.
11. Verify TomTom request volume against Render rate guard; no per-GPS-fix API calls.
12. Regression: traffic overlay, Jarvis, GPX, navigation instructions and manual speed-limit editor remain unchanged.

## Research references

- TomTom Snap to Roads API: https://docs.tomtom.com/snap-to-roads-api/api-explorer
- TomTom synchronous Snap to Roads request/fields: https://docs.tomtom.com/snap-to-roads-api/documentation/snap-to-roads-api/synchronous-snap-to-roads
- TomTom live speed restrictions: https://docs.tomtom.com/intermediate-traffic-service/documentation/endpoint-documentation/tomtom-traffic-speedlimits-intermediate-service-protobuf
- Android Location accuracy, course and bearing accuracy: https://developer.android.com/reference/android/location/Location
- Newson & Krumm, “Hidden Markov Map Matching Through Noise and Sparseness”, ACM GIS 2009: https://doi.org/10.1145/1653771.1653818
- OSM approved enforcement relation model: https://wiki.openstreetmap.org/wiki/Relation:enforcement
- OSM speed camera tagging: https://wiki.openstreetmap.org/wiki/Tag:highway%3Dspeed_camera
- Google Roads API docs used only as an independent technical reference for trace-vs-nearest-road semantics, not as a proposed paid dependency: https://developers.google.com/maps/documentation/roads/snap

## Safety and scope

This is a design/audit document, not a claim that the code has been fixed or that an APK has passed validation. No production branch, Render service, environment variable, API key or deployed backend was changed during this audit. The next implementation must be a small, testable set of commits on this audit branch, followed by GitHub Actions CI and a controlled on-device field test.


## Additional backend audit: Render / TomTom

The active Render-connected backend branch `fix/jarvis-render-backend` was inspected read-only (`backend/jarvis-render/server.js`, `speedLimitPosition.js`).

### Startup / stationary lookup

- `POST /speed-limit-position` calls TomTom Reverse Geocoding with `returnSpeedLimit=true` and `radius=50`.
- The Android client currently sends only latitude and longitude from `findLimitAtPosition()`; although the Render endpoint accepts an optional bearing, it is not sent by the client.
- `parseTomTomPositionSpeedLimit()` reads only `addresses[0]`, extracts its speed limit and road name, and does not require a specific `matchType` or validate that the result is the intended carriageway.
- This is a weak source for startup display on a motorway with parallel carriageways. It must remain provisional and should not be promoted to a confirmed limit without a moving trace/road-identity confirmation.

### Moving trace lookup

- `POST /speed-limit` uses TomTom Snap to Roads and sends each trace point's heading when available.
- The current request asks for only `route.properties{id,speedLimits,address}`.
- It does not request `projectedPoints.routeIndex/snapResult`, route geometry, `formOfWay`, `roadUse`, `laneInfo`, or `confidence`.
- The backend then chooses the final returned route element as the current segment. This is weaker than using the provider's explicit projected-point-to-road-element association.
- The route prefetch endpoint does request projected point mapping, but its returned client payload currently does not preserve all road-identity attributes.
- TomTom's documented allowed `vehicleType` values are PassengerCar, Bus, Truck, Emergency and Taxi; Motorcycle is not listed. Do not send an unsupported motorcycle value. Keep PassengerCar for now and record this as a provider limitation.

### Required backend improvement (separate, guarded PR)

For `/speed-limit` and `/speed-limit-route`, request and parse:
- `projectedPoints.properties.routeIndex` and `snapResult`;
- route element `id`, geometry, `formOfWay`, `roadUse`, `laneInfo.numberOfLanes`, `confidence`;
- explicit maximum speed limits and road address.

Then:
- associate the latest accepted GPS point to its explicit projected road element using `routeIndex`, not merely the last route element in the response;
- return a stable road-segment ID and the match confidence to Android;
- treat OffRoad, MaxDistanceExceeded, missing routeIndex, low confidence or ambiguous adjacent candidates as unknown;
- keep the API key exclusively on Render and retain the existing admission guard, Retry-After handling and request budgets;
- cache by stable segment ID/direction and use bounded retry state in Android.

Do not change the Render-connected branch as part of the Android radar fix. Make the backend work in a separate PR and validate against mocked TomTom responses first. A live provider smoke test must be GET-only unless the request budget and cost of the POST operation are explicitly approved.

## Follow-up implementation block (2026-11-06)

Changes now staged on PR #55, still draft and isolated from alpha13 / Render:

1. **TomTom stationary startup retry** — when the first position lookup produces no usable limit, allow another attempt after 60 seconds, subject to the existing in-flight guard and provider backoff.
2. **No-route radar safety gate** — keep OSM map markers, but suspend proximity voice alerts when no active route provides a defensible road context. This intentionally trades some alerts for fewer false alarms on parallel roads.
3. **Active-route projection** — require route-wide radar prefetch to succeed, then project both rider and camera onto continuous route segments. Compare along-route distance instead of nearest sparse polyline vertices. This removes vertex quantization around bends and rejects positions outside the configured route corridors.
4. **Unit coverage** — add tests for forward distance, a parallel-road camera outside the corridor, and a rider outside the active route corridor.

### Remaining engineering gaps (not claimed as solved)

- OSM enforcement relation `from`/`to` way members are still not retained as road identity in the parsed radar model.
- Directionless OSM devices can still be ambiguous; geometric proximity is not lane-level proof.
- The active-route 22 m camera corridor is a conservative geometric filter, not a substitute for road/way identity.
- TomTom Snap-to-Roads response enrichment with projected point/road attributes remains pending.
- No successful CI run has been confirmed for the current PR head; no APK from these changes is ready for installation.
- Field validation is required on parallel carriageways, slip roads, junctions, bends, and in portrait/landscape.

