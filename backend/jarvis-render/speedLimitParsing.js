/**
 * Conservative parser for explicit maximum speed limits returned by TomTom.
 * Unknown units, non-maximum limits, ambiguous values and implausible values
 * are excluded instead of being guessed.
 */
export function parseExplicitMaximumSpeedLimits(rawLimits) {
  const values = Array.isArray(rawLimits) ? rawLimits : rawLimits ? [rawLimits] : [];
  return values.map(limit => {
    const value = Number(limit?.value);
    if (!Number.isFinite(value) || value <= 0 ||
        String(limit?.type || "").trim().toLowerCase() !== "maximum") return null;

    const unit = String(limit?.unit || "").trim().toLowerCase();
    let kmh;
    if (["kmph", "km/h", "kmh", "kph", "kilometersperhour", "kilometresperhour"].includes(unit)) {
      kmh = value;
    } else if (["mph", "mi/h", "milesperhour", "mileperhour"].includes(unit)) {
      kmh = value * 1.609344;
    } else {
      return null;
    }

    const rounded = Math.round(kmh);
    if (!Number.isFinite(rounded) || rounded < 5 || rounded > 200) return null;
    return { kmh: rounded, rawValue: value, unit, type: "Maximum" };
  }).filter(value => value !== null);
}
