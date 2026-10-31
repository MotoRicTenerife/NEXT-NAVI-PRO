import test from "node:test";
import assert from "node:assert/strict";
import { sanitizeTripContext } from "./tripContextPolicy.js";

const now = 1_800_000_000_000;
const valid = {
  destinationName: "Los Cristianos",
  vehicleMode: "motorcycle",
  distanceKm: 18.4,
  durationSeconds: 1320,
  fetchedAtEpochMs: now - 60_000,
  routeSource: "Valhalla motorcycle",
  routeEstimateType: "baseline_route_engine_estimate_not_traffic_adjusted",
  trafficSource: "TomTom Orbis Traffic Incidents",
  trafficObservedAtEpochMs: now - 30_000,
  trafficCache: "MISS",
  routeRequest: {
    origin: { latitude: 28.05, longitude: -16.72 },
    destination: { latitude: 28.06, longitude: -16.68 },
    waypoints: [], vehicleMode: "motorcycle"
  },
  incidents: [{
    id: "incident-1", severity: "HEAVY", distanceAheadMeters: 3200,
    delaySeconds: 240, road: "TF-1", description: "Accidente",
    observedAtEpochMs: now - 30_000, validUntilEpochMs: now + 120_000,
    geometryFitScore: 0.9, directionVerified: true
  }]
};

test("accepts fresh route context and never trusts client direction flags", () => {
  const result = sanitizeTripContext(valid, now);
  assert.equal(result.destinationName, "Los Cristianos");
  assert.equal(result.incidents.length, 1);
  assert.equal(result.incidents[0].directionVerified, false);
});

test("rejects stale or malformed route and traffic provenance", () => {
  assert.equal(sanitizeTripContext({ ...valid, fetchedAtEpochMs: now - 31 * 60_000 }, now), null);
  assert.equal(sanitizeTripContext({ ...valid, trafficObservedAtEpochMs: now - 6 * 60_000 }, now), null);
  assert.equal(sanitizeTripContext({ ...valid, distanceKm: -1 }, now), null);
  assert.equal(sanitizeTripContext({ ...valid, vehicleMode: "truck" }, now), null);
});

test("rejects malformed or mismatched route requests without rejecting valid trip facts", () => {
  const result = sanitizeTripContext({
    ...valid,
    routeRequest: { ...valid.routeRequest, vehicleMode: "car" }
  }, now);
  assert.equal(result.routeRequest, null);
  const validResult = sanitizeTripContext(valid, now);
  assert.equal(validResult.routeRequest.vehicleMode, "motorcycle");
});

test("drops stale, expired and malformed incidents", () => {
  const result = sanitizeTripContext({
    ...valid,
    incidents: [
      ...valid.incidents,
      { ...valid.incidents[0], id: "stale", observedAtEpochMs: now - 6 * 60_000 },
      { ...valid.incidents[0], id: "expired", validUntilEpochMs: now - 1 },
      { ...valid.incidents[0], id: "invalid", distanceAheadMeters: -10 }
    ]
  }, now);
  assert.equal(result.incidents.length, 1);
});

test("bounds destination text", () => {
  const result = sanitizeTripContext({ ...valid, destinationName: "A".repeat(300) }, now);
  assert.equal(result.destinationName.length, 120);
});
