const MAX_ZOOM = 18;

export function validateTrafficPoint(point) {
  if (!point || typeof point !== "object" || Array.isArray(point)) return null;
  const latitude = Number(point.latitude);
  const longitude = Number(point.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
      !Number.isFinite(longitude) || longitude < -180 || longitude > 180) return null;
  return { latitude, longitude };
}

export function buildTomTomTrafficSegmentUrl(point, apiKey, zoom = 15) {
  const normalized = validateTrafficPoint(point);
  if (!normalized) throw new TypeError("Invalid traffic segment point");
  if (typeof apiKey !== "string" || !apiKey.trim()) throw new TypeError("TomTom API key is missing");
  if (!Number.isInteger(zoom) || zoom < 0 || zoom > MAX_ZOOM) throw new TypeError("Invalid traffic segment zoom");
  const url = new URL(`https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/${zoom}/json`);
  url.searchParams.set("key", apiKey.trim());
  url.searchParams.set("point", normalized.latitude.toFixed(6) + "," + normalized.longitude.toFixed(6));
  url.searchParams.set("unit", "kmph");
  return url;
}

export function normalizeTomTomTrafficSegment(payload, observedAtEpochMs = Date.now()) {
  const data = payload?.flowSegmentData;
  const currentSpeedKmh = Number(data?.currentSpeed);
  const freeFlowSpeedKmh = Number(data?.freeFlowSpeed);
  const currentTravelTimeSeconds = Number(data?.currentTravelTime);
  const freeFlowTravelTimeSeconds = Number(data?.freeFlowTravelTime);
  const confidence = Number(data?.confidence);
  if (!data || !Number.isFinite(currentSpeedKmh) || currentSpeedKmh < 0 || currentSpeedKmh > 300 ||
      !Number.isFinite(freeFlowSpeedKmh) || freeFlowSpeedKmh <= 0 || freeFlowSpeedKmh > 300 ||
      !Number.isFinite(currentTravelTimeSeconds) || currentTravelTimeSeconds < 0 ||
      !Number.isFinite(freeFlowTravelTimeSeconds) || freeFlowTravelTimeSeconds <= 0 ||
      !Number.isFinite(confidence) || confidence < 0 || confidence > 1 ||
      !Number.isSafeInteger(observedAtEpochMs) || observedAtEpochMs <= 0) {
    return null;
  }
  return {
    source: "TomTom Traffic Flow Segment Data",
    currentSpeedKmh: Math.round(currentSpeedKmh),
    freeFlowSpeedKmh: Math.round(freeFlowSpeedKmh),
    currentTravelTimeSeconds: Math.round(currentTravelTimeSeconds),
    freeFlowTravelTimeSeconds: Math.round(freeFlowTravelTimeSeconds),
    relativeSpeed: Math.max(0, Math.min(1.5, currentSpeedKmh / freeFlowSpeedKmh)),
    confidence,
    roadClosure: data.roadClosure === true,
    roadClass: typeof data.frc === "string" ? data.frc.slice(0, 12) : null,
    observedAtEpochMs
  };
}

export async function fetchTomTomTrafficSegment(point, {
  apiKey,
  fetchImpl = globalThis.fetch,
  now = Date.now(),
  zoom = 15
} = {}) {
  const normalized = validateTrafficPoint(point);
  if (!normalized) {
    const error = new Error("Invalid traffic segment point");
    error.statusCode = 400;
    throw error;
  }
  const url = buildTomTomTrafficSegmentUrl(normalized, apiKey, zoom);
  let response;
  try {
    response = await fetchImpl(url, {
      method: "GET",
      headers: { accept: "application/json", "user-agent": "NEXT-NAVI-PRO/0.3 (TomTom traffic segment)" },
      signal: AbortSignal.timeout(9000)
    });
  } catch (cause) {
    const error = new Error("TomTom traffic segment request failed");
    error.statusCode = 502;
    error.cause = cause;
    throw error;
  }
  if (!response.ok) {
    const error = new Error("TomTom traffic segment HTTP " + response.status);
    error.statusCode = response.status === 429 ? 429 : response.status === 403 ? 403 : 502;
    throw error;
  }
  let payload;
  try {
    payload = await response.json();
  } catch {
    const error = new Error("TomTom traffic segment response is not valid JSON");
    error.statusCode = 502;
    throw error;
  }
  const result = normalizeTomTomTrafficSegment(payload, now);
  if (!result) {
    const error = new Error("TomTom traffic segment response is missing valid speed fields");
    error.statusCode = 502;
    throw error;
  }
  return result;
}
