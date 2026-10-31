import test from "node:test";
import assert from "node:assert/strict";
import { buildTomTomIncidentDetailsUrl, parseIncidentBbox } from "./trafficIncidentPolicy.js";

test("accepts a bounded valid incident corridor", () => {
  const bbox = parseIncidentBbox("-16.8,27.9,-16.5,28.2");
  assert.ok(bbox);
  assert.equal(bbox.value, "-16.8,27.9,-16.5,28.2");
  assert.ok(bbox.areaKm2 > 0 && bbox.areaKm2 < 10_000);
});

test("rejects malformed, inverted, out-of-range and oversized bboxes", () => {
  for (const raw of [
    null, "", "1,2,3", "1,2,3,4,5", "NaN,2,3,4",
    "181,0,182,1", "-1,-91,1,-90", "2,2,1,3", "1,3,2,2",
    "-180,-80,180,80"
  ]) assert.equal(parseIncidentBbox(raw), null, String(raw));
});

test("builds Orbis v2 incident URL without embedding provider credentials", () => {
  const bbox = parseIncidentBbox("-16.8,27.9,-16.5,28.2");
  const url = buildTomTomIncidentDetailsUrl(bbox);
  assert.equal(url.hostname, "api.tomtom.com");
  assert.equal(url.pathname, "/maps/orbis/traffic/incidents/details");
  assert.equal(url.searchParams.get("apiVersion"), "2");
  assert.equal(url.searchParams.get("bbox"), bbox.value);
  assert.equal(url.searchParams.get("timeValidity"), "present");
  assert.equal(url.searchParams.has("key"), false);
});
