import test from "node:test";
import assert from "node:assert/strict";
import {
  clearTrafficCacheForTests,
  fetchTomTomTraffic,
  parseTomTomFlow,
  parseTomTomIncidents
} from "./trafficService.js";

test("TomTom flow computes a meaningful congestion state without guessing", () => {
  const flow = parseTomTomFlow({ flowSegmentData: {
    currentSpeed: 18, freeFlowSpeed: 60, currentTravelTime: 120,
    freeFlowTravelTime: 36, confidence: 0.92, roadClosure: false, frc: "FRC2"
  }});
  assert.equal(flow.currentSpeedKmh, 18);
  assert.equal(flow.freeFlowSpeedKmh, 60);
  assert.equal(flow.delayPercent, 70);
  assert.equal(flow.roadCondition, "traffico molto intenso");
  assert.equal(flow.confidence, 0.92);
});

test("TomTom incident parser sorts nearby incidents and rejects malformed geometry", () => {
  const incidents = parseTomTomIncidents({ incidents: [
    { geometry: { type: "Point", coordinates: [-16.7, 28.1] }, properties: {
      id: "near", iconCategory: 6, magnitudeOfDelay: 2,
      events: [{ description: "Coda" }], roadNumbers: ["TF-1"], delay: 240
    }},
    { geometry: { type: "Point", coordinates: [-16.9, 28.3] }, properties: { id: "far" } },
    { geometry: { type: "Point", coordinates: ["bad", 28] }, properties: { id: "bad" } }
  ]}, { latitude: 28.1001, longitude: -16.7001 });
  assert.equal(incidents.length, 1);
  assert.equal(incidents[0].id, "near");
  assert.equal(incidents[0].description, "Coda");
  assert.equal(incidents[0].road, "TF-1");
  assert.ok(incidents[0].distanceKm < 1);
});

test("TomTom traffic calls Flow and Incidents, returns structured live data and caches briefly", async () => {
  clearTrafficCacheForTests();
  const calls = [];
  const fetchImpl = async url => {
    calls.push(String(url));
    if (String(url).includes("flowSegmentData")) {
      return { ok: true, json: async () => ({ flowSegmentData: {
        currentSpeed: 35, freeFlowSpeed: 50, confidence: 0.8, roadClosure: false
      }}) };
    }
    return { ok: true, json: async () => ({ incidents: [] }) };
  };
  const location = { latitude: 28.1, longitude: -16.7 };
  const first = await fetchTomTomTraffic(location, "server-secret", fetchImpl);
  const second = await fetchTomTomTraffic(location, "server-secret", fetchImpl);
  assert.equal(first.source, "TomTom Traffic");
  assert.equal(first.status, "complete");
  assert.equal(first.flow.currentSpeedKmh, 35);
  assert.deepEqual(first.incidents, []);
  assert.equal(second.cacheHit, true);
  assert.equal(calls.length, 2);
  assert.ok(calls.every(url => url.includes("key=server-secret")));
});

test("TomTom traffic rejects missing credentials and coordinates", async () => {
  await assert.rejects(() => fetchTomTomTraffic(null, "key"), /traffic_location_invalid/);
  await assert.rejects(() => fetchTomTomTraffic({ latitude: 28, longitude: -16 }, ""), /tomtom_api_key_missing/);
});
