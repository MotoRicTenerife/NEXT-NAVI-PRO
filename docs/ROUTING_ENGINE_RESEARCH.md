# NEXT NAVI PRO — Routing engine research

Updated: 2026-10-14

This note separates facts about public projects from assumptions about their private implementations. It is not a claim that field validation has been completed.

## Findings

### Valhalla — current online routing and trace matching
- The public API exposes distinct `auto`, `motor_scooter`, and beta `motorcycle` costing models.
- `motor_scooter` defaults to a low top speed and can avoid higher-class roads; this is a poor default for a 300 cc scooter used on motorways unless carefully configured.
- The motorcycle costing model is intended for motorcycle touring and exposes a `use_trails` preference. NEXT NAVI PRO now requests `motorcycle` for the selected motorcycle profile and sets `use_trails=0`.
- Valhalla `trace_route` can snap a sequence of GPS/GPX points to the road network and return a route response with maneuvers. NEXT NAVI PRO now offers this online for imported GPX tracks; on failure, it retains the original track and does not invent turn instructions.
- The public demo endpoint is a best-effort dependency, not a service-level guarantee. Production use needs a stable hosted service or a self-hosted Valhalla instance, rate limits, monitoring, and explicit failure handling.

References:
- https://github.com/valhalla/valhalla/blob/master/docs/docs/api/route/api-reference.md
- https://github.com/valhalla/valhalla-docs/blob/master/map-matching/api-reference.md

### GraphHopper — offline/hosted routing and trace matching
- GraphHopper provides an Apache-2.0 routing engine and a map-matching module that snaps GPX traces to road geometry.
- It supports vehicle profiles including car and motorcycle in its documented map-matching tooling.
- Embedding it is not a one-line Android dependency: the graph must be imported from OSM data, storage and memory are material, and map-data updates need a deliberate regional distribution/update system. The archived map-matching repository is useful as a design reference, but should not be assumed to be a maintained turnkey Android component.

References:
- https://github.com/graphhopper/graphhopper
- https://github.com/graphhopper/map-matching

### OsmAnd — proven offline navigation architecture
- OsmAnd demonstrates mature on-device map data, offline routing, voice guidance and road-specific navigation.
- Its core source is mainly GPLv3; artwork/UI assets have separate CC-BY-NC-ND terms and some resources are proprietary. Copying code into NEXT NAVI PRO would impose license and distribution obligations and cannot be treated as a casual code snippet import.
- The current project deliberately does not embed OsmAnd. Reusing ideas and documented behavior is different from copying code or protected artwork.

References:
- https://github.com/osmandapp/Osmand
- https://github.com/osmandapp/Osmand/blob/master/LICENSE

### Organic Maps — offline map and routing engine
- Organic Maps is Apache-2.0 licensed at the source-code level and has mature offline maps, search, route guidance and GPX import/export.
- It is largely native C++ and uses its own regional map-data format; binary map data and attribution have separate requirements. A safe integration would require a deliberate native build/bridge and map-data pipeline, not just adding one Android dependency.
- It is a strong candidate for a later technical spike if embedded offline navigation becomes the priority.

References:
- https://github.com/organicmaps/organicmaps
- https://github.com/organicmaps/organicmaps/blob/master/LICENSE

### BRouter — offline route engine
- BRouter is MIT-licensed and can run on Android with configurable profiles and regional data.
- Its strongest use cases are bicycle and energy-aware routing; it is not currently the best single engine for a combined car + 300 cc scooter navigation product. It may be useful as a reference or optional specialized profile, not as a replacement for all routing.

Reference:
- https://github.com/abrensch/brouter

### MapLibre — offline map tiles, not offline routing
- MapLibre Native supports offline map regions for style resources and tiles. This can make the map itself available without connectivity.
- Offline tile packs do **not** supply a road graph, routing engine, speed-limit database or turn-by-turn instructions. Those are separate subsystems and must not be described as full offline navigation.

Reference:
- https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/

### Google Maps and WhatsApp
- Their production app source code and private server-side implementations are not publicly available for inspection in the way open-source repositories are. Their visible behavior can be evaluated, but claiming to know their internal map-matching or message-delivery implementation from the app itself would be speculation.
- NEXT NAVI PRO should compare observable behaviors and use public engineering documentation rather than reverse-engineer or copy proprietary internals.

## Implementation direction

1. Keep route geometry, maneuver type, legal speed limit, and confidence as separate typed data. Never infer a legal speed limit from the road class.
2. Use Valhalla's vehicle-specific costing online; use trace matching for imported GPX and keep the unmodified GPX if matching fails.
3. Improve live route progress locally using accuracy, heading, speed, temporal continuity and physically plausible progress; verify with recorded GPS traces before tuning thresholds.
4. Treat map matching and offline routing as separate projects. A local probabilistic matcher needs candidate road segments and a transition model; a remote `trace_route` call for a whole GPX is not a substitute for per-fix live matching.
5. For true offline turn-by-turn navigation, choose and prototype one engine with its regional road graph/data pipeline. GraphHopper and Organic Maps remain candidates; the current app still has no offline routing graph. Decide only after an Android proof of concept measures APK size, RAM, route latency, data update size, licensing and car/motorcycle route quality.
6. Do not declare production readiness until automated tests pass, the APK installs on target devices, and repeatable real-road traces validate position, turn timing, rerouting, limits and connectivity loss.


## Additional findings from Android documentation and navigation-community reports

### Position smoothness is not the same as position accuracy
- Android's Fused Location Provider combines available device sources and treats requested intervals as best-effort hints. A 250 ms request does not guarantee a 4 Hz GNSS fix; many devices derive location around 1 Hz, and hardware, permissions, power policy and environment matter.
- Keep raw fixes for diagnostics and use smoothed/interpolated positions only for presentation. Never feed a visually predicted marker into the speed-limit lookup or route-progress engine as if it were a measured fix.
- Before changing request cadence or filtering constants, compare raw timestamp gaps, accuracy, speed/bearing and matched-road error in the CSV traces. Battery drain and false precision are both regressions.
- Urban multipath can place the apparent location on the wrong side of a road. Smoothing alone cannot resolve this; candidate road geometry, heading, temporal transition cost, road access and a confidence threshold are needed. Google has publicly described using 3D building models and raw GNSS measurements at scale for difficult urban cases; this is not reproducible merely by copying a filter formula.

References:
- https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest
- https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
- https://android-developers.googleblog.com/2020/12/improving-urban-gps-accuracy-for-your.html
- https://github.com/osmandapp/OsmAnd/issues/5681

### Practical lessons from OsmAnd user reports
- GPX files can represent a recorded track, a planned route, or a list of waypoints; treating them as interchangeable can create tiny, misleading instructions. OsmAnd community discussions describe attaching a GPX to roads as a way to obtain cleaner road-following guidance, while warning that it is not perfect.
- NEXT NAVI PRO should preserve the original GPX, label road matching as a derived route, and never silently replace the user's intended geometry. If the match fails or diverges materially, show a clear fallback instead of inventing turns.

Reference:
- https://groups.google.com/g/osmand/c/Cw_J3l9JYnw

### Changes made during this review
- Portrait dashboard layout is reordered to show the tachometer, current clock, km/h label, then arrival time / remaining distance. The portrait vertical weights are adjusted to give the complete stack room before the route button.
- Off-route recalculation now preserves the selected car/motorcycle costing profile instead of silently forcing car costing.
- GPX trace matching no longer sends a made-up fixed `gps_accuracy=10` value when the imported file does not provide accuracy metadata. A regression test checks this.
- These code changes require CI verification and then a visual check on the target Samsung. They do not constitute road validation.
