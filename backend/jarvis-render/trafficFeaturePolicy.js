/** Enable TomTom traffic features by default when the server-side key exists.
 * An explicit "false" disables a feature; missing flags no longer silently disable it.
 * Keep credentials server-side. Per-client admission limits and shared short caches
 * are enforced by server.js before any provider request.
 */
function hasTomTomKey(apiKey) {
  return typeof apiKey === "string" && apiKey.trim().length > 0;
}

function isEnabled(flag, apiKey) {
  return hasTomTomKey(apiKey) && flag !== "false";
}

export function isTomTomTrafficFlowEnabled(flag, apiKey) {
  return isEnabled(flag, apiKey);
}

export function isTomTomTrafficIncidentsEnabled(flag, apiKey) {
  return isEnabled(flag, apiKey);
}

export function isTomTomTrafficSegmentEnabled(flag, apiKey) {
  return isEnabled(flag, apiKey);
}

export function isTomTomTrafficRoutingEnabled(flag, apiKey) {
  return isEnabled(flag, apiKey);
}
