/**
 * Select the response path for Jarvis.
 *
 * Verified live provider data is context for Gemini, not a reason to bypass it.
 * A deterministic response is permitted only when the conversational model is
 * unavailable; otherwise Gemini must synthesize the live facts in context.
 */
export function selectJarvisResponseMode({ apiKey, hasLiveIntent = false, hasVerifiedLiveData = false } = {}) {
  const configured = typeof apiKey === "string" && apiKey.trim().length > 0;
  if (configured) return "gemini";
  if (hasLiveIntent && hasVerifiedLiveData) return "verified_live_fallback";
  return "unavailable";
}
