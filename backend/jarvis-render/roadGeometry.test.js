import test from "node:test";
import assert from "node:assert/strict";
import { parseOsmApiFullWayGeometry, parseOverpassWayGeometry } from "./roadGeometry.js";

test("Overpass geometry preserves coordinate order and tags", () => {
  const parsed = parseOverpassWayGeometry({
    elements: [{
      type: "way", id: 42, tags: { name: "TF-82", oneway: "no" },
      geometry: [{ lat: 28.1, lon: -16.7 }, { lat: 28.2, lon: -16.8 }]
    }]
  }, 42);
  assert.equal(parsed.way.tags.name, "TF-82");
  assert.deepEqual(parsed.geometry, [
    { latitude: 28.1, longitude: -16.7 },
    { latitude: 28.2, longitude: -16.8 }
  ]);
});

test("official OSM full-way response rebuilds ordered geometry from node IDs", () => {
  const parsed = parseOsmApiFullWayGeometry({
    elements: [
      { type: "way", id: 42, nodes: [7, 8, 9], tags: { ref: "TF-82" } },
      { type: "node", id: 9, lat: 28.3, lon: -16.9 },
      { type: "node", id: 7, lat: 28.1, lon: -16.7 },
      { type: "node", id: 8, lat: 28.2, lon: -16.8 }
    ]
  }, 42);
  assert.equal(parsed.way.tags.ref, "TF-82");
  assert.deepEqual(parsed.geometry, [
    { latitude: 28.1, longitude: -16.7 },
    { latitude: 28.2, longitude: -16.8 },
    { latitude: 28.3, longitude: -16.9 }
  ]);
});

test("incomplete or mismatched OSM geometry is rejected", () => {
  assert.equal(parseOverpassWayGeometry({ elements: [{ type: "way", id: 42, geometry: [{ lat: 1, lon: 2 }] }] }, 42), null);
  assert.equal(parseOsmApiFullWayGeometry({
    elements: [{ type: "way", id: 42, nodes: [1, 2] }, { type: "node", id: 1, lat: 1, lon: 2 }]
  }, 42), null);
  assert.equal(parseOsmApiFullWayGeometry({ elements: [] }, 42), null);
});
