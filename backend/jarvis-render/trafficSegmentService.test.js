import test from "node:test";
import assert from "node:assert/strict";
import {
  buildTomTomTrafficSegmentUrl,
  validateTrafficPoint,
  normalizeTomTomTrafficSegment,
  fetchTomTomTrafficSegment
} from "./trafficSegmentService.js";

const point = { latitude: 28.06, longitude: -16.68 };
const payload = {
  flowSegmentData: {
    frc: "FRC0",
    currentSpeed: 34,
    freeFlowSpeed: 80,
    currentTravelTime: 120,
    freeFlowTravelTime: 51,
    confidence: 0.92,
    roadClosure: false
  }
};

test("validates GPS point bounds", () => {
  assert.deepEqual(validateTrafficPoint(point), point);
  assert.equal(validateTrafficPoint({ latitude: 91, longitude: 0 }), null);
  assert.equal(validateTrafficPoint({ latitude: 0, longitude: 181 }), null);
});

test("builds TomTom Flow Segment Data request with server-side key and metric units", () => {
  const url = buildTomTomTrafficSegmentUrl(point, "secret");
  assert.equal(url.hostname, "api.tomtom.com");
  assert.equal(url.pathname, "/traffic/services/4/flowSegmentData/absolute/15/json");
  assert.equal(url.searchParams.get("point"), "28.060000,-16.680000");
  assert.equal(url.searchParams.get("unit"), "kmph");
  assert.equal(url.searchParams.get("key"), "secret");
});

test("normalizes current and free-flow speed, confidence and closure", () => {
  const result = normalizeTomTomTrafficSegment(payload, 1_800_000_000_000);
  assert.equal(result.currentSpeedKmh, 34);
  assert.equal(result.freeFlowSpeedKmh, 80);
  assert.equal(result.relativeSpeed, 34 / 80);
  assert.equal(result.confidence, 0.92);
  assert.equal(result.roadClosure, false);
});

test("rejects incomplete or implausible segment responses", () => {
  assert.equal(normalizeTomTomTrafficSegment({ flowSegmentData: { currentSpeed: 30 } }, 1), null);
  assert.equal(normalizeTomTomTrafficSegment({ flowSegmentData: { ...payload.flowSegmentData, confidence: 2 } }, 1), null);
});

test("fetches and validates a live segment using the injected transport", async () => {
  let requestedUrl;
  const result = await fetchTomTomTrafficSegment(point, {
    apiKey: "secret",
    now: 1_800_000_000_000,
    fetchImpl: async url => {
      requestedUrl = url;
      return { ok: true, json: async () => payload };
    }
  });
  assert.equal(new URL(requestedUrl).searchParams.get("point"), "28.060000,-16.680000");
  assert.equal(result.currentSpeedKmh, 34);
});

test("maps upstream quota and entitlement failures to safe provider status codes", async () => {
  await assert.rejects(
    fetchTomTomTrafficSegment(point, { apiKey: "secret", fetchImpl: async () => ({ ok: false, status: 429 }) }),
    error => error.statusCode === 429
  );
  await assert.rejects(
    fetchTomTomTrafficSegment(point, { apiKey: "secret", fetchImpl: async () => ({ ok: false, status: 403 }) }),
    error => error.statusCode === 403
  );
});
