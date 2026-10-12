# NEXT NAVI PRO

Android navigation prototype focused on real GPS, high-contrast motorcycle/car display and open map data.

## Current online implementation (0.3.0-alpha03)

- Native vector map using MapLibre Android and the OpenFreeMap Liberty style.
- Live position from Android location providers; stale or low-accuracy fixes are not treated as valid.
- Photon geocoding for destination search.
- Valhalla public demo routing for route preview and route geometry overlay.
- Persistent positive/negative UI theme.
- Automatic route line drawing and GPS-follow camera.
- Moto/Auto display mode. Routing currently uses Valhalla's `auto` costing profile for both modes; this is not yet motorcycle-specific routing.
- Online services require data connectivity. This version is not an offline navigator yet.

## Public service and attribution notes

- OpenFreeMap: https://openfreemap.org/ — vector map style URL `https://tiles.openfreemap.org/styles/liberty`.
- MapLibre Native: https://maplibre.org/ — BSD-2-Clause licensed map renderer.
- Photon: https://photon.komoot.io/ — OpenStreetMap-based geocoding; public service use is subject to fair-use policies.
- Valhalla demo: https://valhalla.openstreetmap.de/ — public demo service, best-effort and rate-limited; the app sends an identifying `X-Client-Id` header. Do not treat this public demo as guaranteed production infrastructure.
- OpenStreetMap-derived data requires appropriate attribution and may carry ODbL obligations. Keep attribution visible and review each dataset's licence before redistribution.

## Next implementation stages

1. Verify vector map, GPS following and routing on the real device.
2. Add route progress, rerouting and explicit route error handling.
3. Add a versioned speed-camera dataset with source, timestamp, deduplication and local storage.
4. Add verified speed-limit/road geometry data.
5. Implement regional offline maps and offline routing only after online navigation is stable.

## Build

GitHub Actions runs `gradle assembleDebug` and `gradle testDebugUnitTest`, then uploads the debug APK artifact.
