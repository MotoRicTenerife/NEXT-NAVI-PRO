/** Parse a way returned by an Overpass API response. Returns null for incomplete geometry. */
export function parseOverpassWayGeometry(data, osmId) {
  const way = Array.isArray(data?.elements)
    ? data.elements.find(item => item?.type === "way" && Number(item.id) === Number(osmId))
    : null;
  if (!way || !Array.isArray(way.geometry)) return null;
  const geometry = way.geometry
    .filter(point => Number.isFinite(point?.lat) && Number.isFinite(point?.lon))
    .map(point => ({ latitude: point.lat, longitude: point.lon }));
  return geometry.length >= 2 ? { way, geometry } : null;
}

/**
 * Parse the official OSM API /way/{id}/full.json response.
 * The response contains the way's ordered node IDs plus the corresponding node coordinates.
 */
export function parseOsmApiFullWayGeometry(data, osmId) {
  const elements = Array.isArray(data?.elements) ? data.elements : [];
  const way = elements.find(item => item?.type === "way" && Number(item.id) === Number(osmId));
  if (!way || !Array.isArray(way.nodes)) return null;
  const nodesById = new Map(
    elements
      .filter(item => item?.type === "node" && Number.isSafeInteger(Number(item.id)) &&
        Number.isFinite(item.lat) && Number.isFinite(item.lon))
      .map(item => [Number(item.id), { latitude: item.lat, longitude: item.lon }])
  );
  const geometry = way.nodes
    .map(nodeId => nodesById.get(Number(nodeId)))
    .filter(point => point && Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
  return geometry.length >= 2 ? { way, geometry } : null;
}
