import test from "node:test";
import assert from "node:assert/strict";
import { summarizeTomTomTrafficFlow } from "./trafficFlowSummary.js";

test("reports flowing traffic from current and free-flow speeds", () => {
  const summary = summarizeTomTomTrafficFlow({
    currentSpeedKmh: 47, freeFlowSpeedKmh: 47, confidence: 1
  });
  assert.match(summary, /traffico scorrevole/);
  assert.match(summary, /47 km\/h contro 47 km\/h/);
  assert.match(summary, /confidenza alta/);
});

test("reports congestion proportionally and qualifies low confidence", () => {
  const summary = summarizeTomTomTrafficFlow({
    currentSpeedKmh: 20, freeFlowSpeedKmh: 60, confidence: 0.3
  });
  assert.match(summary, /fortemente congestionato/);
  assert.match(summary, /confidenza bassa/);
  assert.match(summary, /solo segmento stradale abbinato/);
});

test("reports a provider road closure without inventing a queue", () => {
  const summary = summarizeTomTomTrafficFlow({
    currentSpeedKmh: 0, freeFlowSpeedKmh: 50, confidence: 0.9, roadClosure: true
  });
  assert.match(summary, /chiusura segnalata/);
});

test("rejects absent or invalid flow measurements", () => {
  assert.equal(summarizeTomTomTrafficFlow(null), null);
  assert.equal(summarizeTomTomTrafficFlow({ currentSpeedKmh: 10, freeFlowSpeedKmh: 0 }), null);
});
