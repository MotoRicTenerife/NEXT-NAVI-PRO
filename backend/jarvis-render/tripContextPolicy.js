const MAX_ROUTE_AGE_MS = 30 * 60 * 1000;
const MAX_INCIDENT_AGE_MS = 5 * 60 * 1000;

function cleanText(value, max = 180) {
  return typeof value === "string" ? value.replace(/[<>\r\n]/g, " ").replace(/\s+/g, " ").trim().slice(0, max) : "";
}

/**
 * Accept only bounded, timestamped route evidence from the app.
 * Geometry/direction are not trusted as proof of legal access or carriageway.
 */
export function sanitizeTripContext(raw, now = Date.now()) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw) || !Number.isFinite(now) || now <= 0) return null;
  const destinationName = cleanText(raw.destinationName, 120);
  const vehicleMode = raw.vehicleMode === "motorcycle" ? "motorcycle" : raw.vehicleMode === "car" ? "car" : "";
  const distanceKm = Number(raw.distanceKm);
  const durationSeconds = Number(raw.durationSeconds);
  const fetchedAtEpochMs = Number(raw.fetchedAtEpochMs);
  const trafficObservedAtEpochMs = Number(raw.trafficObservedAtEpochMs);
  const routeSource = cleanText(raw.routeSource, 80);
  const routeEstimateType = cleanText(raw.routeEstimateType, 100);
  const trafficSource = cleanText(raw.trafficSource, 100);
  const trafficCache = raw.trafficCache === "HIT" || raw.trafficCache === "MISS" ? raw.trafficCache : "UNKNOWN";
  if (!destinationName || !vehicleMode ||
      !Number.isFinite(distanceKm) || distanceKm <= 0 || distanceKm > 2000 ||
      !Number.isInteger(durationSeconds) || durationSeconds <= 0 || durationSeconds > 86400 ||
      !Number.isSafeInteger(fetchedAtEpochMs) || fetchedAtEpochMs <= 0 ||
      fetchedAtEpochMs > now + 30_000 || now - fetchedAtEpochMs > MAX_ROUTE_AGE_MS ||
      !["Valhalla motorcycle", "Valhalla auto"].includes(routeSource) ||
      routeEstimateType !== "baseline_route_engine_estimate_not_traffic_adjusted" ||
      !trafficSource.includes("TomTom Orbis Traffic Incidents") ||
      !Number.isSafeInteger(trafficObservedAtEpochMs) || trafficObservedAtEpochMs <= 0 ||
      trafficObservedAtEpochMs > now + 30_000 || now - trafficObservedAtEpochMs > MAX_INCIDENT_AGE_MS) return null;

  const incidents = [];
  if (Array.isArray(raw.incidents)) {
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
        // The current matcher explicitly does not verify travel direction.
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
    routeFresh: true,
    trafficFresh: true,
    incidents,
    incidentEvidence: incidents.length ? "fresh_route_corridor_matches_direction_unverified" : "no_fresh_route_corridor_matches"
  };
}
