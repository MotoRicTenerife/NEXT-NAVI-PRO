// Mirror of the Android client's request cadence, used only to drive replay tests.
// Keep in sync with app/src/main/java/com/nextnavipro/app/SpeedLimitPolicy.kt (same constants).
export const POLICY = {
  MIN_QUERY_SPACING_MS: 3000, REFRESH_DISTANCE_M: 250, REFRESH_INTERVAL_MS: 20000,
  STATIONARY_RADIUS_M: 30, VALID_DISTANCE_M: 450, VALID_AGE_MS: 45000, SLOW_VALID_DISTANCE_M: 60,
  CROSS_TRACK_TOLERANCE_M: 25, BACKWARD_TOLERANCE_M: 15, MAX_BEARING_DELTA_DEG: 60
};
const M_PER_DEG = 111320;

export function fixOf(f) {
  const moving = Number.isFinite(f.bearingDeg) && f.speedMps >= 2.5;
  return { latitude: f.latitude, longitude: f.longitude, bearingDeg: moving ? f.bearingDeg : null, timeMs: f.timeMs };
}
function offset(a, b) {
  return [(b.latitude - a.latitude) * M_PER_DEG,
          (b.longitude - a.longitude) * M_PER_DEG * Math.cos(a.latitude * Math.PI / 180)];
}
export function distance(a, b) { const [n, e] = offset(a, b); return Math.hypot(n, e); }
function delta(a, b) { return Math.abs(((a - b + 540) % 360) - 180); }

