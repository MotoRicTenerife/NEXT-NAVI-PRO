# NEXT NAVI PRO

Android navigation prototype for cars and motorcycles, with a high-contrast dashboard and open map data.

## Current online implementation (0.3.0-alpha05)

- Native vector map rendered with MapLibre Android and OpenFreeMap styles. The map is hidden on the main dashboard and opened with the **NAVIGATORE** button.
- Live Android location fixes are checked for age, accuracy and implausible jumps. Position smoothing adapts to reported accuracy, and the map marker is interpolated between accepted fixes.
- If no usable device location arrives after a short wait, approximate public-IP geolocation can provide a rough area for weather and search bias. It is explicitly marked approximate and is never used for navigation, road matching, speed limits or radar alerts.
- GPX library in the app menu: import tracks/routes, save the active route inside the app, reopen saved GPX files and export a route through Android's document picker. Imported GPX tracks are displayed as tracks; turn-by-turn voice instructions are not fabricated when the file contains no route maneuver data.
- Photon destination search and Valhalla route calculation/geometry.
- Typed Valhalla maneuver icons for left/right/fork/roundabout guidance. Text alone is not allowed to turn a typed fork into a roundabout.
- Road matching against nearby drivable OpenStreetMap ways, returning the matched road name (`name`), route reference (`ref`) and explicit numeric speed limit (`maxspeed`) when available.
- Speed-limit display remains unknown when a confident road match or explicit numeric value is unavailable; the app does not invent statutory defaults.
- Configurable speedometer border bands (green/yellow/red) and a motorway overspeed alert. A valid mapped limit takes priority; when absent, the user-configured alarm threshold is only an alarm setting and is **not** shown as a legal speed limit.
- Persistent positive/negative display themes, automatic portrait/landscape layout and saved preferences.
- Community speed-camera markers cached locally and refreshed with rate limiting.
- Unit tests for Valhalla polyline decoding, conservative speed-limit parsing and maneuver icon precedence.

## Known limitations and safety notes

- Routing currently uses Valhalla's public `auto` costing profile for both display modes; a dedicated motorcycle profile is not configured.
- Route progress is still based on proximity to the route geometry, not a full probabilistic map-matching engine. Junctions, parallel carriageways and dense road networks need real-device field testing.
- Road names, references and speed limits currently come from nearby OpenStreetMap ways via the public Overpass API. Coverage and tagging can be incomplete. The official Cabildo de Tenerife road-network dataset has been identified as a valuable independent geometry/reference source, but has **not yet been integrated**.
- Numeric legal limits are shown only when an explicit OSM `maxspeed` value can be associated with the matched road. Missing/conditional values remain unknown; the app does not infer a legal limit from road class.
- Speed-camera pins are visual only; there are no audio radar alerts yet because road/direction matching must be validated first.
- Online services require connectivity. This is not yet a complete offline navigator.
- IP geolocation is city/area-level at best and can point to a carrier gateway or VPN exit; it is not a substitute for GNSS and must never drive turn-by-turn guidance.
- Imported GPX is currently track-following by geometry only. Reliable turn-by-turn guidance for GPX requires a separate road-route recalculation and validation; the app states this limitation instead of inventing turns.
- Public demo endpoints (Photon, Valhalla and Overpass) are best-effort services and may impose rate limits. A successful CI build does not replace GPS, route and alert validation on the actual phone and on-road network.

## Open data and services

- **MapLibre Native:** https://maplibre.org/ — BSD-2-Clause licensed native vector map renderer.
- **OpenFreeMap:** https://openfreemap.org/ — map styles, including `https://tiles.openfreemap.org/styles/liberty`.
- **Photon:** https://photon.komoot.io/ — OpenStreetMap-based geocoding; subject to fair-use policies.
- **Valhalla demo:** https://valhalla.openstreetmap.de/ — public routing demo; the app requests Italian instructions.
- **Overpass API:** https://overpass-api.de/ — public OpenStreetMap query service for road matching, explicit speed-limit values and community camera points.
- **OpenStreetMap tagging for speed limits:** https://wiki.openstreetmap.org/wiki/Key:maxspeed — numeric and contextual `maxspeed` conventions.
- **Cabildo de Tenerife road network:** https://datos.tenerife.es/datos/conjuntos-de-datos/vias-competencia-del-cabildo-insular-de-tenerife — official road-axis GeoJSON/GeoPackage/CSV distributions, CC BY. This source is under review for a local or cached cross-check of road identifiers and names.
- **OpenStreetMap:** https://www.openstreetmap.org/ — community-maintained data. Keep attribution visible and review ODbL obligations before redistributing derived datasets.

## Next implementation stages

1. Complete CI verification of the current changes and inspect the produced debug APK.
2. Test GPS smoothness, route instructions, road names, speed limits, speedometer bands and motorway alert behavior on the real Samsung device.
3. Compare OSM road geometry/references with the official Cabildo dataset and investigate local mismatches before applying any correction.
4. Improve road matching at forks, roundabouts and parallel carriageways; validate speed-limit freshness and prevent stale values from persisting after road changes.
5. Add direction-aware radar alerts only after road matching is dependable.
6. Prepare regional offline vector maps, road graphs, search and routing after online navigation is stable.

## Build

GitHub Actions runs `gradle assembleDebug` and `gradle testDebugUnitTest`, then uploads a debug APK artifact. The APK is a test build and has not been validated for safe standalone navigation.
