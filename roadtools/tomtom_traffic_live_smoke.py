#!/usr/bin/env python3
"""One-call live verification of TomTom flow tiles and incidents after Render deploy."""
import json
import math
import os
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("RENDER_BASE_URL", "https://next-navi-pro-jarvis.onrender.com").rstrip("/")
TIMEOUT = 20
HEADERS = {"User-Agent": "NEXT-NAVI-PRO-TomTom-Traffic-Smoke/1.0", "Accept": "application/json"}


def get(path, accept=None):
    headers = dict(HEADERS)
    if accept:
        headers["Accept"] = accept
    request = urllib.request.Request(BASE + path, headers=headers)
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.status, response.headers, response.read()


def wait_for_feature_activation():
    deadline = time.monotonic() + 600
    expected_revision = os.environ.get("RENDER_EXPECTED_REVISION", "").strip()
    last = None
    while time.monotonic() < deadline:
        try:
            status, _, body = get("/traffic/status")
            payload = json.loads(body.decode("utf-8"))
            health_status, _, health_body = get("/health")
            health = json.loads(health_body.decode("utf-8"))
            revision = str(health.get("revision") or "")
            revision_matches = (not expected_revision or
                                (revision and (expected_revision.startswith(revision) or revision.startswith(expected_revision))))
            last = {"traffic": payload, "revision": revision, "expectedRevision": expected_revision}
            if (status == 200 and health_status == 200 and revision_matches
                    and payload.get("tomtomConfigured") is True
                    and payload.get("trafficFlowEnabled") is True
                    and payload.get("incidentDetailsEnabled") is True
                    and payload.get("trafficSegmentEnabled") is True):
                print("TomTom traffic deployment PASSED:", json.dumps(last, ensure_ascii=False))
                return
        except Exception as error:
            last = str(error)
        print("Waiting for Render to deploy the expected revision and enable all TomTom traffic services:", last)
        time.sleep(15)
    raise RuntimeError("Expected Render traffic revision was not active within 10 minutes: " + repr(last))


def main():
    if not BASE.startswith("https://"):
        raise SystemExit("RENDER_BASE_URL must use HTTPS")
    wait_for_feature_activation()

    # A real traffic tile near Guaza / TF-1 in Tenerife South.
    lat, lon, zoom = 28.06, -16.68, 15
    n = 2 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n)
    status, headers, body = get(f"/traffic/flow/{zoom}/{x}/{y}", "application/vnd.mapbox-vector-tile")
    content_type = headers.get("Content-Type", "")
    if status != 200 or "application/vnd.mapbox-vector-tile" not in content_type or not body:
        raise RuntimeError(f"TomTom flow tile failed: HTTP {status}, content-type={content_type!r}, bytes={len(body)}")
    print(f"TomTom live flow tile PASSED: HTTP {status}, {len(body)} bytes, tile={zoom}/{x}/{y}")

    bbox = "-16.80,27.90,-16.50,28.20"
    status, headers, body = get("/traffic/incidents?" + urllib.parse.urlencode({"bbox": bbox}))
    if status != 200:
        raise RuntimeError(f"TomTom incidents failed: HTTP {status}, body={body[:400]!r}")
    payload = json.loads(body.decode("utf-8"))
    incidents = payload.get("incidents")
    if payload.get("ok") is not True or not isinstance(incidents, list):
        raise RuntimeError("TomTom incidents response schema invalid: " + repr(payload)[:500])
    print(f"TomTom live incident query PASSED: HTTP {status}, {len(incidents)} incidents, observedAt={payload.get('observedAtEpochMs')}")

    print("PASS: bounded GET-only TomTom traffic flow and incident checks completed.")


if __name__ == "__main__":
    main()
