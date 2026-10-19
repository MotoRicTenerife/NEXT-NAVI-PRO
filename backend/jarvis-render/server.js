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

function haversineKm(a, b) {
  const rad = value => value * Math.PI / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const x = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(Math.max(0, 1 - x)));
}

async function fetchLiveWeather(location) {
  const url = new URL("https://api.open-meteo.com/v1/forecast");
  url.search = new URLSearchParams({
    latitude: String(location.latitude),
    longitude: String(location.longitude),
    current: "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
    timezone: "auto"
  }).toString();

  let lastError;
  // Render instances can have a cold network path after sleeping. Retry once on
  // transient transport/5xx failures; never substitute guessed weather values.
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    try {
      const response = await fetch(url, {
        signal: AbortSignal.timeout(12000),
        headers: { "accept": "application/json" }
      });
      if (!response.ok) {
        const error = new Error("Open-Meteo HTTP " + response.status);
        if (response.status >= 500 && attempt < 2) {
          lastError = error;
          continue;
        }
        throw error;
      }
      const data = await response.json();
      const current = data?.current;
      if (!current || !Number.isFinite(current.temperature_2m) ||
          !Number.isFinite(current.weather_code) || !Number.isFinite(current.wind_speed_10m) ||
          !Number.isFinite(current.wind_gusts_10m) || typeof current.time !== "string") {
        throw new Error("Invalid Open-Meteo response");
      }
      return {
        source: "Open-Meteo",
        fetchedAt: new Date().toISOString(),
        validTime: current.time,
        temperatureC: current.temperature_2m,
        feelsLikeC: current.apparent_temperature,
        humidityPercent: current.relative_humidity_2m,
        precipitationMm: current.precipitation,
        weatherCode: current.weather_code,
        windKmh: current.wind_speed_10m,
        windDirectionDegrees: current.wind_direction_10m,
        gustKmh: current.wind_gusts_10m
      };
    } catch (error) {
      lastError = error;
      if (attempt >= 2 || (error instanceof Error && /^Open-Meteo HTTP 4/.test(error.message))) {
        throw error;
      }
    }
  }
  throw lastError || new Error("Open-Meteo lookup failed");
}
function xmlText(block, names) {
  for (const name of names) {
    const pattern = "<(?:[\\w.-]+:)?" + name + "\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?" + name + ">";
    const match = block.match(new RegExp(pattern, "i"));
    if (match) return match[1].replace(/<[^>]*>/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/\s+/g, " ").trim().slice(0, 220);
  }
  return "";
}

async function fetchDgtTraffic(location) {
  const resourceId = "1d36ea74-593d-4e45-acfa-fb8db5a460bc";
  const metadataResponse = await fetch("https://nap.dgt.es/api/3/action/resource_show?id=" + resourceId, {
    signal: AbortSignal.timeout(7000),
    headers: { "accept": "application/json" }
  });
  if (!metadataResponse.ok) throw new Error("DGT resource metadata HTTP " + metadataResponse.status);
  const metadata = await metadataResponse.json();
  const resourceUrl = metadata?.result?.url;
  if (!metadata?.success || typeof resourceUrl !== "string" || !resourceUrl.startsWith("https://")) {
    throw new Error("DGT resource URL unavailable");
  }
  const response = await fetch(resourceUrl, { signal: AbortSignal.timeout(9000), headers: { "accept": "application/xml,text/xml,*/*" } });
  if (!response.ok) throw new Error("DGT feed HTTP " + response.status);
  const declaredLength = Number(response.headers.get("content-length") || 0);
  if (declaredLength > 5 * 1024 * 1024) throw new Error("DGT feed too large");
  const xml = await response.text();
  if (xml.length > 5 * 1024 * 1024) throw new Error("DGT feed too large");
  const records = [];
  const recordPattern = /<(?:[\w.-]+:)?situationRecord\b[^>]*>([\s\S]*?)<\/(?:[\w.-]+:)?situationRecord>/gi;
  let match;
  while ((match = recordPattern.exec(xml)) !== null) {
    const block = match[1];
    const latText = xmlText(block, ["latitude"]);
    const lonText = xmlText(block, ["longitude"]);
    if (!latText || !lonText) continue;
    const lat = Number(latText);
    const lon = Number(lonText);
    if (!Number.isFinite(lat) || lat < -90 || lat > 90 || !Number.isFinite(lon) || lon < -180 || lon > 180) continue;
    const distanceKm = haversineKm(location, { latitude: lat, longitude: lon });
    if (distanceKm > 30) continue;
    records.push({
      distanceKm: Math.round(distanceKm * 10) / 10,
      road: xmlText(block, ["roadNumber", "roadName", "descriptor"]),
      description: xmlText(block, ["description", "value", "cause"]),
      status: xmlText(block, ["validityStatus", "impactOnTraffic", "probabilityOfOccurrence"]),
      startTime: xmlText(block, ["overallStartTime", "situationRecordCreationTime"])
    });
    if (records.length >= 12) break;
  }
  return {
    source: "DGT / Punto de Acceso Nacional de Tráfico, incidencias DATEX2 v3.7",
    fetchedAt: new Date().toISOString(),
    coverageRadiusKm: 30,
    incidents: records,
    note: records.length
      ? "Only geolocated incidents explicitly present in the feed; this is not a complete measure of traffic speed."
      : "No geolocated DGT incidents found in the feed within 30 km. This does NOT prove traffic is flowing freely."
  };
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
    return send(res, 200, { ok: true, service: "NEXT NAVI PRO Jarvis proxy", revision: process.env.RENDER_GIT_COMMIT || "unknown" });
  }
  if (url.pathname !== "/jarvis") return send(res, 404, { error: "Not found" });
  if (req.method !== "POST") return send(res, 405, { error: "Method not allowed" });

  const apiKey = process.env.GEMINI_API_KEY;

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
  const assistantName = typeof input?.assistantName === "string"
    ? input.assistantName.trim().replace(/[<>\r\n]/g, "").slice(0, 30) || "Jarvis"
    : "Jarvis";
  const location = input?.location &&
    Number.isFinite(input.location.latitude) && input.location.latitude >= -90 && input.location.latitude <= 90 &&
    Number.isFinite(input.location.longitude) && input.location.longitude >= -180 && input.location.longitude <= 180
    ? { latitude: input.location.latitude, longitude: input.location.longitude }
    : null;
  if (!message) {
    console.warn("Jarvis request rejected: missing message");
    return send(res, 400, { error: "Message is required" });
  }

  let liveContext = "";
  let weatherResult = null;
  let trafficResult = null;
  const normalizedMessage = message.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
  const asksWeather = /\b(meteo|che tempo fa|previsioni(?: del)? tempo|previsioni meteo|pioggia|vento|weather|forecast|lluvia|clima|el tiempo|tiempo actual)\b/.test(normalizedMessage);
  const asksTraffic = /\b(traffico|trafico|traffic|atasco|incidente|retencion|congestion|coda|code)\b/.test(normalizedMessage);

  if (asksWeather && location) {
    try {
      weatherResult = await fetchLiveWeather(location);
      liveContext += "\nVERIFIED LIVE WEATHER: " + JSON.stringify(weatherResult);
    } catch (error) {
      console.warn("Jarvis live weather lookup failed", { errorType: error instanceof Error ? error.name : "UnknownError", reason: error instanceof Error ? error.message.slice(0, 180) : "unknown" });
      liveContext += "\nLIVE WEATHER LOOKUP FAILED: say current weather could not be verified; do not guess.";
    }
  } else if (asksWeather) {
    liveContext += "\nNO GPS COORDINATES WERE PROVIDED: ask for a location or say local weather cannot be verified.";
  }

  if (asksTraffic && location) {
    try {
      trafficResult = await fetchDgtTraffic(location);
      liveContext += "\nVERIFIED DGT TRAFFIC INCIDENT FEED: " + JSON.stringify(trafficResult);
    } catch {
      liveContext += "\nDGT TRAFFIC LOOKUP FAILED: say live traffic could not be verified. Official DGT traffic map: https://infocar.dgt.es/etraffic/ . Do not claim to have checked Google Maps.";
    }
  } else if (asksTraffic) {
    liveContext += "\nNO GPS COORDINATES WERE PROVIDED: live local traffic cannot be checked. Official DGT traffic map: https://infocar.dgt.es/etraffic/ .";
  }

  const instructions = "You are " + assistantName + ", the user-renamed spoken conversational assistant for NEXT NAVI PRO. Your name is exactly " +
    assistantName + ". If asked your name, answer with that exact name, never the default name Jarvis unless that is the configured name. Reply in " +
    language +
    ". Be friendly and brief. You are not the navigation engine. Never invent GPS position, road names, legal speed limits, cameras, weather, maneuvers, routes, arrival times or live facts. Use verified live context below when present, state its source and timestamp when available, and distinguish incidents from a full measure of traffic flow. If a source lookup failed or is not configured, say so clearly. Never pretend to browse or query Google Maps. Never tell the app to change navigation state, route, GPS, limits or alarms. Only deterministic app commands and verified app data can do that. Never encourage phone interaction while driving; tell the user to stop safely first. Do not ask for passwords, keys or unnecessary personal data." +
    liveContext;

  // Live-data questions use verified provider results directly, so they still work if Gemini is unavailable or misconfigured.
  if (!apiKey || asksWeather || asksTraffic) {
    const fallbackReplies = [];
    if (asksWeather) {
      if (weatherResult) {
        const descriptions = {
          0: "sereno", 1: "prevalentemente sereno", 2: "parzialmente nuvoloso", 3: "coperto",
          45: "nebbia", 48: "nebbia con brina", 51: "pioviggine leggera", 53: "pioviggine",
          55: "pioviggine intensa", 61: "pioggia leggera", 63: "pioggia", 65: "pioggia intensa",
          71: "neve leggera", 73: "neve", 75: "neve intensa", 80: "rovesci leggeri",
          81: "rovesci", 82: "rovesci intensi", 95: "temporale", 96: "temporale con grandine"
        };
        const description = descriptions[weatherResult.weatherCode] || "condizioni meteo variabili";
        fallbackReplies.push("Meteo attuale da Open-Meteo: " + description + ", " +
          weatherResult.temperatureC + " gradi, vento " + weatherResult.windKmh +
          " km/h e raffiche fino a " + weatherResult.gustKmh + " km/h. Dato aggiornato alle " +
          (weatherResult.validTime || "ora locale del servizio") + ".");
      } else {
        fallbackReplies.push("Non riesco a verificare il meteo attuale dai dati disponibili.");
      }
    }
    if (asksTraffic) {
      if (trafficResult) {
        if (trafficResult.incidents.length) {
          const summaries = trafficResult.incidents.slice(0, 3)
            .map(item => [item.road, item.description].filter(Boolean).join(": "))
            .filter(Boolean);
          fallbackReplies.push("La DGT segnala " + trafficResult.incidents.length +
            " incidenza/e geolocalizzata/e entro 30 km. " +
            (summaries.length ? summaries.join(". ") + ". " : "") +
            "Sono segnalazioni di incidenti, non una misura completa della velocità del traffico.");
        } else {
          fallbackReplies.push("Nel feed DGT non ho trovato incidenti geolocalizzati entro 30 km. Questo non significa che il traffico sia scorrevole.");
        }
      } else {
        fallbackReplies.push("Non riesco a verificare il traffico in tempo reale. Puoi consultare la mappa ufficiale DGT su infocar.dgt.es/etraffic.");
      }
    }
    if (fallbackReplies.length) return send(res, 200, { reply: fallbackReplies.join(" ") });
    console.error("Jarvis configuration error: GEMINI_API_KEY is missing");
    return send(res, 503, { error: "Provider not configured" });
  }

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
