#!/usr/bin/env python3
"""Repeatable, offline quality report for NEXT NAVI PRO GPS diagnostic CSV + route GPX."""
import argparse
import csv
import json
import math
import statistics
import xml.etree.ElementTree as ET
from pathlib import Path


def haversine_m(a, b):
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6_371_000 * 2 * math.asin(math.sqrt(min(1.0, max(0.0, h))))


def read_route_gpx(path):
    root = ET.parse(path).getroot()
    points = []
    for node in root.iter():
        if node.tag.rsplit("}", 1)[-1].lower() in ("trkpt", "rtept"):
            try:
                lat, lon = float(node.attrib["lat"]), float(node.attrib["lon"])
            except (KeyError, ValueError):
                continue
            if math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180:
                points.append((lat, lon))
    if len(points) < 2:
        raise ValueError("Il GPX di riferimento deve contenere almeno due punti.")
    return points


def read_trace_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream))
    required = {"timestamp_epoch_ms", "latitude", "longitude", "accuracy_m"}
    if not rows or not required.issubset(rows[0]):
        raise ValueError("CSV diagnostico NEXT NAVI PRO non riconosciuto o vuoto.")
    samples = []
    for row in rows:
        try:
            timestamp = int(row["timestamp_epoch_ms"])
            lat, lon = float(row["latitude"]), float(row["longitude"])
            accuracy = float(row["accuracy_m"]) if row.get("accuracy_m") else math.nan
        except (TypeError, ValueError):
            continue
        if timestamp <= 0 or not all(map(math.isfinite, (lat, lon))) or not (-90 <= lat <= 90 and -180 <= lon <= 180):
            continue
        samples.append({"time": timestamp, "lat": lat, "lon": lon, "accuracy": accuracy, "row": row})
    if not samples:
        raise ValueError("Il CSV non contiene fix GPS validi.")
    return samples


def point_to_segment_m(point, a, b):
    # Local tangent-plane projection; accurate enough for adjacent road geometry segments.
    lat0 = math.radians(point[0])
    scale_x = 111_320.0 * math.cos(lat0)
    scale_y = 111_132.0
    ax, ay = (a[1] - point[1]) * scale_x, (a[0] - point[0]) * scale_y
    bx, by = (b[1] - point[1]) * scale_x, (b[0] - point[0]) * scale_y
    dx, dy = bx - ax, by - ay
    length2 = dx * dx + dy * dy
    if length2 < 0.01:
        return math.hypot(ax, ay)
    fraction = max(0.0, min(1.0, -(ax * dx + ay * dy) / length2))
    return math.hypot(ax + fraction * dx, ay + fraction * dy)


def nearest_route_distance_m(point, route):
    return min(point_to_segment_m(point, route[i], route[i + 1]) for i in range(len(route) - 1))


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1)], 2)


def summarize_trace(samples, route):
    cross_track = [nearest_route_distance_m((s["lat"], s["lon"]), route) for s in samples]
    accuracy = [s["accuracy"] for s in samples if math.isfinite(s["accuracy"])]
    app_distances = []
    app_matches = 0
    intervals = []
    implausible_jumps = 0
    for i, sample in enumerate(samples):
        row = sample["row"]
        try:
            d = float(row.get("route_distance_m", ""))
            if math.isfinite(d):
                app_distances.append(d)
        except (TypeError, ValueError):
            pass
        if row.get("match_status") == "MATCHED":
            app_matches += 1
        if i:
            dt = (sample["time"] - samples[i - 1]["time"]) / 1000.0
            if dt > 0:
                intervals.append(dt)
                speed = haversine_m((samples[i-1]["lat"], samples[i-1]["lon"]), (sample["lat"], sample["lon"])) / dt
                if speed > 55.0:
                    implausible_jumps += 1
    n = len(samples)
    return {
        "samples": n,
        "app_matched_percent": round(100.0 * app_matches / n, 1),
        "independent_cross_track_m_median": percentile(cross_track, 0.5),
        "independent_cross_track_m_p95": percentile(cross_track, 0.95),
        "cross_track_over_25m_percent": round(100.0 * sum(d > 25 for d in cross_track) / n, 1),
        "cross_track_over_50m_percent": round(100.0 * sum(d > 50 for d in cross_track) / n, 1),
        "reported_accuracy_m_median": percentile(accuracy, 0.5),
        "app_snap_distance_m_p95": percentile(app_distances, 0.95),
        "sample_interval_s_median": percentile(intervals, 0.5),
        "implausible_jump_count_over_55mps": implausible_jumps,
        "note": "Misura indipendente rispetto alla geometria GPX fornita; non è una verità assoluta se il GPX non coincide con la strada realmente percorsa."
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace_csv", type=Path, help="CSV esportato da Diagnostica GPS")
    parser.add_argument("route_gpx", type=Path, help="GPX del percorso pianificato da confrontare")
    args = parser.parse_args()
    report = summarize_trace(read_trace_csv(args.trace_csv), read_route_gpx(args.route_gpx))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
