import test from "node:test";
import assert from "node:assert/strict";
import { createFixedWindowLimiter, createSpeedLimitGuard } from "./speedLimitGuard.js";

test("token bucket permits a bounded burst and refills at the configured cadence", () => {
  let now = 0;
  const guard = createSpeedLimitGuard({ burst: 2, refillMs: 1000, dailyBudget: 10, now: () => now });
  assert.equal(guard.admit("rider-a").allowed, true);
  assert.equal(guard.admit("rider-a").allowed, true);
  const blocked = guard.admit("rider-a");
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, "rate_limited");
  assert.equal(blocked.retryAfterSeconds, 1);
  now = 1000;
  assert.equal(guard.admit("rider-a").allowed, true);
});

test("daily budget blocks additional upstream calls after its limit", () => {
  const guard = createSpeedLimitGuard({ burst: 10, refillMs: 1000, dailyBudget: 1, now: () => 1_800_000 });
  assert.equal(guard.admit("rider-a").allowed, true);
  const blocked = guard.admit("rider-b");
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, "daily_budget_exhausted");
});

test("provider 429 response activates a bounded cooldown", () => {
  let now = 0;
  const guard = createSpeedLimitGuard({ burst: 10, refillMs: 1000, dailyBudget: 10, now: () => now });
  guard.noteUpstreamStatus(429, "10");
  assert.deepEqual(guard.cooldown(), { seconds: 10, reason: "tomtom_quota_or_rate_limit" });
  assert.equal(guard.admit("rider-a").reason, "provider_cooldown");
  now = 10_000;
  assert.equal(guard.admit("rider-a").allowed, true);
});

test("provider authorization failures activate a five-minute cooldown", () => {
  const guard = createSpeedLimitGuard({ burst: 10, refillMs: 1000, dailyBudget: 10, now: () => 500 });
  guard.noteUpstreamStatus(403, null);
  assert.equal(guard.cooldown().seconds, 300);
  assert.equal(guard.cooldown().reason, "tomtom_auth_or_permission_error");
});

test("fixed window archive limiter rejects excess requests and resets after the window", () => {
  const limiter = createFixedWindowLimiter({ max: 1, windowMs: 1000 });
  assert.equal(limiter.check("ip", 0).allowed, true);
  assert.equal(limiter.check("ip", 10).allowed, false);
  assert.equal(limiter.check("ip", 1000).allowed, true);
});
