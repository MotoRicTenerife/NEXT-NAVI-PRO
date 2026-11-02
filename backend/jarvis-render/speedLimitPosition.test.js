import test from "node:test";
import assert from "node:assert/strict";
import {
  buildTomTomPositionSpeedLimitUrl,
  parseTomTomPositionSpeedLimit
} from "./speedLimitPosition.js";

test("builds a single-coordinate reverse-geocode request with speed limits enabled", () => {
  const url = new URL(buildTomTomPositionSpeedLimitUrl({
    latitude: 28.1101,
    longitude: -16.7302,
    apiKey: "server-secret"
  }));
  assert.equal(url.pathname, "/search/2/reverseGeocode/28.1101,-16.7302.json");
  assert.equal(url.searchParams.get("returnSpeedLimit"), "true");
  assert.equal(url.searchParams.get("radius"), "50");
  assert.equal(url.searchParams.get("key"), "server-secret");
  assert.equal(url.searchParams.has("heading"), false);
});

test("adds a reliable heading when supplied", () => {
  const url = new URL(buildTomTomPositionSpeedLimitUrl({
    latitude: 28.1, longitude: -16.7, apiKey: "key", heading: 185.5
  }));
  assert.equal(url.searchParams.get("heading"), "185.5");
});

test("rejects invalid coordinates and missing server key", () => {
  assert.throws(() => buildTomTomPositionSpeedLimitUrl({
    latitude: 91, longitude: 0, apiKey: "key"
  }), TypeError);
  assert.throws(() => buildTomTomPositionSpeedLimitUrl({
    latitude: 0, longitude: 0, apiKey: ""
  }), TypeError);
});

test("parses metric speed limits and road identity", () => {
  assert.deepEqual(parseTomTomPositionSpeedLimit({
    addresses: [{
      matchType: "Street",
      address: {
        speedLimit: "30.00KPH",
        streetName: "Calle Example",
        routeNumbers: ["TF-82"]
      }
    }]
  }), {
    speedLimitKmh: 30,
    road: "Calle Example",
    ref: "TF-82",
    matchType: "Street",
    reason: null
  });
});

test("converts mph and rejects malformed or implausible limits", () => {
  assert.equal(parseTomTomPositionSpeedLimit({
    addresses: [{ address: { speedLimit: "50.00MPH" } }]
  }).speedLimitKmh, 80);
  assert.equal(parseTomTomPositionSpeedLimit({
    addresses: [{ address: { speedLimit: "201.00KPH" } }]
  }).speedLimitKmh, null);
  assert.equal(parseTomTomPositionSpeedLimit({
    addresses: [{ address: { speedLimit: "unknown" } }]
  }).reason, "no_explicit_speed_limit");
  assert.equal(parseTomTomPositionSpeedLimit({ addresses: [] }).reason, "no_matching_road");
});
