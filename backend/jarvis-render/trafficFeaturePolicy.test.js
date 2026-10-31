import test from "node:test";
import assert from "node:assert/strict";
import {
  isTomTomTrafficFlowEnabled,
  isTomTomTrafficIncidentsEnabled,
  isTomTomTrafficRoutingEnabled
} from "./trafficFeaturePolicy.js";

const features = [
  ["flow tiles", isTomTomTrafficFlowEnabled],
  ["incident details", isTomTomTrafficIncidentsEnabled],
  ["traffic-aware routing", isTomTomTrafficRoutingEnabled]
];

for (const [name, enabled] of features) {
  test(`enables TomTom ${name} by default when a server key exists`, () => {
    assert.equal(enabled(undefined, "configured-key"), true);
    assert.equal(enabled("true", "configured-key"), true);
  });

  test(`allows an explicit disable and refuses ${name} without a key`, () => {
    assert.equal(enabled("false", "configured-key"), false);
    assert.equal(enabled(undefined, ""), false);
    assert.equal(enabled("true", null), false);
  });
}
