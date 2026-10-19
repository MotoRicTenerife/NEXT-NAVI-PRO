const ipWindows = new Map();
const WINDOW_MS = 10 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 10;

function normalizeText(value) {
  return String(value || "").normalize("NFD").replace(/[\\u0300-\\u036f]/g, "").toLowerCase();
}

function isWeatherQuestion(message) {
  const text = normalizeText(message);
  return /\\b(meteo|meteorolog\\w*|previsioni\\w*|pioggia|piove|piovera|vento|ventoso|raffiche|temperatura|weather|forecast|rain|wind|gusts|lluvia|llueve|viento|clima)\\b/.test(text) ||
    /\\b(che|come|que|como)\\b.*\\b(tempo|tiempo)\\b/.test(text) ||
    text.includes("che condizioni meteo") || text.includes("come e il tempo") ||
    text.includes("fa caldo") || text.includes("fa freddo");
}

async function fetchLiveWeather(location) {
  const url = new URL("https://api.open-meteo.com/v1/forecast");
  url.search = new URLSearchParams({
    latitude: String(location.latitude),
    longitude: String(location.longitude),
    current: "temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
    timezone: "auto"
  }).toString();
  const response = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error("Open-Meteo HTTP " + response.status);
  const data = await response.json();
  const current = data?.current;
  if (!current || !Number.isFinite(current.temperature_2m) ||
      !Number.isFinite(current.weather_code) || !Number.isFinite(current.wind_speed_10m) ||
      !Number.isFinite(current.wind_gusts_10m)) {
    throw new Error("Invalid Open-Meteo response");
  }
  return current;
}

function weatherReply(current, language) {
  const descriptions = {
    0: "sereno", 1: "prevalentemente sereno", 2: "parzialmente nuvoloso", 3: "coperto",
    45: "nebbia", 48: "nebbia con brina", 51: "pioviggine leggera", 53: "pioviggine",
    55: "pioviggine intensa", 56: "pioviggine gelata", 57: "pioviggine gelata intensa",
    61: "pioggia leggera", 63: "pioggia", 65: "pioggia intensa", 66: "pioggia gelata",
    67: "pioggia gelata intensa", 71: "neve leggera", 73: "neve", 75: "neve intensa",
    77: "granelli di neve", 80: "rovesci leggeri", 81: "rovesci", 82: "rovesci intensi",
    85: "rovesci di neve", 86: "rovesci intensi di neve", 95: "temporale",
    96: "temporale con grandine", 99: "temporale con grandine intensa"
  };
  const description = descriptions[current.weather_code] || "condizioni meteo non classificate";
  const time = typeof current.time === "string" ? current.time : "orario non disponibile";
  if (String(language).toLowerCase().startsWith("it")) {
    return "Meteo attuale verificato da Open-Meteo: " + description + ", " +
      current.temperature_2m + " gradi, vento " + current.wind_speed_10m +
      " km/h e raffiche fino a " + current.wind_gusts_10m +
      " km/h. Dato valido alle " + time + ".";
  }
  return "Verified current weather from Open-Meteo: " + description + ", " +
    current.temperature_2m + " degrees, wind " + current.wind_speed_10m +
    " km/h, gusts up to " + current.wind_gusts_10m + " km/h. Valid time: " + time + ".";
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const headers = {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff"
    };
    const respond = (body, status = 200) =>
      new Response(JSON.stringify(body), { status, headers });

    if (request.method === "GET" && url.pathname === "/health") {
      return respond({ ok: true, service: "NEXT NAVI PRO Jarvis proxy" });
    }
    if (url.pathname !== "/jarvis") return respond({ error: "Not found" }, 404);
    if (request.method !== "POST") return respond({ error: "Method not allowed" }, 405);
    const ip = request.headers.get("CF-Connecting-IP") || "unknown";
    const now = Date.now();
    let window = ipWindows.get(ip);
    if (!window || now - window.startedAt >= WINDOW_MS) {
      window = { startedAt: now, count: 0 };
      ipWindows.set(ip, window);
    }
    if (window.count >= MAX_REQUESTS_PER_WINDOW) {
      console.warn("Jarvis rate limit reached");
      return respond({ error: "Too many requests; try again later" }, 429);
    }
    window.count += 1;
    if (ipWindows.size > 5000) {
      for (const [key, value] of ipWindows) {
        if (now - value.startedAt >= WINDOW_MS) ipWindows.delete(key);
      }
    }

    let input;
    try { input = await request.json(); }
    catch {
      console.warn("Jarvis request rejected: invalid JSON");
      return respond({ error: "Invalid JSON" }, 400);
    }

    const message = typeof input?.message === "string" ? input.message.trim().slice(0, 500) : "";
    const language = typeof input?.language === "string" ? input.language.slice(0, 20) : "it-IT";
    if (!message) {
      console.warn("Jarvis request rejected: missing message");
      return respond({ error: "Message is required" }, 400);
    }

    const assistantName = typeof input?.assistantName === "string"
      ? input.assistantName.trim().replace(/[<>\\r\\n]/g, "").slice(0, 30) || "Jarvis"
      : "Jarvis";
    const location = input?.location &&
      Number.isFinite(input.location.latitude) && input.location.latitude >= -90 && input.location.latitude <= 90 &&
      Number.isFinite(input.location.longitude) && input.location.longitude >= -180 && input.location.longitude <= 180
      ? { latitude: input.location.latitude, longitude: input.location.longitude }
      : null;

    if (isWeatherQuestion(message)) {
      if (!location) {
        return respond({ reply: "Per verificare il meteo attuale mi serve una posizione GPS attendibile. Attendi il GPS e riprova." });
      }
      try {
        const current = await fetchLiveWeather(location);
        return respond({ reply: weatherReply(current, language), source: "Open-Meteo", validTime: current.time });
      } catch (error) {
        console.warn("Jarvis live weather lookup failed", {
          errorType: error instanceof Error ? error.name : "UnknownError"
        });
        return respond({ reply: "Non riesco a verificare il meteo attuale dai dati Open-Meteo. Non voglio inventare temperatura o vento; riprova più tardi." });
      }
    }

    if (!env.GEMINI_API_KEY) {
      console.error("Jarvis configuration error: GEMINI_API_KEY is missing");
      return respond({ error: "Provider not configured" }, 503);
    }

    const instructions = "You are " + assistantName + ", a concise spoken conversational assistant for NEXT NAVI PRO. Your name is exactly " + assistantName + ". Reply in " + language + ". Be friendly and brief. You are not the navigation engine. Never invent GPS position, road names, legal speed limits, cameras, weather, maneuvers, routes, arrival times or live facts. Never tell the app to change navigation state, route, GPS, limits or alarms. Only deterministic app commands and verified app data can do that. If you cannot verify a live fact, say so. Never encourage phone interaction while driving; tell the user to stop safely first. Do not ask for passwords, keys or unnecessary personal data.";

    const model = (env.GEMINI_MODEL || "gemini-3.5-flash-lite").trim();
    const endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
      encodeURIComponent(model) + ":generateContent?key=" + encodeURIComponent(env.GEMINI_API_KEY);

    let response;
    try {
      response = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: instructions }] },
          contents: [{ role: "user", parts: [{ text: message }] }],
          generationConfig: { temperature: 0.4, maxOutputTokens: 300 }
        })
      });
    } catch (error) {
      console.error("Jarvis provider network failure", {
        errorType: error instanceof Error ? error.name : "UnknownError",
        model
      });
      return respond({ error: "AI provider unreachable" }, 502);
    }
    if (!response.ok) {
      // Log only status/model; never log the API key, prompt, or provider response body.
      console.error("Jarvis provider returned HTTP error", {
        status: response.status,
        model
      });
      return respond({
        error: response.status === 429
          ? "Free-tier limit reached"
          : response.status === 400
            ? "AI provider rejected the request"
            : response.status === 401 || response.status === 403
              ? "AI provider authentication or permission error"
              : "AI provider request failed",
        providerStatus: response.status
      }, response.status === 429 ? 429 : 502);
    }
    let data;
    try { data = await response.json(); }
    catch {
      console.error("Jarvis provider returned invalid JSON", { model });
      return respond({ error: "Invalid provider response" }, 502);
    }
    const reply = data?.candidates?.[0]?.content?.parts
      ?.map(part => typeof part.text === "string" ? part.text : "").join("\n").trim().slice(0, 800);
    if (!reply) {
      console.error("Jarvis provider response contained no text", { model });
      return respond({ error: "AI returned no text" }, 502);
    }
    return respond({ reply });
  }
};
