import test from "node:test";
import assert from "node:assert/strict";
import { selectJarvisResponseMode } from "./jarvisPolicy.js";

test("traffic and weather questions still go through Gemini when configured", () => {
  for (const intent of ["traffic", "weather", "route", "general"]) {
    assert.equal(selectJarvisResponseMode({
      apiKey: "configured-secret",
      hasLiveIntent: intent !== "general",
      hasVerifiedLiveData: true
    }), "gemini");
  }
});

test("verified live facts may be returned deterministically only when Gemini is unavailable", () => {
  assert.equal(selectJarvisResponseMode({
    apiKey: "",
    hasLiveIntent: true,
    hasVerifiedLiveData: true
  }), "verified_live_fallback");
});

test("missing Gemini and missing live facts must not fabricate a response", () => {
  assert.equal(selectJarvisResponseMode({
    apiKey: undefined,
    hasLiveIntent: true,
    hasVerifiedLiveData: false
  }), "unavailable");
});

test("whitespace-only credentials are treated as missing", () => {
  assert.equal(selectJarvisResponseMode({
    apiKey: "   ",
    hasLiveIntent: false,
    hasVerifiedLiveData: false
  }), "unavailable");
});
