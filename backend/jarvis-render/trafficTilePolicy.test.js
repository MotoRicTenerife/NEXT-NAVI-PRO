import test from "node:test";
import assert from "node:assert/strict";
import { buildTomTomTrafficTileUrl, parseTrafficTilePath } from "./trafficTilePolicy.js";

test("accepts valid XYZ traffic tiles including the world tile", () => {
  assert.deepEqual(parseTrafficTilePath("/traffic/flow/0/0/0"), { z: 0, x: 0, y: 0 });
  assert.deepEqual(parseTrafficTilePath("/traffic/flow/12/2177/1436"), { z: 12, x: 2177, y: 1436 });
});

test("rejects malformed and out-of-range tile coordinates", () => {
  for (const path of [
    "/traffic/flow/-1/0/0",
    "/traffic/flow/23/0/0",
    "/traffic/flow/4/16/0",
    "/traffic/flow/4/0/16",
    "/traffic/flow/4/1.5/2",
    "/traffic/flow/4/1/2/extra",
    "/traffic/flow/abc/1/2"
  ]) assert.equal(parseTrafficTilePath(path), null, path);
});

test("builds the official Orbis v2 flow tile URL without exposing a key in the query", () => {
  const url = buildTomTomTrafficTileUrl({ z: 8, x: 130, y: 90 });
  assert.equal(url.hostname, "api.tomtom.com");
  assert.equal(url.pathname, "/maps/orbis/traffic/flow/vector/tile/8/130/90");
  assert.equal(url.searchParams.get("apiVersion"), "2");
  assert.equal(url.searchParams.has("key"), false);
});
