const LANGUAGE_TAG = /^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/;

/** Resolve a bounded BCP-47 language tag from the app payload or HTTP preference. */
export function normalizeLanguageTag(value, acceptLanguage = "en-GB") {
  const candidates = [value, ...String(acceptLanguage || "").split(",").map(part => part.split(";")[0].trim())];
  for (const candidate of candidates) {
    if (typeof candidate !== "string") continue;
    const tag = candidate.trim().replace(/_/g, "-").slice(0, 35);
    if (tag.length <= 35 && LANGUAGE_TAG.test(tag)) return tag;
  }
  return "en-GB";
}
