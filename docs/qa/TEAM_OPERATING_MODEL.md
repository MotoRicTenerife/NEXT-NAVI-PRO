# Navigation QA Team — Operating Model

## Purpose
This document turns the specialist profiles into a repeatable engineering process. It does not claim that ten autonomous LLM agents run continuously. The repository contains ten role-specific instruction profiles; GitHub Actions executes deterministic checks. Agent-capable tooling can use the profiles for bounded investigation/review when available.

## The ten specialists
1. **Navigation Architect / QA lead** — owns system contracts, task boundaries, dependency order, and integration decisions.
2. **GNSS & Geodesy** — fix age/accuracy, monotonic time, coordinate geometry, heading quality, raw-vs-filtered position.
3. **Map Matching** — candidate scoring, road topology, confidence, hysteresis, parallel-road ambiguity.
4. **Routing & Maneuvers** — route progress, maneuver lifecycle, roundabout entry/circulation/exit, reroute and arrival.
5. **Speed Intelligence** — TomTom provenance/freshness/direction, tachometer correction, overspeed policy.
6. **Voice & Interaction** — single TTS arbiter, priority, expiry, coalescing, interruption and lifecycle.
7. **Android Runtime** — lifecycle, threading, permissions, battery, rendering, crash and memory behavior.
8. **Data & Telemetry** — GPX/CSV pairing, timestamp/coordinate integrity, replay fixtures and diagnostics.
9. **Predictive AI** — evaluates predictions against deterministic baselines; no authority over legal limits or safety-critical maneuvers.
10. **Independent QA & Safety** — adversarial review, regression matrix, evidence quality, release veto.

## Work protocol
- One ticket/PR has one implementation owner and one independent reviewer.
- Specialists first return evidence: source locations, observed behavior, reproduction, counterexample, severity, and test proposal.
- Architect publishes the contract before implementation where interfaces cross domains.
- Never have two agents edit the same shared file concurrently.
- QA reviews the diff independently and checks the exact candidate SHA.
- CI results are recorded separately from emulator, physical-device, and road results.
- No merge, Render deployment, release, or APK handoff is implied by green CI. Those actions remain approval-gated.

## Automated gates in Navigation QA Team workflow
- Team contract: all ten role profiles and operating documents exist and contain required metadata.
- GNSS/telemetry: Python trace regression suite under `roadtools/test_*.py`.
- Backend/speed intelligence: offline syntax and unit tests; TomTom parsing uses mocked upstream and does not expose API keys.
- Android runtime: debug compile and unit tests on JDK 17 / Android API 35.
- Independent gate: fails if any specialist job fails, is cancelled, or is skipped.

The workflow intentionally does not call Gemini/Jarvis, spend external AI tokens, mutate production data, or deploy Render. It is deterministic and safe to run on every relevant pull request.

## What still requires additional infrastructure
True autonomous chatbot execution needs an explicitly selected provider/runtime, API credentials, cost limits, prompt-injection protections, restricted repository permissions, output review, and audit logs. It must be opt-in/manual initially, not a default pull-request action. Copilot custom-agent profiles alone do not start or schedule agents. No external paid AI calls are enabled by this workflow.

## Evidence levels
- **L0 — static:** source inspection, formatting, contracts.
- **L1 — deterministic automated:** unit tests, backend mocks, trace replay.
- **L2 — integration/simulation:** route/GPX scenarios, Android emulator, controlled provider tests.
- **L3 — physical device:** S22 Ultra lifecycle, GPS, audio/Cardo, thermal and battery.
- **L4 — road validation:** known routes, junctions, roundabouts, parallel roads, reroutes, arrival, speed-limit transitions.

A feature is not road-validated until L4 evidence exists. CI cannot certify real-world navigation safety.
