# NEXT NAVI PRO

Android navigation prototype focused on real GPS, high-contrast motorcycle/car display and open map data.

## Current online implementation (0.3.0-alpha04)

- Native vector map using MapLibre Android and OpenFreeMap Liberty style.
- Live position from Android location providers; stale or low-accuracy fixes are not treated as valid, and no placeholder coordinate is shown as the user's position.
- Photon geocoding for destination search.
- Valhalla public demo routing for route calculation and route geometry overlay.
- Persistent positive/negative UI theme.
- GPS-follow camera, destination selection, route distance/time summary, and route clearing.
- OpenStreetMap speed-camera overlay around the current GPS position. The app caches the returned community data locally and rate-limits refresh attempts.
- Automatic display layout for portrait/landscape and Moto/Auto UI modes.
- Unit tests for Valhalla polyline6 route geometry decoding.

## Known limitations

- Routing currently uses Valhalla's `auto` costing profile for both display modes; it is not yet a dedicated motorcycle profile.
- Route preview and geometry are implemented, but turn-by-turn progress, deviation detection, and automatic rerouting are still pending.
- Radar pins are visual map markers only. There are no audio radar alerts yet: we must first match the user's actual road and direction to avoid warnings for cameras on parallel or opposite carriageways.
- Speed-limit data is not yet displayed. We will only show numeric limits when a road-matched, traceable source provides a valid value.
- Online services require data connectivity. This version is not a complete offline navigator.
- Public demo endpoints (Photon, Valhalla and Overpass) are best-effort services, may impose rate limits, and are not production guarantees.

## Open data and services

- **MapLibre Native:** https://maplibre.org/ — BSD-2-Clause licensed native vector map renderer.
- **OpenFreeMap:** https://openfreemap.org/ — map style URL `https://tiles.openfreemap.org/styles/liberty`.
- **Photon:** https://photon.komoot.io/ — OpenStreetMap-based geocoding; public service use is subject to fair-use policies.
- **Valhalla demo:** https://valhalla.openstreetmap.de/ — public routing demo. The app sends an identifying `X-Client-Id` header.
- **Overpass API:** https://overpass-api.de/ — public OpenStreetMap query service used for community speed-camera points. Results can be incomplete or stale.
- **OpenStreetMap:** https://www.openstreetmap.org/ — community-maintained data. Keep attribution visible and review ODbL obligations before redistributing derived datasets.

## Next implementation stages

1. Test map rendering, GPS following, destination search and route calculation on the real Samsung device.
2. Add route progress, reliable road matching, deviation detection and automatic rerouting.
3. Integrate additional speed-camera datasets (including verified Spanish public data and suitable licensed feeds), deduplicate them and preserve source/update timestamps.
4. Add speed limits only after reliable road matching and explicit data-quality handling.
5. Add alerts after route/direction matching can avoid false positives.
6. Prepare downloadable regional vector maps, local road graphs, offline search and offline routing after online navigation is stable.

## Build

GitHub Actions runs `gradle assembleDebug` and `gradle testDebugUnitTest`, then uploads the debug APK artifact.
