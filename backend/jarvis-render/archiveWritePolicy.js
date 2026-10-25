/**
 * Shared speed-limit archive write policy.
 *
 * IMPORTANT: this intentionally blocks mutations until the app and backend
 * perform real Firebase ID-token authentication. A public APK must never hold
 * a shared server secret, and IP-based rate limiting is not authentication.
 */
export function isUnauthenticatedArchiveWrite(method, pathname) {
  return (
    (method === "POST" && pathname === "/speed-limit-segments/upsert") ||
    (method === "DELETE" && pathname === "/speed-limit-segments")
  );
}
