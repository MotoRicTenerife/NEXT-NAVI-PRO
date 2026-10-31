# NEXT NAVI PRO — Engineering audit and field-test gate

Audit date: 2026-10-31
Audit baseline: `fix/trip-archive-and-gps-heading` at `8fe81a675aadb61d7b6ddd361c98df9411ee5ac4`
Status: static source review; no runtime changes in this audit commit.

## Executive decision

The current codebase is a credible test candidate, but it is **not yet justified as a near-final navigation release**. Existing Android CI success proves compilation and configured automated checks on the respective PR commits; it does not prove route correctness, roundabout exit selection, TTS behavior under contention, or real-world GPS recovery.

Keep the navigation safety core deterministic and local-first. Predictive AI may rank hypotheses or detect anomalies, but must not invent legal limits, road topology, or authoritative maneuvers.

## Repository / PR state observed

- PR #9, `fix/navigation-voice-queue`, draft, based on `fix/gps-trace-monotonic-filter`; head `69925320f12e4c2aa99cece8012737c398e465c5`; Android CI run 40394623607 successful.
- PR #13, `fix/gpx-csv-trace-consistency`, draft, based on `fix/smooth-gps-marker-camera`; head `c7cc79f11cb619428a7e2c487462fd4be429026d`; Android CI run 41050665849 successful.
- PR #14, `fix/trip-archive-and-gps-heading`, draft, based on PR #13; head `8fe81a675aadb61d7b6ddd361c98df9411ee5ac4`; Android CI run 41190887162 successful.
- PR #15, specialist role documentation, draft, based on PR #14; head `70aacc54e15bd7e7ae51141b849136f8902e085b`; Android CI run 41199749836 successful.
- None of these PRs was merged at audit time. PR #9 is not included in PR #14's ancestry. Do not merge these branches blindly: they overlap in `MainActivity.kt` and have different bases.

## Findings and risk register

### P0 — One speech arbiter is not yet implemented

The PR #9 voice patch serializes navigation maneuver utterances and Jarvis speech, but it does not own all TTS sources. On the PR #14 baseline, independent `speak()` calls remain for Jarvis preview/response, voice test, navigation start, overspeed, radar, arrival, reroute, and turn guidance. Radar uses `QUEUE_ADD`; several other sources use `QUEUE_FLUSH`.

Risk: a routine event can cut off an instruction or an urgent warning; queued radar audio can become stale. The proposed PR #9 queue also needs adversarial review for callback races, interrupted utterance IDs, expiry of pending instructions, TTS engine errors, lifecycle shutdown, and priority inversion. It is not safe to label the issue solved by the current patch.

Acceptance:
- One scheduler owns every runtime utterance.
- Requests carry source, priority, creation time, expiry, route/maneuver identity and interruption policy.
- Urgent radar/overspeed warnings outrank routine status; explicit rider interaction has a defined interrupt rule.
- Stale maneuver prompts are dropped; same-maneuver stages are coalesced.
- Completion, error, stop, engine shutdown and activity recreation cannot revive stale speech.
- Deterministic tests cover simultaneous events and callback reordering.

### P0 — Maneuver progress needs a lifecycle, not only nearest-point selection

Current `updateTurnGuidance` selects the first maneuver whose `pointIndex` is more than two route points beyond the nearest route match, otherwise it falls back to the last maneuver. Stage prompts are distance thresholds based on current speed. This does not itself model approach, prepare, imminent, executing, completed, missed/recovery. Roundabouts are recognized for wording/icon, but there is no explicit entry/circulation/selected-exit/exit-completed state in this path.

Risk: premature or late prompts, wrong exit, repeated last instruction, missed maneuver after a route-index jump, and failure to recover after ambiguous map matching.

Acceptance:
- Stable maneuver ID and typed maneuver semantics.
- Explicit state transitions with hysteresis and monotonic but recoverable progress.
- Roundabout tests for entry, multiple exits, wrong exit, missed exit, GPS drift and route rejoin.
- Prompt time-to-maneuver uses reliable distance, speed, confidence, maneuver complexity and bounded rider reaction time.
- Never advance solely because one noisy fix is spatially close.

### P0 — Route matching and rerouting need replay coverage

The current progress path uses a local monotonic tracker and accepts a match only within a confidence limit. This is a useful guard, but route guidance, route metrics, arrival and reroute must be checked for consistent use of the same accepted match and for recovery from ambiguous candidates. Off-route detection uses lateral distance and time thresholds, then a 30-second reroute cooldown.

Acceptance:
- Replay parallel carriageways, junctions, hairpins, U-turns, roundabouts, stop/restart, GPS jumps and short match-loss intervals.
- Verify no false reroute while still on-route and bounded recovery after a true departure.
- Verify route progress can recover from a legitimate reversal/U-turn without becoming permanently stuck.
- Arrival must be tied to route-end evidence and destination proximity, not an arbitrary nearby road.

### P1 — GPX/CSV pairing and recovery

PR #14 changes GPX recording to use accepted raw GPS fixes, matching the CSV's raw-fix basis. It finalizes a paired save and deletes checkpoints only after both outputs are present. This is directionally correct.

Important implementation detail: `finishGpxRecording` returns immediately unless `recordingGpx` is true. The dashboard currently starts both flags together, but every caller and recovery path must preserve that invariant. Saving two files is not enough: sample timestamps, coordinates, accepted/rejected status and row counts must be reconciled.

Acceptance:
- GPX points correspond exactly to accepted raw CSV fixes under the documented sampling policy.
- Timestamps are monotonic and coordinate order is valid.
- Simulate process death before checkpoint, between temp writes, between final renames and after only one final file exists.
- Recover or report orphaned pairs; never silently delete the only recoverable copy.
- Independent recording survives navigation clear/arrival; navigation-owned recording closes exactly once.

### P1 — Speed-limit freshness and provenance

The current implementation explicitly uses TomTom as the sole automatic speed-limit provider and checks whether a verified result remains applicable to the current fix. This avoids mixing conflicting OSM values into the legal limit display.

Acceptance:
- Every displayed limit has source, acquisition time, road/direction association and a validity decision.
- Stale, ambiguous, wrong-direction, late or unavailable answers become unknown.
- A late response cannot overwrite a newer road context.
- Manual override precedence and user calibration are tested separately from legal posted limit.
- Overspeed alert thresholds, visual flash cadence and voice repeat cadence match the agreed product policy.

### P1 — Saved places and favourites are split

The current code has separate stores and menus: address favourites use `favorite_*` preferences and the “Preferiti” menu; saved map places use `poi_*` preferences and a separate “LUOGHI” flow. This explains why a map flag such as “Casa” may not be manageable from the expected Preferiti screen.

Acceptance:
- One coherent Preferiti/luoghi manager for address favourites and map flags.
- Select, navigate, rename and delete each saved item.
- Deletion updates persistent storage and map markers immediately.
- Duplicate names and malformed preference data are handled safely.
- Regression test for a saved “Casa” flag.

### P2 — Android runtime / performance

`MainActivity.kt` still owns a large share of GPS, map, route, TTS, speed-limit and recording orchestration. Refactor incrementally only after deterministic seams and regression tests exist. Do not combine architectural refactoring with behavior changes in the same unreviewed patch.

Acceptance:
- No network or heavy geometry work on the UI thread.
- Location/TTS/map callbacks are lifecycle-safe.
- No duplicate location subscriptions, leaking animators, or unbounded route/trace collections.
- Battery, memory and frame-time measurements on the target Samsung device.

## Integration decision

1. Keep PR #14's branch as the current integration baseline for the GPX/archive and GPS-marker work, subject to independent review.
2. Keep PR #9 separate until its voice changes are reconciled against the exact PR #14 source and all TTS call sites are routed through one scheduler.
3. PR #15 is documentation/configuration only; useful as a team charter, not a runtime feature.
4. Do not merge PR #9/#13/#14/#15 without a reviewed dependency plan and exact-head CI.
5. Do not change approved S22 Ultra layouts during navigation correctness work.

## Field-test release gates

A candidate APK is eligible for controlled field testing only when:
- Exact candidate commit has successful Android CI and APK artifact.
- Unit/replay tests cover P0/P1 scenarios above.
- No unresolved critical/high-severity static review finding.
- APK signing identity and version are verified.
- Test plan and known limitations are attached to the candidate.

Field testing is still required for:
- GPS reacquisition and heading stability on the Tricity 300.
- Junction/roundabout prompt timing and correct exit.
- Voice arbitration through Cardo Freecom 4x, including simultaneous radar/overspeed/turn events.
- TomTom coverage, direction correctness and temporary network loss.
- GPX/CSV recording start/stop, independent of navigation, plus recovery.
- Arrival, missed destination and rerouting.

## Explicit non-claims

This audit is based on source inspection and GitHub CI metadata. It does not claim that an APK was installed on the Samsung, that Render was deployed, that routes were tested physically, or that the application is release-ready.
