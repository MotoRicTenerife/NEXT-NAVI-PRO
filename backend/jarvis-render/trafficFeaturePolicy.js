/** Enable TomTom traffic routing by default when its server-side key exists.
 * An explicit "false" disables it; a missing flag no longer silently breaks Jarvis.
 */
export function isTomTomTrafficRoutingEnabled(flag, apiKey) {
  return Boolean(typeof apiKey === "string" && apiKey.trim()) && flag !== "false";
}
