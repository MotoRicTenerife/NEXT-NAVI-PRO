const CACHE_TTL_MS = 45_000;
const cache = new Map();

function validLocation(location) {
  return location && Number.isFinite(location.latitude) && location.latitude >= -90 && location.latitude <= 90 &&
    Number.isFinite(location.longitude) && location.longitude >= -180 && location.longitude <= 180;
}

function cacheKey(location) {
  return location.latitude.toFixed(3) + "," + location.longitude.toFixed(3);
}

function distanceKm(a, b) {
  const rad = value => value * Math.PI / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
}

async function requestJson(url, fetchImpl, label) {
  const response = await fetchImpl(url, {
    method: "GET",
    headers: { accept: "application/json", "user-agent": "NEXT-NAVI-PRO/0.4 (TomTom live traffic)" },
    signal: AbortSignal.timeout(9000)
  });
  if (!response.ok) throw new Error(label + "_http_" + response.status);
  return response.json();
}

function parseFlow(data) {
  const flow = data?.flowSegmentData;
  if (!flow || !Number.isFinite(flow.currentSpeed) || !Number.isFinite(flow.freeFlowSpeed)) {
    throw new Error("tomtom_flow_invalid_response");
  }
  const currentSpeedKmh = Math.round(flow.currentSpeed);
  const freeFlowSpeedKmh = Math.round(flow.freeFlowSpeed);
  const delayPercent = freeFlowSpeedKmh > 0
    ? Math.round((1 - currentSpeedKmh / freeFlowSpeedKmh) * 100)
    : null;
  let condition = "scorrevole";
  if (flow.roadClosure === true) condition = "strada chiusa";
  else if (currentSpeedKmh <= 5 && freeFlowSpeedKmh > 15) condition = "traffico quasi fermo";
  else if (delayPercent !== null && delayPercent >= 65) condition = "traffico molto intenso";
  else if (delayPercent !== null && delayPercent >= 35) condition = "traffico intenso";
  else if (delayPercent !== null && delayPercent >= 15) condition = "rallentamenti";
  return {
    currentSpeedKmh,
    freeFlowSpeedKmh,
    delayPercent,
    currentTravelTimeSeconds: Number.isFinite(flow.currentTravelTime) ? flow.currentTravelTime : null,
    freeFlowTravelTimeSeconds: Number.isFinite(flow.freeFlowTravelTime) ? flow.freeFlowTravelTime : null,
    confidence: Number.isFinite(flow.confidence) ? flow.confidence : null,
    roadClosure: flow.roadClosure === true,
    roadCondition: condition,
    frc: typeof flow.frc === "string" ? flow.frc : null
  };
}

function parseIncidents(data, location) {
  const incidents = Array.isArray(data?.incidents) ? data.incidents : [];
  const mapped = incidents.map(item => {
    const properties = item?.properties || {};
    const coordinates = item?.geometry?.coordinates;
    const points = item?.geometry?.type === "Point"
      ? [coordinates]
      : Array.isArray(coordinates) ? coordinates : [];
    const validPoints = points.filter(point =>
      Array.isArray(point) && Number.isFinite(point[0]) && Number.isFinite(point[1]));
    if (!validPoints.length) return null;
    const nearestKm = Math.min(...validPoints.map(point =>
      distanceKm(location, { latitude: point[1], longitude: point[0] })));
    return {
      id: typeof properties.id === "string" ? properties.id : null,
      category: Number.isFinite(properties.iconCategory) ? properties.iconCategory : null,
      severity: Number.isFinite(properties.magnitudeOfDelay) ? properties.magnitudeOfDelay : null,
      description: Array.isArray(properties.events)
        ? properties.events.map(event => event?.description).filter(value => typeof value === "string").join("; ").slice(0, 300)
        : "",
      road: [properties.roadNumbers?.join(" / "), properties.from, properties.to].filter(Boolean).join(" • ").slice(0, 180),
      delaySeconds: Number.isFinite(properties.delay) ? properties.delay : null,
      lengthMeters: Number.isFinite(properties.length) ? properties.length : null,
      startTime: properties.startTime || null,
      endTime: properties.endTime || null,
      distanceKm: Math.round(nearestKm * 10) / 10
    };
  }).filter(item => item && item.distanceKm <= 30)
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .slice(0, 12);
  return mapped;
}

/**
 * Query TomTom's live Flow Segment Data and Incident Details APIs.
 * Results are cached briefly because TomTom's non-tile quota is shared with speed-limit calls.
 */
export async function fetchTomTomTraffic(location, apiKey, fetchImpl = fetch) {
  if (!validLocation(location)) throw new Error("traffic_location_invalid");
  if (typeof apiKey !== "string" || !apiKey.trim()) throw new Error("tomtom_api_key_missing");

  const key = cacheKey(location);
  const cached = cache.get(key);
  if (cached && Date.now() - cached.cachedAt < CACHE_TTL_MS) {
    return { ...cached.value, cacheHit: true };
  }

  const flowUrl = new URL("https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json");
  flowUrl.search = new URLSearchParams({
    key: apiKey,
    point: location.latitude + "," + location.longitude,
    unit: "KMPH",
    thickness: "10"
  }).toString();

  // A roughly 30–35 km local window, well below TomTom's 10,000 km² bbox limit.
  const latDelta = 0.14;
  const lonDelta = Math.min(0.25, 0.14 / Math.max(0.2, Math.cos(location.latitude * Math.PI / 180)));
  const bbox = [
    location.longitude - lonDelta, location.latitude - latDelta,
    location.longitude + lonDelta, location.latitude + latDelta
  ].join(",");
  const incidentUrl = new URL("https://api.tomtom.com/traffic/services/5/incidentDetails");
  incidentUrl.search = new URLSearchParams({
    key: apiKey,
    bbox,
    fields: "{incidents{type,geometry{type,coordinates},properties{id,iconCategory,magnitudeOfDelay,events{description,code},startTime,endTime,from,to,length,delay,roadNumbers}}}",
    language: "it-IT",
    timeValidityFilter: "present"
  }).toString();

  const [flowOutcome, incidentsOutcome] = await Promise.allSettled([
    requestJson(flowUrl, fetchImpl, "tomtom_flow"),
    requestJson(incidentUrl, fetchImpl, "tomtom_incidents")
  ]);
  const flow = flowOutcome.status === "fulfilled" ? parseFlow(flowOutcome.value) : null;
  const incidents = incidentsOutcome.status === "fulfilled"
    ? parseIncidents(incidentsOutcome.value, location)
    : null;
  if (!flow && !incidents) {
    const reasons = [flowOutcome, incidentsOutcome]
      .filter(result => result.status === "rejected")
      .map(result => result.reason?.message || "provider_error");
    throw new Error(reasons.join(";").slice(0, 240) || "tomtom_traffic_unavailable");
  }

  const value = {
    source: "TomTom Traffic",
    fetchedAt: new Date().toISOString(),
    status: flow && incidents ? "complete" : "partial",
    flow,
    incidents,
    incidentCount: incidents?.length ?? null,
    cacheHit: false
  };
  cache.set(key, { cachedAt: Date.now(), value });
  if (cache.size > 500) {
    for (const [cacheId, entry] of cache) {
      if (Date.now() - entry.cachedAt >= CACHE_TTL_MS) cache.delete(cacheId);
    }
  }
  return value;
}

export function clearTrafficCacheForTests() {
  cache.clear();
}

export { parseFlow as parseTomTomFlow, parseIncidents as parseTomTomIncidents };
