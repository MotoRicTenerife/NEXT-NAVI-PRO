import test from "node:test";
import assert from "node:assert/strict";
import { parseExplicitMaximumSpeedLimits } from "./speedLimitParsing.js";

test("accepts explicit maximum limits with recognized metric units", () => {
  assert.deepEqual(
    parseExplicitMaximumSpeedLimits([{ value: 50, unit: "km/h", type: "Maximum" }]),
    [{ kmh: 50, rawValue: 50, unit: "km/h", type: "Maximum" }]
  );
});

test("converts miles per hour and rounds to km/h", () => {
  assert.equal(
    parseExplicitMaximumSpeedLimits([{ value: 30, unit: "mph", type: "maximum" }])[0].kmh,
    48
  );
});

test("rejects advisory, minimum, unknown-unit and implausible values", () => {
  const raw = [
    { value: 50, unit: "km/h", type: "advisory" },
    { value: 50, unit: "km/h", type: "minimum" },
    { value: 50, unit: "unknown", type: "maximum" },
    { value: 2, unit: "km/h", type: "maximum" },
    { value: 250, unit: "km/h", type: "maximum" },
    { value: Number.NaN, unit: "km/h", type: "maximum" }
  ];
  assert.deepEqual(parseExplicitMaximumSpeedLimits(raw), []);
});

test("keeps conflicting maximum values distinct so callers can mark the result unknown", () => {
  const parsed = parseExplicitMaximumSpeedLimits([
    { value: 30, unit: "km/h", type: "Maximum" },
    { value: 50, unit: "km/h", type: "Maximum" }
  ]);
  assert.deepEqual([...new Set(parsed.map(item => item.kmh))], [30, 50]);
});

test("accepts singleton objects and arrays without inventing missing limits", () => {
  assert.equal(parseExplicitMaximumSpeedLimits({ value: 40, unit: "kmh", type: "Maximum" }).length, 1);
  assert.deepEqual(parseExplicitMaximumSpeedLimits(null), []);
  assert.deepEqual(parseExplicitMaximumSpeedLimits([{ value: 40, type: "Maximum" }]), []);
});
