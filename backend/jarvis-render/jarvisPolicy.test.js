import test from "node:test";
import assert from "node:assert/strict";
import { selectJarvisResponseMode } from "./jarvisPolicy.js";

test("traffic and weather questions still go through Gemini when configured", () => {
  for (const intent of ["traffic", "weather", "route", "general"]) {
    assert.equal(selectJarvisResponseMode({ apiKey: "configured-secret", intent }), "gemini");
  }
});

test("verified live data may use deterministic fallback only when Gemini is unavailable", () => {
  assert.equal(selectJarvisResponseMode({ apiKey: "" }), "verified_live_fallback");
  assert.equal(selectJarvisResponseMode({ apiKey: undefined }), "verified_live_fallback");
  assert.equal(selectJarvisResponseMode({ apiKey: "   " }), "verified_live_fallback");
});
