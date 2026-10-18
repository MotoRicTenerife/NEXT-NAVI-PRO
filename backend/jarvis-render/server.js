import http from "node:http";

const PORT = Number.parseInt(process.env.PORT || "10000", 10);
const HOST = "0.0.0.0";
const MAX_BODY_BYTES = 16 * 1024;
const MAX_REQUEST_CHARS = clampInt(process.env.MAX_REQUEST_CHARS, 500, 1, 4000);
const WINDOW_MS = 10 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 10;
const ipWindows = new Map();

function clampInt(raw, fallback, min, max) {
  const value = Number.parseInt(raw ?? "", 10);
  return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback;
}

function send(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-length": Buffer.byteLength(payload)
  });
  res.end(payload);
}

function getClientIp(req) {
  // Render terminates HTTPS at its proxy and forwards the client address.
  // This is a best-effort abuse limit, not an authentication mechanism.
  const forwarded = req.headers["x-forwarded-for"];
  if (typeof forwarded === "string" && forwarded.length < 500) {
    return forwarded.split(",")[0].trim().slice(0, 80) || "unknown";
  }
  return req.socket.remoteAddress || "unknown";
}

function rateLimited(ip, now) {
  let window = ipWindows.get(ip);
  if (!window || now - window.startedAt >= WINDOW_MS) {
    window = { startedAt: now, count: 0 };
    ipWindows.set(ip, window);
  }
  if (window.count >= MAX_REQUESTS_PER_WINDOW) return true;
  window.count += 1;

  if (ipWindows.size > 5000) {
    for (const [key, value] of ipWindows) {
      if (now - value.startedAt >= WINDOW_MS) ipWindows.delete(key);
    }
  }
  return false;
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      const error = new Error("Request body too large");
      error.statusCode = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    const error = new Error("Invalid JSON");
    error.statusCode = 400;
    throw error;
  }
}

async function handle(req, res) {
  let url;
  try {
    url = new URL(req.url || "/", "http://localhost");
  } catch {
    return send(res, 400, { error: "Invalid URL" });
  }

  if (req.method === "GET" && (url.pathname === "/health" || url.pathname === "/")) {
    return send(res, 200, { ok: true, service: "NEXT NAVI PRO Jarvis proxy" });
  }
  if (url.pathname !== "/jarvis") return send(res, 404, { error: "Not found" });
  if (req.method !== "POST") return send(res, 405, { error: "Method not allowed" });

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error("Jarvis configuration error: GEMINI_API_KEY is missing");
    return send(res, 503, { error: "Provider not configured" });
  }

  if (rateLimited(getClientIp(req), Date.now())) {
    console.warn("Jarvis rate limit reached");
    return send(res, 429, { error: "Too many requests; try again later" });
  }

  let input;
  try {
    input = await readJson(req);
  } catch (error) {
    console.warn("Jarvis request rejected", { status: error.statusCode || 400 });
    return send(res, error.statusCode || 400, {
      error: error.statusCode === 413 ? "Request body too large" : "Invalid JSON"
    });
  }

  const message = typeof input?.message === "string"
    ? input.message.trim().slice(0, MAX_REQUEST_CHARS)
    : "";
  const language = typeof input?.language === "string"
    ? input.language.trim().slice(0, 20) || "it-IT"
    : "it-IT";
  if (!message) {
    console.warn("Jarvis request rejected: missing message");
    return send(res, 400, { error: "Message is required" });
  }

  const instructions = "You are Jarvis, a concise spoken conversational assistant for NEXT NAVI PRO. Reply in " +
    language +
    ". Be friendly and brief. You are not the navigation engine. Never invent GPS position, road names, legal speed limits, cameras, weather, maneuvers, routes, arrival times or live facts. Never tell the app to change navigation state, route, GPS, limits or alarms. Only deterministic app commands and verified app data can do that. If you cannot verify a live fact, say so. Never encourage phone interaction while driving; tell the user to stop safely first. Do not ask for passwords, keys or unnecessary personal data.";

  const model = (process.env.GEMINI_MODEL || "gemini-3.5-flash-lite").trim();
  const endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
    encodeURIComponent(model) + ":generateContent?key=" + encodeURIComponent(apiKey);

  let providerResponse;
  try {
    providerResponse = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: instructions }] },
        contents: [{ role: "user", parts: [{ text: message }] }],
        generationConfig: { temperature: 0.4, maxOutputTokens: 300 }
      }),
      signal: AbortSignal.timeout(25000)
    });
  } catch (error) {
    console.error("Jarvis provider network failure", {
      errorType: error instanceof Error ? error.name : "UnknownError",
      model
    });
    return send(res, 502, { error: "AI provider unreachable" });
  }

  if (!providerResponse.ok) {
    console.error("Jarvis provider returned HTTP error", {
      status: providerResponse.status,
      model
    });
    const status = providerResponse.status;
    return send(res, status === 429 ? 429 : 502, {
      error: status === 429
        ? "Free-tier limit reached"
        : status === 400
          ? "AI provider rejected the request"
          : status === 401 || status === 403
            ? "AI provider authentication or permission error"
            : "AI provider request failed",
      providerStatus: status
    });
  }

  let data;
  try {
    data = await providerResponse.json();
  } catch {
    console.error("Jarvis provider returned invalid JSON", { model });
    return send(res, 502, { error: "Invalid provider response" });
  }

  const reply = data?.candidates?.[0]?.content?.parts
    ?.map(part => typeof part.text === "string" ? part.text : "")
    .join("\n").trim().slice(0, 800);
  if (!reply) {
    console.error("Jarvis provider response contained no text", { model });
    return send(res, 502, { error: "AI returned no text" });
  }
  return send(res, 200, { reply });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(error => {
    console.error("Jarvis request handler failed", {
      errorType: error instanceof Error ? error.name : "UnknownError"
    });
    if (!res.headersSent) send(res, 500, { error: "Internal server error" });
    else res.destroy();
  });
});

server.listen(PORT, HOST, () => {
  console.log("NEXT NAVI PRO Jarvis proxy listening", { host: HOST, port: PORT });
});
