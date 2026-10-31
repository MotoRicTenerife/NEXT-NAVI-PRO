// Admission control for the TomTom-backed POST /speed-limit route.
//
// Goals (see README "TomTom quota and rate limiting"):
//  - protect the TomTom key against runaway clients and against the free daily allowance
//    (TomTom documents 2,500 free non-tile requests per day; Snap to Roads is a non-tile API);
//  - never starve a rider who is simply navigating: a token bucket allows a burst and a
//    sustained rate above the app's normal cadence instead of a hard 20-per-10-minutes window;
//  - stay completely separate from the manual-segment archive limiter;
//  - tell the client exactly how long to back off (Retry-After) instead of failing silently.
//
// All state is in memory. Render's free plan restarts/sleeps the service, which resets the
// counters: the daily budget is a best-effort safeguard, not an accounting system.

const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_KEYS = 5000;

/** Fixed window limiter (same semantics as the previous per-IP limiter). */
export function createFixedWindowLimiter({ max, windowMs, maxKeys = MAX_KEYS }) {
  const windows = new Map();
  return {
    check(key, now) {
      let window = windows.get(key);
      if (!window || now - window.startedAt >= windowMs) {
        window = { startedAt: now, count: 0 };
        windows.set(key, window);
      }
      if (window.count >= max) {
        return { allowed: false, retryAfterMs: Math.max(1, window.startedAt + windowMs - now) };
      }
      window.count += 1;
      if (windows.size > maxKeys) {
        for (const [k, v] of windows) {
          if (now - v.startedAt >= windowMs) windows.delete(k);
        }
      }
      return { allowed: true, retryAfterMs: 0 };
    }
  };
}

/** Token bucket: `capacity` burst, one token regained every `refillMs`. */
export function createTokenBucket({ capacity, refillMs, maxKeys = MAX_KEYS }) {
  const buckets = new Map();
  return {
    take(key, now) {
      let bucket = buckets.get(key);
      if (!bucket) {
        bucket = { tokens: capacity, updatedAt: now };
        buckets.set(key, bucket);
      } else {
        const elapsed = Math.max(0, now - bucket.updatedAt);
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed / refillMs);
        bucket.updatedAt = now;
      }
      if (bucket.tokens < 1) {
        return { allowed: false, retryAfterMs: Math.ceil((1 - bucket.tokens) * refillMs) };
      }
      bucket.tokens -= 1;
      if (buckets.size > maxKeys) {
        for (const [k, v] of buckets) {
          if (v.tokens + (now - v.updatedAt) / refillMs >= capacity) buckets.delete(k);
        }
      }
      return { allowed: true, retryAfterMs: 0 };
    }
  };
}

/** Global budget of upstream calls per UTC day. */
export function createDailyBudget({ limit }) {
  let day = null;
  let used = 0;
  const roll = now => {
    const current = Math.floor(now / DAY_MS);
    if (current !== day) { day = current; used = 0; }
    return current;
  };
  return {
    take(now) {
      const current = roll(now);
      if (used >= limit) return { allowed: false, retryAfterMs: (current + 1) * DAY_MS - now };
      used += 1;
      return { allowed: true, retryAfterMs: 0 };
    },
    snapshot(now) {
      roll(now);
      return { used, limit };
    }
  };
}

function deny(reason, retryAfterMs) {
  return { allowed: false, reason, retryAfterSeconds: Math.max(1, Math.ceil(retryAfterMs / 1000)) };
}

function parseRetryAfterSeconds(raw) {
  const value = Number.parseInt(String(raw ?? "").trim(), 10);
  return Number.isFinite(value) && value > 0 ? value : null;
}

export function createSpeedLimitGuard({
  burst = 30,
  refillMs = 5000,
  dailyBudget = 2000,
  now = () => Date.now()
} = {}) {
  const bucket = createTokenBucket({ capacity: burst, refillMs });
  const budget = createDailyBudget({ limit: dailyBudget });
  let cooldownUntil = 0;
  let cooldownReason = null;

  return {
    /** Decide whether one TomTom non-tile upstream call may be made on behalf of `clientKey`. */
    admit(clientKey) {
      const t = now();
      if (t < cooldownUntil) return deny("provider_cooldown", cooldownUntil - t);
      const token = bucket.take(clientKey, t);
      if (!token.allowed) return deny("rate_limited", token.retryAfterMs);
      const day = budget.take(t);
      // A day-level refusal is re-evaluated at most hourly so a restart or a raised budget is noticed.
      if (!day.allowed) return deny("daily_budget_exhausted", Math.min(day.retryAfterMs, 60 * 60 * 1000));
      return { allowed: true };
    },
    /** Record TomTom's own answer so a refused key or quota is not hammered. */
    noteUpstreamStatus(status, retryAfterHeader) {
      const t = now();
      if (status === 429) {
        const seconds = Math.min(300, Math.max(5, parseRetryAfterSeconds(retryAfterHeader) ?? 30));
        cooldownUntil = t + seconds * 1000;
        cooldownReason = "tomtom_quota_or_rate_limit";
      } else if (status === 401 || status === 403) {
        cooldownUntil = t + 300 * 1000;
        cooldownReason = "tomtom_auth_or_permission_error";
      }
    },
    /** Remaining upstream cooldown in whole seconds (0 when none) and why. */
    cooldown() {
      const remaining = Math.max(0, cooldownUntil - now());
      return { seconds: Math.ceil(remaining / 1000), reason: remaining > 0 ? cooldownReason : null };
    },
    snapshot() {
      return { dailyBudget: budget.snapshot(now()), burst, refillMs, cooldown: this.cooldown() };
    }
  };
}
