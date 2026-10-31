const MAX_WAYPOINTS = 3;
const MAX_ROUTES = 3;

function validPoint(point) {
  return point && typeof point === "object" &&
    Number.isFinite(Number(point.latitude)) && Number(point.latitude) >= -90 && Number(point.latitude) <= 90 &&
    Number.isFinite(Number(point.longitude)) && Number(point.longitude) >= -180 && Number(point.longitude) <= 180;
}

export function validateTomTomRouteRequest(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) return null;
  const origin = input.origin;
  const destination = input.destination;
  const vehicleMode = input.vehicleMode === "motorcycle" ? "motorcycle" :
    input.vehicleMode === "car" ? "car" : "";
  const waypoints = Array.isArray(input.waypoints) ? input.waypoints : [];
  if (!validPoint(origin) || !validPoint(destination) || !vehicleMode ||
      waypoints.length > MAX_WAYPOINTS || !waypoints.every(validPoint)) return null;
  return {
    origin: { latitude: Number(origin.latitude), longitude: Number(origin.longitude) },
    destination: { latitude: Number(destination.latitude), longitude: Number(destination.longitude) },
    waypoints: waypoints.map(p => ({ latitude: Number(p.latitude), longitude: Number(p.longitude) })),
    vehicleMode
  };
}

export function buildTomTomRouteUrl(input, apiKey) {
  const request = validateTomTomRouteRequest(input);
  if (!request) throw new TypeError("Invalid TomTom route request");
  if (typeof apiKey !== "string" || !apiKey.trim()) throw new TypeError("TomTom API key is missing");
  const locations = [request.origin, ...request.waypoints, request.destination]
    .map(p => p.latitude.toFixed(6) + "," + p.longitude.toFixed(6)).join(":");
  const url = new URL("https://api.tomtom.com/routing/1/calculateRoute/" + locations + "/json");
  url.searchParams.set("key", apiKey.trim());
  url.searchParams.set("traffic", "true");
  url.searchParams.set("travelMode", request.vehicleMode);
  url.searchParams.set("routeType", "fastest");
  url.searchParams.set("maxAlternatives", "2");
  url.searchParams.set("alternativeType", "anyRoute");
  url.searchParams.set("computeTravelTimeFor", "all");
  url.searchParams.append("sectionType", "travelMode");
  url.searchParams.append("sectionType", "traffic");
  url.searchParams.set("report", "effectiveSettings");
  url.searchParams.set("language", "it-IT");
  return url;
}

function cleanSection(section) {
  if (!section || typeof section !== "object") return null;
  const sectionType = typeof section.sectionType === "string" ? section.sectionType.toUpperCase() : "";
  if (sectionType === "TRAVEL_MODE") {
    return {
      sectionType,
      travelMode: typeof section.travelMode === "string" ? section.travelMode.toLowerCase() : "",
      startPointIndex: Number.isInteger(section.startPointIndex) ? section.startPointIndex : null,
      endPointIndex: Number.isInteger(section.endPointIndex) ? section.endPointIndex : null
    };
  }
  if (sectionType !== "TRAFFIC") return null;
  return {
    sectionType,
    startPointIndex: Number.isInteger(section.startPointIndex) ? section.startPointIndex : null,
    endPointIndex: Number.isInteger(section.endPointIndex) ? section.endPointIndex : null,
    delayInSeconds: Number.isFinite(section.delayInSeconds) && section.delayInSeconds >= 0 ? Math.round(section.delayInSeconds) : null,
    magnitudeOfDelay: Number.isInteger(section.magnitudeOfDelay) ? section.magnitudeOfDelay : null,
    effectiveSpeedInKmh: Number.isFinite(section.effectiveSpeedInKmh) && section.effectiveSpeedInKmh >= 0 ? section.effectiveSpeedInKmh : null,
    simpleCategory: typeof section.simpleCategory === "string" ? section.simpleCategory.slice(0, 40) : null,
    eventId: typeof section.eventId === "string" ? section.eventId.slice(0, 100) : null
  };
}

export function normalizeTomTomTrafficRoutes(payload, vehicleMode, observedAtEpochMs = Date.now()) {
  if (!payload || typeof payload !== "object" || !Array.isArray(payload.routes) ||
      !["motorcycle", "car"].includes(vehicleMode) ||
      !Number.isSafeInteger(observedAtEpochMs) || observedAtEpochMs <= 0) {
    return { routes: [], rejectedRoutes: 0 };
  }
  const routes = [];
  let rejectedRoutes = 0;
  for (const [index, route] of payload.routes.entries()) {
    const summary = route?.summary;
    const sections = Array.isArray(route?.sections) ? route.sections.map(cleanSection).filter(Boolean) : [];
    const modeSections = sections.filter(s => s.sectionType === "TRAVEL_MODE");
    const legs = Array.isArray(route?.legs) ? route.legs : [];
    const points = [];
    for (const leg of legs) {
      for (const p of (Array.isArray(leg?.points) ? leg.points : [])) {
        if (validPoint(p)) points.push({ latitude: Number(p.latitude), longitude: Number(p.longitude) });
        if (points.length >= 500) break;
      }
      if (points.length >= 500) break;
    }
    const validSummary = Number.isFinite(summary?.lengthInMeters) && summary.lengthInMeters > 0 &&
      summary.lengthInMeters <= 5_000_000 &&
      Number.isInteger(summary?.travelTimeInSeconds) && summary.travelTimeInSeconds > 0 &&
      summary.travelTimeInSeconds <= 86400;
    const orderedModeSections = [...modeSections].sort((a, b) => (a.startPointIndex ?? -1) - (b.startPointIndex ?? -1));
    let coveredThrough = 0;
    let modeVerified = orderedModeSections.length > 0;
    for (const section of orderedModeSections) {
      if (section.travelMode !== vehicleMode || !Number.isInteger(section.startPointIndex) ||
          !Number.isInteger(section.endPointIndex) || section.startPointIndex > coveredThrough ||
          section.endPointIndex < section.startPointIndex) {
        modeVerified = false;
        break;
      }
      coveredThrough = Math.max(coveredThrough, section.endPointIndex + 1);
    }
    modeVerified = modeVerified && coveredThrough >= points.length - 1;
    if (!modeVerified || !validSummary || points.length < 2) {
      rejectedRoutes += 1;
      continue;
    }
    routes.push({
      routeId: index === 0 ? "primary" : "alternative-" + index,
      lengthInMeters: Math.round(summary.lengthInMeters),
      travelTimeInSeconds: summary.travelTimeInSeconds,
      trafficDelayInSeconds: Number.isFinite(summary.trafficDelayInSeconds) ? Math.max(0, Math.round(summary.trafficDelayInSeconds)) : null,
      noTrafficTravelTimeInSeconds: Number.isInteger(summary.noTrafficTravelTimeInSeconds) ? summary.noTrafficTravelTimeInSeconds : null,
      historicTrafficTravelTimeInSeconds: Number.isInteger(summary.historicTrafficTravelTimeInSeconds) ? summary.historicTrafficTravelTimeInSeconds : null,
      liveTrafficIncidentsTravelTimeInSeconds: Number.isInteger(summary.liveTrafficIncidentsTravelTimeInSeconds) ? summary.liveTrafficIncidentsTravelTimeInSeconds : null,
      trafficLengthInMeters: Number.isFinite(summary.trafficLengthInMeters) ? Math.max(0, Math.round(summary.trafficLengthInMeters)) : null,
      departureTime: typeof summary.departureTime === "string" ? summary.departureTime : null,
      arrivalTime: typeof summary.arrivalTime === "string" ? summary.arrivalTime : null,
      vehicleMode,
      travelModeVerified: true,
      trafficSections: sections.filter(s => s.sectionType === "TRAFFIC"),
      points,
      observedAtEpochMs
    });
    if (routes.length >= MAX_ROUTES) break;
  }
  return { routes, rejectedRoutes };
}

export async function fetchTomTomTrafficRoutes(input, {
  apiKey,
  fetchImpl = globalThis.fetch,
  now = Date.now()
} = {}) {
  const request = validateTomTomRouteRequest(input);
  if (!request) {
    const error = new Error("Invalid route request");
    error.statusCode = 400;
    throw error;
  }
  const url = buildTomTomRouteUrl(request, apiKey);
  const response = await fetchImpl(url, {
    method: "GET",
    headers: { accept: "application/json" },
    signal: AbortSignal.timeout(12_000)
  });
  if (!response.ok) {
    const error = new Error("TomTom Routing API unavailable");
    error.statusCode = response.status;
    throw error;
  }
  const payload = await response.json();
  const normalized = normalizeTomTomTrafficRoutes(payload, request.vehicleMode, now);
  return {
    ok: true,
    source: "TomTom Routing API v1",
    vehicleMode: request.vehicleMode,
    motorcycleModeBeta: request.vehicleMode === "motorcycle",
    trafficUsed: true,
    observedAtEpochMs: now,
    routes: normalized.routes,
    rejectedRoutes: normalized.rejectedRoutes,
    status: normalized.routes.length ? "available" : "vehicle_mode_unverified"
  };
}
