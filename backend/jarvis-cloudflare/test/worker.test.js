import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const originalFetch = globalThis.fetch;
let nextWeatherResponse;

test.beforeEach(() => {
  nextWeatherResponse = {
    current: {
      temperature_2m: 23.4,
      weather_code: 2,
      wind_speed_10m: 12.1,
      wind_direction_10m: 90,
      wind_gusts_10m: 24.7,
      time: "2026-10-19T12:00"
    }
  };
  globalThis.fetch = async (url) => {
    assert.match(String(url), /^https:\/\/api\.open-meteo\.com\/v1\/forecast\?/);
    return new Response(JSON.stringify(nextWeatherResponse), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  };
});

test.afterEach(() => {
  globalThis.fetch = originalFetch;
});

async function postJarvis(payload, ip) {
  return worker.fetch(new Request("https://example.test/jarvis", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "CF-Connecting-IP": ip
    },
    body: JSON.stringify(payload)
  }), {});
}

test("health endpoint is available without provider credentials", async () => {
  const response = await worker.fetch(new Request("https://example.test/health"), {});
  assert.equal(response.status, 200);
  assert.equal((await response.json()).ok, true);
});

test("answers live weather from Open-Meteo even when Gemini key is absent", async () => {
  const response = await postJarvis({
    message: "Che condizioni meteo ci sono?",
    language: "it-IT",
    location: { latitude: 28.05, longitude: -16.68 }
  }, "test-weather-without-key");
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.match(body.reply, /Open-Meteo/);
  assert.match(body.reply, /23\.4 gradi/);
  assert.match(body.reply, /12\.1 km\/h/);
  assert.equal(body.validTime, "2026-10-19T12:00");
});

test("does not invent local weather when no coordinates are provided", async () => {
  const response = await postJarvis({
    message: "Che tempo fa?",
    language: "it-IT"
  }, "test-weather-no-location");
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.match(body.reply, /posizione GPS attendibile/i);
});

test("conversational AI clearly reports missing Gemini configuration", async () => {
  const response = await postJarvis({
    message: "Raccontami una battuta",
    language: "it-IT"
  }, "test-chat-without-key");
  assert.equal(response.status, 503);
  assert.equal((await response.json()).error, "Provider not configured");
});

test("weather lookup failure is reported without fabricated values", async () => {
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 503 });
  const response = await postJarvis({
    message: "Che tempo fa?",
    language: "it-IT",
    location: { latitude: 28.05, longitude: -16.68 }
  }, "test-weather-upstream-failure");
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.match(body.reply, /Non riesco a verificare il meteo/i);
  assert.doesNotMatch(body.reply, /23\.4 gradi/);
});
