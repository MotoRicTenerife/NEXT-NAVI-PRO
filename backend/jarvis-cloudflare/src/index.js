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
    if (!env.GEMINI_API_KEY) return respond({ error: "Provider not configured" }, 503);

    let input;
    try { input = await request.json(); }
    catch { return respond({ error: "Invalid JSON" }, 400); }

    const message = typeof input?.message === "string" ? input.message.trim().slice(0, 500) : "";
    const language = typeof input?.language === "string" ? input.language.slice(0, 20) : "it-IT";
    if (!message) return respond({ error: "Message is required" }, 400);

    const instructions = "You are Jarvis, a concise spoken conversational assistant for NEXT NAVI PRO. Reply in " + language + ". Be friendly and brief. You are not the navigation engine. Never invent GPS position, road names, legal speed limits, cameras, weather, maneuvers, routes, arrival times or live facts. Never tell the app to change navigation state, route, GPS, limits or alarms. Only deterministic app commands and verified app data can do that. If you cannot verify a live fact, say so. Never encourage phone interaction while driving; tell the user to stop safely first. Do not ask for passwords, keys or unnecessary personal data.";

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
    } catch {
      return respond({ error: "AI provider unreachable" }, 502);
    }
    if (!response.ok) {
      return respond({ error: response.status === 429 ? "Free-tier limit reached" : "AI provider request failed" }, response.status === 429 ? 429 : 502);
    }
    let data;
    try { data = await response.json(); }
    catch { return respond({ error: "Invalid provider response" }, 502); }
    const reply = data?.candidates?.[0]?.content?.parts
      ?.map(part => typeof part.text === "string" ? part.text : "").join("\n").trim().slice(0, 800);
    if (!reply) return respond({ error: "AI returned no text" }, 502);
    return respond({ reply });
  }
};
