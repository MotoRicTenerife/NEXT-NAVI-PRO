#!/usr/bin/env python3
"""Bounded, read-only load check for the live NEXT NAVI PRO Render backend.

No Gemini requests are sent (avoids API spend). Only health and provider-status GETs
are used. The paid TomTom lookup is a POST endpoint and is tested with mocked upstream
responses in backend unit tests, not by sending invalid GET requests to production.
"""
import concurrent.futures
import json
import os
import statistics
import time
import urllib.error
import urllib.request

BASE = os.environ.get("RENDER_BASE_URL", "https://next-navi-pro-jarvis.onrender.com").rstrip("/")
USER_AGENT = "NEXT-NAVI-PRO-Render-Stress/1.0"
TIMEOUT = 25


def request(path):
    url = BASE + path
    started = time.monotonic()
    req = urllib.request.Request(url, headers={
        "Accept": "application/json",
        "User-Agent": USER_AGENT,
    })
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            body = response.read(65536).decode("utf-8", "replace")
            status = response.status
        try:
            payload = json.loads(body)
        except (ValueError, TypeError):
            payload = {}
        return {"path": path, "status": status, "seconds": time.monotonic() - started,
                "ok": status == 200, "payload": payload}
    except urllib.error.HTTPError as exc:
        try:
            detail = exc.read(512).decode("utf-8", "replace")
        except Exception:
            detail = ""
        return {"path": path, "status": exc.code, "seconds": time.monotonic() - started,
                "ok": False, "error": detail[:200]}
    except Exception as exc:
        return {"path": path, "status": None, "seconds": time.monotonic() - started,
                "ok": False, "error": str(exc)[:200]}


def run_group(label, paths, workers):
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        results = list(pool.map(request, paths))
    durations = sorted(item["seconds"] for item in results)
    ok_count = sum(item["ok"] for item in results)
    p95 = durations[min(len(durations) - 1, int(0.95 * len(durations)))] if durations else 0
    report = {
        "test": label, "requests": len(results), "workers": workers,
        "successes": ok_count, "failures": len(results) - ok_count,
        "success_rate_pct": round(100 * ok_count / len(results), 1) if results else 0,
        "elapsed_seconds": round(time.monotonic() - started, 2),
        "mean_latency_seconds": round(statistics.mean(durations), 3) if durations else None,
        "p95_latency_seconds": round(p95, 3),
        "max_latency_seconds": round(max(durations), 3) if durations else None,
        "http_statuses": {},
    }
    for item in results:
        key = str(item["status"])
        report["http_statuses"][key] = report["http_statuses"].get(key, 0) + 1
    print(json.dumps(report, ensure_ascii=False))
    for item in results:
        if not item["ok"]:
            print(json.dumps({"failure": label, **{k: v for k, v in item.items() if k != "payload"}}, ensure_ascii=False))
    return results, report


def main():
    if not BASE.startswith("https://"):
        raise SystemExit("RENDER_BASE_URL must use HTTPS")
    print(json.dumps({"test": "start", "base": BASE, "note": "No Gemini calls; bounded GET-only test"}))
    health, health_report = run_group("health-concurrency", ["/health"] * 40, 8)
    status, status_report = run_group("speed-limit-status-concurrency", ["/speed-limit/status"] * 16, 4)
    health_payload_ok = all(item["ok"] and item.get("payload", {}).get("ok") is True for item in health)
    status_payload_ok = all(
        item["ok"]
        and item.get("payload", {}).get("ok") is True
        and (
            item.get("payload", {}).get("roadEditorOsm") is True
            or item.get("payload", {}).get("osmDiagnostic") is True
        )
        and isinstance(item.get("payload", {}).get("tomtomConfigured"), bool)
        for item in status
    )
    print(json.dumps({
        "test": "summary",
        "health_stable": health_payload_ok,
        "status_stable": status_payload_ok,
        "health_p95_seconds": health_report["p95_latency_seconds"],
        "health_success_rate_pct": health_report["success_rate_pct"],
        "status_success_rate_pct": status_report["success_rate_pct"],
    }, ensure_ascii=False))
    # Keep CI useful while separately flagging the already-known speed-limit
    # provider outage. Core health must remain reliable under bounded concurrency.
    if not health_payload_ok or health_report["success_rate_pct"] < 95 or health_report["p95_latency_seconds"] > 25:
        raise SystemExit("FAIL: Render health endpoint did not meet stress-test stability thresholds")
    if health_report["p95_latency_seconds"] > 10:
        print("::warning::Render health is responding, but p95 latency is elevated; investigate cold starts/instance capacity.")
    if not status_payload_ok:
        print("::warning::Speed-limit status endpoint degraded under concurrent requests.")
    if not limit_ok:
        print("::warning::Live speed-limit lookup remains unavailable; inspect OSM/Overpass/TomTom provider diagnostics.")
    print("PASS: bounded Render health stress test completed; see summary for feature-level failures.")


if __name__ == "__main__":
    main()
