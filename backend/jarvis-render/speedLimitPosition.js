const MIN_LIMIT_KMH = 5;
const MAX_LIMIT_KMH = 200;

export function buildTomTomPositionSpeedLimitUrl({ latitude, longitude, apiKey, heading }) {
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
      !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new TypeError("Valid latitude and longitude are required");
  }
  if (typeof apiKey !== "string" || !apiKey.trim()) {
    throw new TypeError("A TomTom API key is required");
  }
  const position = `${latitude},${longitude}`;
  const query = new URLSearchParams({
    key: apiKey,
    returnSpeedLimit: "true",
    radius: "50"
  });
  if (Number.isFinite(heading) && heading >= 0 && heading < 360) {
    query.set("heading", String(heading));
  }
  return `https://api.tomtom.com/search/2/reverseGeocode/${position}.json?${query.toString()}`;
}

function parseSpeedLimitKmh(raw) {
  const match = String(raw ?? "").trim().toUpperCase().match(/^(\d+(?:\.\d+)?)\s*(KPH|KMH|MPH)$/);
  if (!match) return null;
  const value = Number(match[1]);
  const kmh = match[2] === "MPH" ? value * 1.609344 : value;
  const rounded = Math.round(kmh);
  return Number.isFinite(rounded) && rounded >= MIN_LIMIT_KMH && rounded <= MAX_LIMIT_KMH
    ? rounded
    : null;
}

export function parseTomTomPositionSpeedLimit(data) {
  const result = Array.isArray(data?.addresses) ? data.addresses[0] : null;
  const address = result?.address || {};
  const speedLimitKmh = parseSpeedLimitKmh(address.speedLimit);
  const road = typeof address.streetName === "string" && address.streetName.trim()
    ? address.streetName.trim()
    : typeof address.street === "string" && address.street.trim()
      ? address.street.trim()
      : null;
  const routeNumbers = Array.isArray(address.routeNumbers)
    ? address.routeNumbers.filter(value => typeof value === "string" && value.trim())
    : [];
  return {
    speedLimitKmh,
    road,
    ref: routeNumbers.length ? routeNumbers.join(" / ") : null,
    matchType: typeof result?.matchType === "string" ? result.matchType : null,
    reason: speedLimitKmh === null
      ? (result ? "no_explicit_speed_limit" : "no_matching_road")
      : null
  };
}
