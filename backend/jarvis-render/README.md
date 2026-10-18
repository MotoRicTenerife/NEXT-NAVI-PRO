# NEXT NAVI PRO — Jarvis proxy for Render

This is a Render-compatible Node.js deployment of the existing Jarvis proxy contract. It is kept separate from `backend/jarvis-cloudflare`; the Cloudflare Worker and current Android endpoint are not changed by adding these files.

## Deploy with Render Blueprint

1. In Render, choose **New + → Blueprint** and connect this repository.
2. Select branch `fix/jarvis-render-backend` (or the branch containing this file).
3. Render reads `render.yaml` at the repository root and creates the `next-navi-pro-jarvis` web service.
4. When prompted, enter the existing Gemini API key as the value of the secret environment variable `GEMINI_API_KEY`. Do not put the key in GitHub files, commits, APK configuration, or chat.
5. Wait for the first deployment to finish. Open the service URL plus `/health`. Expected JSON:
   `{"ok":true,"service":"NEXT NAVI PRO Jarvis proxy"}`
6. The app endpoint, after testing, will be the service URL plus `/jarvis`.

## API contract

- `GET /` and `GET /health`: health response.
- `POST /jarvis`: accepts JSON containing `message` and optional `language`; returns `{"reply":"..."}`.
- Gemini key remains server-side in `GEMINI_API_KEY`.
- The server listens on `0.0.0.0` and Render's `PORT`.
- Request body is capped at 16 KiB, user text is capped by `MAX_REQUEST_CHARS`, and an in-memory best-effort per-IP rate limit is applied. This is not authentication and is not a robust global abuse-control system.

## Important limitations

The Render free web service can sleep after inactivity, causing a cold start delay. Test response time before relying on online Jarvis while riding. The offline app features remain separate.

Do not change GitHub Actions variable `NEXT_NAVI_AI_ENDPOINT` until `/health` is reachable from the phone and the `/jarvis` endpoint has been tested. Keep the existing APK signing secrets and signing configuration unchanged.
