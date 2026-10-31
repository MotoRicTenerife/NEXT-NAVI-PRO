# NEXT NAVI PRO — Jarvis proxy for Render

This is the production Node.js backend for Jarvis, deployed on Render. The legacy `backend/jarvis-cloudflare` Worker is not the production endpoint.

## Deploy with Render Blueprint

1. In Render, choose **New + → Blueprint** and connect this repository.
2. Select branch `fix/jarvis-render-backend` (or the branch containing this file).
3. Render reads `render.yaml` at the repository root and creates the `next-navi-pro-jarvis` web service.
4. Enter the existing Gemini API key as the secret environment variable `GEMINI_API_KEY`. For speed-limit matching, set `TOMTOM_API_KEY` in Render Environment. TomTom Snap to Roads is the only paid map-matching provider used by this version; OSM/Overpass remains the community-data fallback. HERE integration has been removed. Never put API keys in GitHub files, commits, APK configuration, or chat.
5. Wait for the first deployment to finish. Open the service URL plus `/health`. Expected JSON:
   `{"ok":true,"service":"NEXT NAVI PRO Jarvis proxy"}`
6. The production app endpoint is `https://next-navi-pro-jarvis.onrender.com/jarvis`. Verify `/health` and `/jarvis` before releasing a new APK.

## API contract

- `GET /` and `GET /health`: health response.
- `POST /jarvis`: accepts JSON containing `message`, optional `language`, and optional `personality` (`preset`, `irony`, `warmth`, `energy`, `concision`); returns `{"reply":"..."}`. Personality fields are bounded and allow-listed by the server. Voice speed and pitch are applied locally by Android and are not sent to the backend.
- Jarvis keeps Gemini in the response path for live weather/traffic questions whenever `GEMINI_API_KEY` is configured. Verified provider results are supplied as context; deterministic provider-only replies are reserved for the no-Gemini fallback.
- `POST /jarvis` may include an optional `tripContext` from Android: a recent Valhalla route estimate plus fresh route-matched TomTom Orbis incidents. The backend validates source, freshness, ranges and bounded fields before using it. The route estimate is explicitly marked as baseline (not traffic-adjusted); incident geometry does not prove carriageway direction; an empty incident match does not prove free-flowing traffic.
- `POST /traffic/route`: gated TomTom Routing API v1 proxy. Accepts WGS84 origin/destination, up to three waypoints and `vehicleMode` (`car` or `motorcycle`); returns live-traffic ETA, delay, no-traffic ETA, traffic sections and route geometry only when the requested vehicle mode is verified for every returned route section.
- Set `TOMTOM_TRAFFIC_ROUTING_ENABLED=true` in Render only after confirming the API key's routing entitlement and quota. The flag is off by default. Motorcycle routing is beta and has incomplete restriction data in some areas; the app must retain Valhalla motorcycle routing as its navigation baseline and use TomTom as a traffic-aware cross-check, never an automatic replacement.
- `GET /traffic/status` reports provider configuration and the two traffic feature flags without exposing credentials.
- `GET /speed-limit/status`: reports whether TomTom is configured and the provider guard state without exposing credentials. OSM road-editor endpoints are separate and do not supply automatic legal speed limits.
- `POST /speed-limit`: accepts 2–8 recent valid GPS points and uses TomTom Snap to Roads. It reads only explicit maximum speed limits from the matched road segment. If TomTom cannot return an explicit, unambiguous limit, the result remains unknown/null; neither the Android automatic display nor the road-editor proposal falls back to OSM `maxspeed`. TomTom is requested in metric mode. Unknown or missing units are rejected instead of being guessed. Requests are rate-limited on the server. Provider keys remain server-side.
- Gemini key remains server-side in `GEMINI_API_KEY`.
- The server listens on `0.0.0.0` and Render's `PORT`.
- Request body is capped at 16 KiB, user text is capped by `MAX_REQUEST_CHARS`, and an in-memory best-effort per-IP rate limit is applied. This is not authentication and is not a robust global abuse-control system.

## Important limitations

The Render free web service can sleep after inactivity, causing a cold start delay. Test response time before relying on online Jarvis while riding. The offline app features remain separate. TomTom must be configured through `TOMTOM_API_KEY` for automatic speed-limit lookups; if it is not configured, the POST endpoint returns a configuration error and the app leaves the legal limit unknown. OSM/Overpass remains available only for road-editor search/geometry and community camera data, not as a speed-limit fallback. Verify TomTom plan, quotas, and permitted use before relying on the service.

GitHub Actions defaults `NEXT_NAVI_AI_ENDPOINT` to the Render URL above. If the repository variable is set, it must also point to this Render service. Keep the existing APK signing secrets and signing configuration unchanged.

## TomTom quota and rate limiting

`POST /speed-limit` calls TomTom Snap to Roads, which counts against TomTom's free allowance of
2,500 non-tile requests per day (shared by every non-tile API on the key; Snap to Roads default
QPS is 5). Admission is decided in `speedLimitGuard.js`, independently from the manual-segment
archive (`/speed-limit-segments*`, 20 requests / 10 minutes per client, unchanged):

| Control | Default | Environment override |
| --- | --- | --- |
| Per-client token bucket (burst) | 30 | `TOMTOM_RATE_BURST` |
| Per-client refill | 1 token / 5 s | `TOMTOM_RATE_REFILL_MS` |
| Global upstream calls per UTC day | 2000 | `TOMTOM_DAILY_BUDGET` |
| Cooldown after TomTom 429 | `Retry-After` (5–300 s, default 30 s) | — |
| Cooldown after TomTom 401/403 | 300 s | — |

Refusals are `429` with a `Retry-After` header and `{ "reason", "retryAfterSeconds" }` where
`reason` is `rate_limited`, `daily_budget_exhausted`, `provider_cooldown` or
`tomtom_quota_or_rate_limit`. Malformed requests (`400`) never consume admission. Counters are in
memory and reset when the Render free instance restarts, so the daily budget is a safeguard, not
accounting. `GET /speed-limit/status` reports the guard state (no secrets) and `GET /health`
reports `revision` (`RENDER_GIT_COMMIT`) to identify the deployed commit.

Run the tests with `npm test`. Set `NEXT_NAVI_TRACE_CSV` to a recorded trip CSV to replay a real ride;
otherwise a deterministic synthetic trace of the same duration is used.
