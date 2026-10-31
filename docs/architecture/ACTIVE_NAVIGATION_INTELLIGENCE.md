# NEXT NAVI PRO — Active Navigation Intelligence Architecture

Status: target architecture and implementation contract. This document does not claim that every component is already implemented.

## Product objective
Build a motorcycle-first, globally deployable navigator that continuously observes road context, estimates what may happen ahead, compares valid route alternatives, and gives the rider a timely, explainable choice. Core navigation must continue locally when network services are unavailable.

This is not an LLM-driven navigator. A language model may explain a decision through Jarvis, but it must never generate route geometry, legal speed limits, maneuver topology, or safety-critical actions.

## Five cooperating planes

### 1. World and vehicle perception
- GNSS ingestion with fix age, accuracy, elapsed-realtime, speed, bearing, and sensor health.
- Preserve accepted raw measurements; smooth the displayed marker/camera separately.
- Map-match using distance, heading, plausible motion, route continuity, and road topology.
- Preserve ambiguity. Parallel carriageways and junctions must not silently become false certainty.

### 2. Road and route model
- Global baseline routing: Valhalla + OpenStreetMap, subject to uptime and regional map-data completeness.
- Request multiple alternatives when supported; keep stable route identity/version.
- Motorcycle/scooter access is a hard constraint. Validate the Tricity 300 profile against local legal access. Do not blindly choose Valhalla motor_scooter defaults: its documented default top speed is 45 km/h. Valhalla motorcycle costing is marked beta and requires field validation.
- TomTom Orbis Routing v2 is currently documented as Public Preview and its documented travelMode parameter allows only car. It is therefore NOT a motorcycle alternative provider for the Tricity 300. It may be evaluated separately for a future car mode only after entitlement, coverage, pricing and terms are verified. Motorcycle mode remains on Valhalla plus independently matched traffic evidence.

### 3. Traffic and hazard perception
- Map overlay: TomTom Orbis v2 vector flow tiles; draw road geometries and color by relative_speed. Missing tiles mean unknown, never green. The Render proxy is feature-flagged off by default until provider quota and client abuse controls are verified.
- Structured incident feed: TomTom Orbis v2 Incident Details for accidents, jams, closures, roadworks, weather hazards and future events.
- Structured flow assessment: investigate route-corridor sampling via supported Flow Segment Data or server-side vector-tile decoding. Legacy Flow Segment Data exposes current/free-flow speed, travel time, confidence, road closure and geometry; verify lifecycle and commercial terms before depending on it.
- Other national, regional and community sources are optional adapters with per-source freshness, provenance, coverage, rate limits and license. Never assume a country-specific feed covers another country.
- Normalize each observation: source, event ID, road/segment, direction, geometry, observedAt, validUntil, confidence, severity, closure, current/free-flow speed, delay and attribution metadata.

### 4. Decision and prediction
- Deterministic safety gate first: reject inaccessible roads, confirmed closures, stale or directionally mismatched evidence.
- Compare candidate routes by traffic-adjusted ETA, expected delay, distance, motorcycle suitability, maneuver complexity, evidence freshness and uncertainty.
- Calculate hazard chainage along route geometry, distance-to-hazard, time-to-hazard and expected delay. Straight-line distance alone is insufficient.
- Use hysteresis/materiality thresholds to prevent reroute oscillation.
- Offer a decision only when benefit is meaningful and evidence sufficiently fresh/confident. Show current route, alternative, expected savings/delay, hazard distance and uncertainty.
- Rider actions: continue, inspect alternative, accept alternative. No silent route change for an advisory. An authoritative closure must be explained and a safe option offered.
- Keep monitoring after the choice; cancel/update an offer if traffic clears, worsens or becomes stale.

### 5. Human interaction and safety
- One TTS arbiter: safety > imminent maneuver > route hazard > preparation > radar > Jarvis conversation.
- Speech request carries priority, route/maneuver identity, creation time, expiry, interruption policy and completion result. Cancel stale speech; do not scatter QUEUE_ADD/QUEUE_FLUSH calls through MainActivity.
- Voice responses are short and unambiguous. While moving, avoid prolonged visual dialogs. Continue and Evaluate alternative are explicit actions.
- Missing traffic data must never be described as a clear road.

## Continuous route-monitoring loop
1. At route start, request a baseline route and bounded alternatives.
2. Sample active and candidate routes at road-aligned intervals; request incidents for corridor bounding boxes, not the whole world.
3. Normalize/cache provider responses with source identity, observed time and TTL.
4. Match traffic observations to route edges using geometry, direction and, where available, OpenLR/provider road IDs.
5. Estimate route delay and hazard chainage. Discount low-confidence or stale measurements.
6. Estimate time-to-hazard from route distance and robust current speed, not one noisy GNSS sample.
7. Re-evaluate on meaningful events or bounded cadence (initial target 30–60 s and/or 400–800 m, adapted to speed, freshness and quota).
8. Offer an advisory only when policy gates pass. Suppress duplicates by stable event/route identity and cooldown.
9. Re-check before accepting an alternative; preserve the current route until rider confirmation.
10. Log route version, traffic snapshot IDs/timestamps, confidence, estimates, alternatives, decision reason, prompt and rider response for replay.

## Predictive ML: a real model, not a renamed rules engine

### Initial baseline
The first safe decision layer is deterministic and explainable. It is not advertised as trained ML. It establishes data contracts, labels, safety boundaries and a baseline that future models must beat.

### Dataset and training
- Collect opt-in, privacy-minimized GPX/CSV travel observations aligned with traffic snapshots.
- Segment features may include road class, time/day, weather where available, live/free-flow speed, traffic confidence, incident class/severity, gradient/curvature, route progress and actual segment travel time.
- Split train/validation/test by complete trip, rider, geographic region and time period to prevent leakage.
- Exclude or anonymize home/work endpoints. Do not upload precise traces without explicit consent.

### Candidate model
- First candidate: compact gradient-boosted decision tree for segment travel-time distribution and congestion persistence, trained offline.
- Evaluate TFLite/ONNX or a compact tree evaluator against APK size, cold-start latency, CPU, RAM, battery and device compatibility.
- Model output is a calibrated estimate plus uncertainty only. Deterministic routing and safety constraints remain authoritative.
- Ship ML only if it beats the deterministic baseline on held-out trips and passes calibration, drift, latency and battery gates. Otherwise keep the baseline.

## Global availability and distribution
- App distribution is global; routing coverage depends on global road-graph and map-data quality.
- Live traffic is provider-coverage-dependent. No provider supplies complete live traffic on every road worldwide.
- Expose coverage/freshness and degrade gracefully to local/offline routing.
- Public distribution requires account/app identity or another abuse-control mechanism before exposing a shared Render proxy. Per-IP rate limits alone are not authentication and cannot protect a shared quota at scale.
- Provider keys stay server-side on Render. Do not introduce Cloudflare or HERE.

## Implementation sequence

### Phase A — trustworthy baselines
- Keep GPS/map matching and maneuver lifecycle under replay tests.
- Centralize TTS before adding more speech features.
- Audit route-provider response contracts and alternative support.
- Verify TomTom Traffic entitlements and perform bounded live smoke tests.

### Phase B — traffic data plane
- Add Incident Details proxy with bounded bbox, rate limits, cache, TTL and source metadata; keep it disabled until user authentication/app abuse controls are ready. The current per-IP guard is a temporary quota guard, not production authentication.
- Add route-corridor flow sampling and route/segment matching.
- Add typed Kotlin traffic observations with freshness/confidence.
- Test stale, contradictory, directional, missing and closure observations.

### Phase C — rider decision engine
- Generate motorcycle alternatives with Valhalla alternates (the current request is not time-dependent; Valhalla documents that alternates are unavailable for time-dependent requests). TomTom Orbis Routing v2 is car-only in its current documentation and must not be used to route the motorcycle.
- Score by traffic-adjusted ETA, closure constraints, motorcycle suitability, uncertainty and reroute cost.
- Implement advisory lifecycle: detected → verified → offered → accepted/rejected/expired → resolved.
- Integrate with the single TTS arbiter and safe rider confirmation UI.

### Phase D — predictive learning
- Build opt-in telemetry dataset and replay harness.
- Train/evaluate against deterministic baseline.
- Integrate local inference only after all gates pass.

### Phase E — validation and release
- Unit, backend mock, route replay, map-matching and traffic-decision tests.
- Coverage tests across Europe and representative non-European regions.
- Device tests on Galaxy S22 Ultra / Android 16, Cardo Freecom 4x, long-ride battery/thermal checks.
- Road tests: parallel roads, junctions, roundabouts, closures, traffic clearing, false alerts, route changes and no-network behavior.
- No merge, Render deployment, APK handoff or release without explicit approval. Green CI is not road validation.

## Traffic advisory acceptance criteria
- Hazard is matched to active route in correct direction.
- Observation is fresh; source and confidence are available.
- Distance is measured along route geometry; ETA includes bounded uncertainty.
- At least one valid alternative is computed, or the UI clearly labels an information-only warning.
- Estimated benefit exceeds a materiality threshold and remains stable through confirmation.
- Duplicate warnings are suppressed; stale offers expire.
- Rider can continue or inspect/accept an alternative.
- Advisory never silently changes the route.
- Decisions can be reconstructed from telemetry and replayed in tests.

## External technical references
- TomTom Orbis Traffic Flow v2: https://docs.tomtom.com/traffic-api/documentation/tomtom-orbis-maps/v2/traffic-flow/vector-flow-tiles
- TomTom Orbis Incident Details v2: https://docs.tomtom.com/traffic-api/documentation/tomtom-orbis-maps/v2/traffic-incidents/incident-details
- TomTom legacy Flow Segment Data v4 (lifecycle/terms must be verified): https://docs.tomtom.com/traffic-api/documentation/tomtom-maps/v1/traffic-flow/flow-segment-data
- Valhalla route API, alternatives and motorcycle/scooter costing: https://valhalla.github.io/valhalla/api/route/api-reference/
- TomTom Orbis Routing v2 (Public Preview; documented travelMode currently car-only): https://docs.tomtom.com/routing-api/documentation/tomtom-orbis-maps/v2/calculate-route
