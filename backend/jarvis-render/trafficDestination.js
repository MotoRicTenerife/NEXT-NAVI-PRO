const MAX_DESTINATION_CHARS = 120;

function normalizeName(value) {
  return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().replace(/[^a-z0-9 ]/g, " ").replace(/\s+/g, " ").trim();
}

function cleanCandidate(value) {
  return String(value || "").replace(/[<>\r\n]/g, " ").replace(/\s+/g, " ")
    .replace(/[?.!,;:]+$/g, "").trim().slice(0, MAX_DESTINATION_CHARS);
}

/** Extract only an explicitly spoken destination; never infer one from chat history. */
export function extractTrafficDestination(message) {
  if (typeof message !== "string" || message.length > 1000) return "";
  const patterns = [
    /\bda qui\s+(?:fino\s+)?a\s+(.+?)(?=[?.!,;]|$)/i,
    /\b(?:per andare|devo andare|vorrei andare|voglio andare|sto andando|andare|raggiungere|arrivare)\s+(?:da qui\s+)?(?:a|ad|al|alla|allo|ai|agli|alle)\s+(.+?)(?=[?.!,;]|$)/i,
    /\bverso\s+(.+?)(?=[?.!,;]|$)/i
  ];
  for (const pattern of patterns) {
    const match = message.match(pattern);
    if (!match) continue;
    const rawCandidate = String(match[1] || "").replace(/\\s+/g, " ").trim();
    if (rawCandidate.length > MAX_DESTINATION_CHARS) return "";
    const candidate = cleanCandidate(rawCandidate);
    if (candidate.length >= 2 && !/^(qui|casa mia|me)$/i.test(candidate)) return candidate;
  }
  return "";
}

function distanceKm(a, b) {
  const rad = value => value * Math.PI / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const x = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(Math.max(0, 1 - x)));
}

function validPoint(point) {
  return point && Number.isFinite(point.latitude) && point.latitude >= -90 && point.latitude <= 90 &&
    Number.isFinite(point.longitude) && point.longitude >= -180 && point.longitude <= 180;
}

/** Resolve a destination near the live GPS fix; reject ambiguous distant geocoder results. */
export async function geocodeTrafficDestination(destination, origin, {
  fetchImpl = globalThis.fetch
} = {}) {
  const query = cleanCandidate(destination);
  if (query.length < 2 || !validPoint(origin)) return null;
  const url = new URL("https://photon.komoot.io/api/");
  url.search = new URLSearchParams({
    q: query,
    lat: String(origin.latitude),
    lon: String(origin.longitude),
    limit: "8"
  }).toString();
  const response = await fetchImpl(url, {
    method: "GET",
    headers: {
      accept: "application/geo+json, application/json",
      "user-agent": "NEXT-NAVI-PRO/0.3 (Render traffic destination lookup)"
    },
    signal: AbortSignal.timeout(9000)
  });
  if (!response.ok) throw new Error("Destination geocoder HTTP " + response.status);
  const payload = await response.json();
  const features = Array.isArray(payload?.features) ? payload.features : [];
  const candidates = features.flatMap(feature => {
    const coordinates = feature?.geometry?.coordinates;
    const properties = feature?.properties || {};
    if (!Array.isArray(coordinates) || coordinates.length < 2) return [];
    const point = { latitude: Number(coordinates[1]), longitude: Number(coordinates[0]) };
    if (!validPoint(point)) return [];
    const country = normalizeName(properties.country);
    if (country && country !== "spain" && country !== "espana") return [];
    const name = cleanCandidate(properties.name || properties.city || properties.locality || properties.street);
    if (!name) return [];
    const distance = distanceKm(origin, point);
    if (distance > 150) return [];
    const exact = normalizeName(name) === normalizeName(query);
    const city = cleanCandidate(properties.city || properties.county || properties.state);
    return [{
      point,
      name,
      displayName: [name, city, properties.state, properties.country].filter(Boolean).join(", ").slice(0, 220),
      distanceKm: Math.round(distance * 10) / 10,
      score: distance + (exact ? -1000 : 0)
    }];
  }).sort((a, b) => a.score - b.score);
  if (!candidates.length) return null;
  const best = candidates[0];
  return { point: best.point, name: best.name, displayName: best.displayName, distanceKm: best.distanceKm };
}
