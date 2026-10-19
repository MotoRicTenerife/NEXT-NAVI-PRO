export function parseHereSpeedLimit(body) {
  const root = JSON.parse(body);
  const response = root?.response || root;
  const routes = Array.isArray(response?.route) ? response.route : [];
  let lastLinkLimit = null;
  let sawLink = false;
  for (const route of routes) {
    for (const leg of Array.isArray(route?.leg) ? route.leg : []) {
      for (const link of Array.isArray(leg?.link) ? leg.link : []) {
        sawLink = true;
        lastLinkLimit = null;
        const attributes = link?.attributes;
        if (!attributes || typeof attributes !== "object") continue;
        for (const [key, value] of Object.entries(attributes)) {
          if (!key.startsWith("SPEED_LIMITS_FC") || !Array.isArray(value)) continue;
          for (const item of value) {
            const parse = field => {
              const raw = item?.[field];
              if (raw === null || raw === undefined || String(raw).trim() === "") return null;
              const numeric = Number(raw);
              if (!Number.isFinite(numeric) || numeric <= 0) return null;
              const unit = String(item.UNIT ?? item.SPEED_LIMIT_UNIT ?? "K").toUpperCase();
              const kmh = unit === "M" || unit.includes("MPH") ? numeric * 1.609344 : numeric;
              const rounded = Math.round(kmh);
              return rounded >= 5 && rounded <= 200 ? rounded : null;
            };
            const from = parse("FROM_REF_SPEED_LIMIT");
            const to = parse("TO_REF_SPEED_LIMIT");
            if (from !== null && to !== null && from === to) {
              lastLinkLimit = from;
              break;
            }
          }
          if (lastLinkLimit !== null) break;
        }
      }
    }
  }
  return sawLink ? lastLinkLimit : null;
}
