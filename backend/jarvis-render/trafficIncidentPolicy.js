const MAX_BBOX_AREA_KM2 = 10_000;

export function parseIncidentBbox(raw) {
  if (typeof raw !== "string") return null;
  const values = raw.split(",").map(value => Number(value.trim()));
  if (values.length !== 4 || values.some(value => !Number.isFinite(value))) return null;
  const [minLon, minLat, maxLon, maxLat] = values;
  if (minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90 ||
      minLon >= maxLon || minLat >= maxLat) return null;

  // Conservative local equirectangular estimate. The API limit is 10,000 km²;
  // reject oversized boxes before spending a provider request.
  const heightKm = (maxLat - minLat) * 111.32;
  const widthKm = (maxLon - minLon) * 111.32 *
    Math.max(0.01, Math.cos(((minLat + maxLat) / 2) * Math.PI / 180));
  const areaKm2 = heightKm * widthKm;
  if (!Number.isFinite(areaKm2) || areaKm2 <= 0 || areaKm2 > MAX_BBOX_AREA_KM2) return null;

  return {
    minLon, minLat, maxLon, maxLat,
    areaKm2,
    value: [minLon, minLat, maxLon, maxLat].map(value => Number(value.toFixed(6))).join(",")
  };
}

export function buildTomTomIncidentDetailsUrl(bbox) {
  const url = new URL("https://api.tomtom.com/maps/orbis/traffic/incidents/details");
  url.searchParams.set("apiVersion", "2");
  url.searchParams.set("bbox", bbox.value);
  url.searchParams.set("timeValidity", "present");
  return url;
}
