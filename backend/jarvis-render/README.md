# NEXT NAVI PRO — Jarvis proxy for Render

This is the production Node.js backend for Jarvis, deployed on Render. The legacy `backend/jarvis-cloudflare` Worker is not the production endpoint.

## Deploy with Render Blueprint

1. In Render, choose **New + → Blueprint** and connect this repository.
2. Select branch `fix/jarvis-render-backend` (or the branch containing this file).
3. Render reads `render.yaml` at the repository root and creates the `next-navi-pro-jarvis` web service.
4. Enter the existing Gemini API key as the secret environment variable `GEMINI_API_KEY`. For speed-limit matching, set `TOMTOM_API_KEY` in Render Environment. TomTom Snap to Roads is the primary provider. Optionally keep `HERE_API_KEY` configured as a secondary fallback only if your HERE plan explicitly permits this navigation/speed-limit use case. Never put either key in GitHub files, commits, APK configuration, or chat.
5. Wait for the first deployment to finish. Open the service URL plus `/health`. Expected JSON:
   `{"ok":true,"service":"NEXT NAVI PRO Jarvis proxy"}`
6. The production app endpoint is `https://next-navi-pro-jarvis.onrender.com/jarvis`. Verify `/health` and `/jarvis` before releasing a new APK.

## API contract

- `GET /` and `GET /health`: health response.
- `POST /jarvis`: accepts JSON containing `message`, optional `language`, and optional `personality` (`preset`, `irony`, `warmth`, `energy`, `concision`); returns `{"reply":"..."}`. Personality fields are bounded and allow-listed by the server. Voice speed and pitch are applied locally by Android and are not sent to the backend.
- `GET /speed-limit?lat=28.12271&lon=-16.72600`: diagnostic query using OSM/Overpass. Returns an explicit mapped `speedLimitKmh`, nearby road name/ref, and estimated distance, or `speedLimitKmh: null` if no unambiguous mapped limit is found. This endpoint is suitable for a browser smoke test and does not require a HERE key. It can return HTTP 502 if Overpass is unavailable.
- `POST /speed-limit`: accepts 2–8 recent valid GPS points and uses TomTom Snap to Roads when `TOMTOM_API_KEY` is configured. It reads explicit maximum speed limits from the last matched road segment. If TomTom cannot return a limit and `HERE_API_KEY` is configured, it tries HERE Route Matching. Returns `{"speedLimitKmh":50,"source":"TomTom Snap to Roads"}` or an explicit null result. Requests are rate-limited on the server. Provider keys remain server-side.
- Gemini key remains server-side in `GEMINI_API_KEY`.
- The server listens on `0.0.0.0` and Render's `PORT`.
- Request body is capped at 16 KiB, user text is capped by `MAX_REQUEST_CHARS`, and an in-memory best-effort per-IP rate limit is applied. This is not authentication and is not a robust global abuse-control system.

## Important limitations

The Render free web service can sleep after inactivity, causing a cold start delay. Test response time before relying on online Jarvis while riding. The offline app features remain separate. TomTom is optional until `TOMTOM_API_KEY` is set: if no provider is configured, the POST endpoint returns a configuration error; Android's separate OSM fallback remains available. Verify TomTom plan, quotas, and permitted use before relying on the service.

GitHub Actions defaults `NEXT_NAVI_AI_ENDPOINT` to the Render URL above. If the repository variable is set, it must also point to this Render service. Keep the existing APK signing secrets and signing configuration unchanged.
