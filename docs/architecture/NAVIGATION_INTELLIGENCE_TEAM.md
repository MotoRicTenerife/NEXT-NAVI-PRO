# NEXT NAVI PRO — Navigation Intelligence Team

Status: initial team charter and architecture baseline. This configures specialist roles; it does not claim that autonomous agents are already running continuously.

## Objective
Deliver a dependable motorcycle-first navigator combining GNSS, road-network geometry, route instructions, speed-limit evidence, vehicle motion, and rider interaction. Car mode remains a supported profile.

## Two cooperating systems

### Development team
Specialist coding agents inspect, design, implement, review, and test bounded changes. They collaborate through written contracts and pull requests. The Navigation Architect owns integration.

### On-device Navigation Intelligence Engine
The runtime is deterministic, local-first, observable, and fail-safe. Predictive ML may estimate maneuvers or detect anomalies, but cannot invent road geometry, legal speed limits, or authorize unsafe instructions. No cloud/LLM round trip is required for immediate guidance.

## Runtime architecture target

1. **GNSS ingestion and quality gate**
   - Validate fix age, horizontal accuracy, monotonic elapsed realtime, plausible displacement/acceleration, and bearing validity.
   - Preserve accepted raw fixes for diagnostics and recording.
   - Maintain a separate filtered presentation position.

2. **Road and route map matcher**
   - Score candidates using distance, heading, speed, topology, and temporal continuity.
   - Expose confidence and ambiguity, not just a segment index.
   - Use hysteresis and bounded recovery at junctions, roundabouts, parallel roads, and U-turns.
   - Never advance route progress solely because a noisy fix is spatially close.

3. **Route and maneuver state machine**
   - Explicit states: approaching, prepare, imminent, executing, completed, missed/recovery.
   - Maneuver identity and lifecycle remain stable across GPS updates.
   - Completion is grounded in route topology and observed movement, not merely passing a point.
   - Roundabout entry, circulation, exit selection, and exit completion are separate states.

4. **Speed intelligence**
   - Keep posted legal limit, user tachometer correction, measured GNSS speed, and user alarm threshold separate.
   - Store source, timestamp, direction, road association, confidence, and validity for every limit.
   - TomTom is preferred where configured; missing/ambiguous values remain unknown.
   - Never infer legal limits from road class, traffic averages, curve radius, or model output.

5. **Voice scheduler**
   - One speech arbiter owns navigation TTS requests.
   - Requests carry maneuver ID, stage, priority, creation time, expiry, and interruption policy.
   - Drop stale instructions; coalesce repeated stages; preserve urgent alerts; do not use QUEUE_FLUSH as a generic update mechanism.
   - Drain speech state on completion, error, stop, and engine shutdown.
   - Navigation prompts and Jarvis speech require explicit arbitration.

6. **Presentation and telemetry**
   - UI consumes navigation snapshots; it does not own navigation decisions.
   - Smooth marker/camera independently of route progress.
   - Log enough timestamps and state transitions to reconstruct defects without rider audio.
   - GPX/CSV pair consistency is a release invariant.

7. **Predictive assistance**
   - Start with deterministic rules and calibrated thresholds.
   - Add lightweight on-device models only after obtaining labelled, representative traces.
   - Every prediction includes confidence and a deterministic fallback.
   - No continuous cloud inference while riding.

## Target navigation decision record
Each decision should be traceable to fix timestamp and elapsed-realtime timestamp; coordinates, accuracy, speed, bearing and validity; matched road/route segment, candidate score and confidence; maneuver ID/type/state/distance/time-to-maneuver; speed-limit value or unknown with source/age/direction/confidence; and selected prompt, priority, expiry and reason.

## Specialist roles
| Role | Owns | Must coordinate with |
|---|---|---|
| Navigation Architect | contracts, sequencing, integration, ADRs | all roles |
| GNSS & Geodesy | fix quality, filters, heading, geometry | Map Matching, QA |
| Map Matching | candidate scoring, topology, confidence | Routing, GNSS |
| Routing & Maneuvers | route progress, roundabouts, exits, reroute | Map Matching, Voice |
| Speed Intelligence | TomTom limits, correction, alerts | GNSS, QA |
| Voice & Interaction | TTS queue, timing, Jarvis arbitration | Routing, Android |
| Android Runtime | lifecycle, threading, battery, UI boundaries | all runtime roles |
| Data & Telemetry | GPX/CSV, replay, diagnostics | GNSS, QA |
| Predictive AI | model evaluation and confidence | Architect, Safety |
| QA & Safety | test strategy, regression gates, evidence | all roles |

## Collaboration workflow
1. Architect creates a scoped task and identifies the source-of-truth branch.
2. Specialists perform read-only analysis and submit evidence-backed findings.
3. Architect resolves disagreements and publishes an interface/data contract.
4. One implementation owner edits a shared file at a time.
5. An independent reviewer checks correctness and edge cases.
6. QA runs unit/integration/CI tests and reports gaps.
7. Changes remain in draft PRs until CI is green and device validation is planned.
8. Merge/deploy/release require explicit user approval.

## Repository baseline observed
- Android Kotlin app, application ID com.nextnavipro.app, min SDK 26, target SDK 35, Java/Kotlin 17.
- MapLibre Android 11.11.0; Google Play Services Location 21.3.0.
- Routing/geocoding currently use Valhalla and Photon; README documents OSM/Overpass road attributes, with TomTom preferred for speed-limit service when configured.
- MainActivity.kt still contains substantial orchestration: GPS updates, route guidance, speed-limit display, and TTS. Refactor incrementally behind tested seams.
- README describes current route matching as an accuracy-weighted local candidate scorer, not a full-window HMM.
- Branch fix/trip-archive-and-gps-heading had successful Android CI run 932 at commit 8fe81a675aadb61d7b6ddd361c98df9411ee5ac4.

## PR dependency warning
At inspection, PR #14 was draft, based on PR #13's branch; PR #13 was draft and based on the GPS-marker branch. PR #9 (voice queue) was a separate draft branch based on fix/gps-trace-monotonic-filter, not the direct base chain of PR #14. Do not assume the voice-queue fix is included in PR #14. Reconcile branch ancestry and cherry-pick/rebase only through a reviewed integration plan.

## Phased delivery
- Phase 0: contracts, replay harness, diagnostics, and PR dependency clarity.
- Phase 1: fix voice timing and maneuver lifecycle using recorded traces.
- Phase 2: improve map matching and route progress at junctions/roundabouts.
- Phase 3: unify speed-limit provenance, freshness, direction, and alert policy.
- Phase 4: separate runtime services from MainActivity and introduce immutable navigation snapshots.
- Phase 5: evaluate predictive on-device models against deterministic baselines.
- Phase 6: Samsung device road validation; only then consider release readiness.

## Safety boundary
This is a navigation aid, not a certified vehicle control or advanced rider-assistance system. Never recommend a curve-entry speed as safe based only on average traffic speed or map curvature.
