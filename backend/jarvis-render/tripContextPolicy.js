const MAX_ROUTE_AGE_MS = 30 * 60 * 1000;
const MAX_INCIDENT_AGE_MS = 5 * 60 * 1000;

function cleanPoint(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const latitude = Number(value.latitude);
  const longitude = Number(value.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
      !Number.isFinite(longitude) || longitude < -180 || longitude > 180) return null;
  return { latitude, longitude };
}

function cleanText(value, max = 180) {
  return typeof value === "string" ? value.replace(/[<>\r\n]/g, " ").replace(/\s+/g, " ").trim().slice(0, max) : "";
}

/** Accept only bounded, timestamped route evidence from the app. */
export function sanitizeTripContext(raw, now = Date.now()) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw) || !Number.isFinite(now) || now <= 0) return null;
  const destinationName = cleanText(raw.destinationName, 120);
  const vehicleMode = raw.vehicleMode === "motorcycle" ? "motorcycle" : raw.vehicleMode === "car" ? "car" : "";
  const distanceKm = Number(raw.distanceKm);
  const durationSeconds = Number(raw.durationSeconds);
  const fetchedAtEpochMs = Number(raw.fetchedAtEpochMs);
  const trafficUnavailable = raw.trafficUnavailable === true;
  const trafficObservedAtEpochMs = Number(raw.trafficObservedAtEpochMs);
  const routeSource = cleanText(raw.routeSource, 80);
  const routeEstimateType = cleanText(raw.routeEstimateType, 100);
  const trafficSource = cleanText(raw.trafficSource, 100);
  const trafficCache = raw.trafficCache === "HIT" || raw.trafficCache === "MISS" ? raw.trafficCache : "UNKNOWN";
  const trafficEvidenceValid = !trafficUnavailable &&
    trafficSource.includes("TomTom Orbis Traffic Incidents") &&
    Number.isSafeInteger(trafficObservedAtEpochMs) && trafficObservedAtEpochMs > 0 &&
    trafficObservedAtEpochMs <= now + 30_000 && now - trafficObservedAtEpochMs <= MAX_INCIDENT_AGE_MS;
  const viaRoadName = cleanText(raw.viaRoadName, 80) || null;
  let baselineRoute = null;
  if (raw.baselineRoute && typeof raw.baselineRoute === "object" && !Array.isArray(raw.baselineRoute)) {
    const baselineDistanceKm = Number(raw.baselineRoute.distanceKm);
    const baselineDurationSeconds = Number(raw.baselineRoute.durationSeconds);
    const baselineSource = cleanText(raw.baselineRoute.routeSource, 80);
    if (Number.isFinite(baselineDistanceKm) && baselineDistanceKm > 0 && baselineDistanceKm <= 2000 &&
        Number.isInteger(baselineDurationSeconds) && baselineDurationSeconds > 0 && baselineDurationSeconds <= 86400 &&
        baselineSource === routeSource) {
      baselineRoute = { distanceKm: Math.round(baselineDistanceKm * 10) / 10, durationSeconds: baselineDurationSeconds, routeSource: baselineSource };
    }
  }
  let baselineRouteRequest = null;
  if (raw.baselineRouteRequest && typeof raw.baselineRouteRequest === "object" && !Array.isArray(raw.baselineRouteRequest)) {
    const origin = cleanPoint(raw.baselineRouteRequest.origin);
    const destination = cleanPoint(raw.baselineRouteRequest.destination);
    const requestMode = raw.baselineRouteRequest.vehicleMode;
    if (origin && destination && requestMode === vehicleMode) {
      baselineRouteRequest = { origin, destination, waypoints: [], vehicleMode };
    }
  }
  let routeRequest = null;
  if (raw.routeRequest && typeof raw.routeRequest === "object" && !Array.isArray(raw.routeRequest)) {
    const origin = cleanPoint(raw.routeRequest.origin);
    const destination = cleanPoint(raw.routeRequest.destination);
    const waypoints = Array.isArray(raw.routeRequest.waypoints) ? raw.routeRequest.waypoints : [];
    const requestMode = raw.routeRequest.vehicleMode;
    if (origin && destination && requestMode === vehicleMode && waypoints.length <= 3) {
      const cleanWaypoints = waypoints.map(cleanPoint);
      if (cleanWaypoints.every(Boolean)) routeRequest = { origin, destination, waypoints: cleanWaypoints, vehicleMode };
    }
  }
  if (!destinationName || !vehicleMode ||
      !Number.isFinite(distanceKm) || distanceKm <= 0 || distanceKm > 2000 ||
      !Number.isInteger(durationSeconds) || durationSeconds <= 0 || durationSeconds > 86400 ||
      !Number.isSafeInteger(fetchedAtEpochMs) || fetchedAtEpochMs <= 0 ||
      fetchedAtEpochMs > now + 30_000 || now - fetchedAtEpochMs > MAX_ROUTE_AGE_MS ||
      !["Valhalla motorcycle", "Valhalla auto"].includes(routeSource) ||
      routeEstimateType !== "baseline_route_engine_estimate_not_traffic_adjusted" ||
      (!trafficUnavailable && !trafficEvidenceValid)) return null;

  const incidents = [];
  if (!trafficUnavailable && Array.isArray(raw.incidents)) {
    for (const item of raw.incidents.slice(0, 20)) {
      if (!item || typeof item !== "object" || Array.isArray(item)) continue;
      const id = cleanText(item.id, 100);
      const severity = ["MODERATE", "HEAVY", "CLOSED"].includes(item.severity) ? item.severity : "";
      const distanceAheadMeters = Number(item.distanceAheadMeters);
      const observedAtEpochMs = Number(item.observedAtEpochMs);
      const validUntilEpochMs = Number(item.validUntilEpochMs);
      if (!id || !severity ||
          !Number.isFinite(distanceAheadMeters) || distanceAheadMeters < 0 || distanceAheadMeters > 500_000 ||
          !Number.isSafeInteger(observedAtEpochMs) || observedAtEpochMs <= 0 ||
          observedAtEpochMs > now + 30_000 || now - observedAtEpochMs > MAX_INCIDENT_AGE_MS ||
          !Number.isSafeInteger(validUntilEpochMs) || validUntilEpochMs < now) continue;
      incidents.push({
        id,
        severity,
        distanceAheadMeters: Math.round(distanceAheadMeters),
        delaySeconds: Number.isInteger(item.delaySeconds) && item.delaySeconds >= 0 && item.delaySeconds <= 86400 ? item.delaySeconds : null,
        road: cleanText(item.road, 100) || null,
        description: cleanText(item.description, 240) || null,
        observedAtEpochMs,
        validUntilEpochMs,
        geometryFitScore: Number.isFinite(item.geometryFitScore) ? Math.max(0, Math.min(1, item.geometryFitScore)) : null,
        directionVerified: false
      });
    }
  }

  return {
    destinationName,
    vehicleMode,
    distanceKm: Math.round(distanceKm * 10) / 10,
    durationSeconds,
    fetchedAtEpochMs,
    routeSource,
    routeEstimateType,
    trafficSource,
    trafficObservedAtEpochMs,
    trafficCache,
    routeRequest,
    baselineRouteRequest,
    viaRoadName,
    baselineRoute,
    routeFresh: true,
    trafficFresh: trafficEvidenceValid,
    trafficUnavailable,
    incidents,
    incidentEvidence: trafficUnavailable ? "incident_service_unavailable" : incidents.length ? "fresh_route_corridor_matches_direction_unverified" : "no_fresh_route_corridor_matches"
  };
}
