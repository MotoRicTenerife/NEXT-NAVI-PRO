const MAX_ZOOM = 22;

export function parseTrafficTilePath(pathname) {
  const match = /^\/traffic\/flow\/(\d{1,2})\/(\d{1,10})\/(\d{1,10})$/.exec(pathname);
  if (!match) return null;
  const z = Number(match[1]);
  const x = Number(match[2]);
  const y = Number(match[3]);
  if (!Number.isInteger(z) || z < 0 || z > MAX_ZOOM) return null;
  const tileCount = 2 ** z;
  if (!Number.isSafeInteger(x) || !Number.isSafeInteger(y) ||
      x < 0 || y < 0 || x >= tileCount || y >= tileCount) return null;
  return { z, x, y };
}

export function buildTomTomTrafficTileUrl({ z, x, y }) {
  const url = new URL(`https://api.tomtom.com/maps/orbis/traffic/flow/vector/tile/${z}/${x}/${y}`);
  url.searchParams.set("apiVersion", "2");
  return url;
}
