import test from "node:test";
import assert from "node:assert/strict";
import { isTomTomTrafficRoutingEnabled } from "./trafficFeaturePolicy.js";

test("enables TomTom route traffic by default when a server key exists", () => {
  assert.equal(isTomTomTrafficRoutingEnabled(undefined, "configured-key"), true);
  assert.equal(isTomTomTrafficRoutingEnabled("true", "configured-key"), true);
});

test("allows an explicit disable and refuses to run without a key", () => {
  assert.equal(isTomTomTrafficRoutingEnabled("false", "configured-key"), false);
  assert.equal(isTomTomTrafficRoutingEnabled(undefined, ""), false);
  assert.equal(isTomTomTrafficRoutingEnabled("true", null), false);
});
