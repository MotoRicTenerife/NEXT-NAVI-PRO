const TRAFFIC_TERMS = /\\b(traffico|trafico|traffic|atasco|incidente|retencion|congestion|coda|code|circulation|embouteillage|bouchon|verkehr|stau|transito|engarrafamento|verkeer|file)\\b/;

export function isTrafficQuery(message) {
  if (typeof message !== "string" || message.length > 2000) return false;
  const normalized = message.normalize("NFD").replace(/[\\u0300-\\u036f]/g, "").toLowerCase();
  return TRAFFIC_TERMS.test(normalized);
}
