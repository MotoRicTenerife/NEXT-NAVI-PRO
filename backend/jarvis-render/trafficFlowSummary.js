/**
 * Turn one verified TomTom nearest-road flow segment into a cautious spoken summary.
 * This describes only the matched segment, never the whole surrounding road network.
 */
export function summarizeTomTomTrafficFlow(segment) {
  if (!segment || typeof segment !== "object") return null;
  const current = Number(segment.currentSpeedKmh);
  const freeFlow = Number(segment.freeFlowSpeedKmh);
  const confidence = Number(segment.confidence);
  if (!Number.isFinite(current) || current < 0 || current > 300 ||
      !Number.isFinite(freeFlow) || freeFlow <= 0 || freeFlow > 300) return null;

  const ratio = current / freeFlow;
  const condition = segment.roadClosure === true ? "chiusura segnalata"
    : ratio >= 0.9 ? "scorrevole"
    : ratio >= 0.7 ? "rallentato"
    : ratio >= 0.4 ? "congestionato"
    : "fortemente congestionato";
  const confidenceText = Number.isFinite(confidence)
    ? confidence >= 0.8 ? "confidenza alta"
      : confidence >= 0.5 ? "confidenza media"
      : "confidenza bassa"
    : "confidenza non disponibile";
  const difference = Math.round(Math.abs(current - freeFlow));
  const comparison = current <= freeFlow
    ? `${difference} km/h sotto la velocità a flusso libero`
    : `${difference} km/h sopra la velocità a flusso libero`;
  return `Flusso TomTom sulla strada più vicina al GPS: traffico ${condition}, velocità attuale ${Math.round(current)} km/h contro ${Math.round(freeFlow)} km/h a flusso libero (${comparison}; ${confidenceText}). È una misura del solo segmento stradale abbinato, non di tutte le strade vicine.`;
}
