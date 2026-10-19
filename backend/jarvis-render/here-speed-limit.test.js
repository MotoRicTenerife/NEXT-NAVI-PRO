import test from "node:test";
import assert from "node:assert/strict";
import { parseHereSpeedLimit } from "./here-speed-limit.js";

const response = links => JSON.stringify({ response: { route: [{ leg: [{ link: links }] }] } });

test("accepts equal explicit limits for both directions", () => {
  assert.equal(parseHereSpeedLimit(response([
    { attributes: { SPEED_LIMITS_FC2: [{ FROM_REF_SPEED_LIMIT: 50, TO_REF_SPEED_LIMIT: 50, UNIT: "K" }] } }
  ])), 50);
});

test("converts HERE miles-per-hour units to km/h", () => {
  assert.equal(parseHereSpeedLimit(response([
    { attributes: { SPEED_LIMITS_FC1: [{ FROM_REF_SPEED_LIMIT: 30, TO_REF_SPEED_LIMIT: 30, UNIT: "M" }] } }
  ])), 48);
});

test("does not guess when directional limits conflict", () => {
  assert.equal(parseHereSpeedLimit(response([
    { attributes: { SPEED_LIMITS_FC2: [{ FROM_REF_SPEED_LIMIT: 30, TO_REF_SPEED_LIMIT: 50, UNIT: "K" }] } }
  ])), null);
});

test("does not reuse a limit from an earlier link when the latest link is unknown", () => {
  assert.equal(parseHereSpeedLimit(response([
    { attributes: { SPEED_LIMITS_FC2: [{ FROM_REF_SPEED_LIMIT: 50, TO_REF_SPEED_LIMIT: 50, UNIT: "K" }] } },
    { attributes: { LINK_ATTRIBUTE_FC2: [{ ISO_COUNTRY_CODE: "ESP" }] } }
  ])), null);
});

test("returns unknown for missing route data", () => {
  assert.equal(parseHereSpeedLimit(JSON.stringify({ response: { route: [] } })), null);
});
