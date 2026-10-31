import test from "node:test";
import assert from "node:assert/strict";
import { buildTomTomRouteUrl, validateTomTomRouteRequest, normalizeTomTomTrafficRoutes, fetchTomTomTrafficRoutes } from "./trafficRoutingService.js";

const request = {
  origin: { latitude: 28.05, longitude: -16.72 },
  destination: { latitude: 28.06, longitude: -16.68 },
  waypoints: [{ latitude: 28.055, longitude: -16.70 }],
  vehicleMode: "motorcycle"
};
const validRoute = {
  summary: {
    lengthInMeters: 18400, travelTimeInSeconds: 1320, trafficDelayInSeconds: 240,
    noTrafficTravelTimeInSeconds: 1080, historicTrafficTravelTimeInSeconds: 1190,
    liveTrafficIncidentsTravelTimeInSeconds: 1320, trafficLengthInMeters: 3200,
    departureTime: "2026-10-31T18:00:00+00:00", arrivalTime: "2026-10-31T18:22:00+00:00"
  },
  sections: [
    { sectionType: "TRAVEL_MODE", travelMode: "motorcycle", startPointIndex: 0, endPointIndex: 1 },
    { sectionType: "TRAFFIC", startPointIndex: 4, endPointIndex: 9, delayInSeconds: 240, magnitudeOfDelay: 2, effectiveSpeedInKmh: 28, simpleCategory: "JAM", eventId: "event-1" }
  ],
  legs: [{ points: [{ latitude: 28.05, longitude: -16.72 }, { latitude: 28.06, longitude: -16.68 }] }]
};

test("validates coordinates, vehicle mode and bounded waypoints", () => {
  assert.equal(validateTomTomRouteRequest(request).waypoints.length, 1);
  assert.equal(validateTomTomRouteRequest({ ...request, vehicleMode: "truck" }), null);
  assert.equal(validateTomTomRouteRequest({ ...request, waypoints: Array(4).fill(request.origin) }), null);
  assert.equal(validateTomTomRouteRequest({ ...request, origin: { latitude: 100, longitude: 0 } }), null);
});

test("builds a traffic-aware motorcycle route request with waypoint and mode sections", () => {
  const url = buildTomTomRouteUrl(request, "secret");
  assert.match(url.pathname, /28\.050000,-16\.720000:28\.055000,-16\.700000:28\.060000,-16\.680000/);
  assert.equal(url.searchParams.get("travelMode"), "motorcycle");
  assert.equal(url.searchParams.get("traffic"), "true");
  assert.equal(url.searchParams.get("computeTravelTimeFor"), "all");
  assert.deepEqual(url.searchParams.getAll("sectionType"), ["travelMode", "traffic"]);
  assert.equal(url.searchParams.get("key"), "secret");
});

test("normalizes ETA, traffic delay and incident sections only for verified vehicle mode", () => {
  const result = normalizeTomTomTrafficRoutes({ routes: [validRoute] }, "motorcycle", 1_800_000_000_000);
  assert.equal(result.routes.length, 1);
  assert.equal(result.routes[0].travelTimeInSeconds, 1320);
  assert.equal(result.routes[0].trafficDelayInSeconds, 240);
  assert.equal(result.routes[0].noTrafficTravelTimeInSeconds, 1080);
  assert.equal(result.routes[0].trafficSections[0].eventId, "event-1");
  assert.equal(result.routes[0].travelModeVerified, true);
});

test("rejects routes with any unverified or non-motorcycle section", () => {
  const wrong = structuredClone(validRoute);
  wrong.sections[0].travelMode = "other";
  const result = normalizeTomTomTrafficRoutes({ routes: [validRoute, wrong] }, "motorcycle", 1_800_000_000_000);
  assert.equal(result.routes.length, 1);
  assert.equal(result.rejectedRoutes, 1);
});

test("mocked upstream fetch never returns the API key", async () => {
  let requestedUrl = "";
  const result = await fetchTomTomTrafficRoutes(request, {
    apiKey: "secret",
    now: 1_800_000_000_000,
    fetchImpl: async url => {
      requestedUrl = String(url);
      return { ok: true, json: async () => ({ routes: [validRoute] }) };
    }
  });
  assert.match(requestedUrl, /key=secret/);
  assert.equal(JSON.stringify(result).includes("secret"), false);
  assert.equal(result.routes[0].trafficDelayInSeconds, 240);
});
