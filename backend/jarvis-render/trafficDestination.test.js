import test from "node:test";
import assert from "node:assert/strict";
import { extractTrafficDestination, geocodeTrafficDestination } from "./trafficDestination.js";

test("extracts an explicit destination from natural Italian traffic questions", () => {
  assert.equal(extractTrafficDestination("Che traffico c'è per andare da qui a Los Cristianos?"), "Los Cristianos");
  assert.equal(extractTrafficDestination("Devo andare a Guaza, mi dici se c'è traffico?"), "Guaza");
  assert.equal(extractTrafficDestination("Com'è il traffico verso Costa Adeje?"), "Costa Adeje");
  assert.equal(extractTrafficDestination("Traffico vicino a me?"), "");
});

test("extracts explicit traffic destinations in common device languages", () => {
  assert.equal(extractTrafficDestination("What's traffic like from here to Los Cristianos?"), "Los Cristianos");
  assert.equal(extractTrafficDestination("¿Qué tráfico hay desde aquí hasta Los Cristianos?"), "Los Cristianos");
  assert.equal(extractTrafficDestination("Quel trafic y a-t-il d'ici à Los Cristianos?"), "Los Cristianos");
  assert.equal(extractTrafficDestination("Wie ist der Verkehr von hier nach Los Cristianos?"), "Los Cristianos");
  assert.equal(extractTrafficDestination("Como está o trânsito daqui até Los Cristianos?"), "Los Cristianos");
  assert.equal(extractTrafficDestination("Hoe is het verkeer van hier naar Los Cristianos?"), "Los Cristianos");
});

test("rejects blank, overlong and non-explicit destinations", () => {
  assert.equal(extractTrafficDestination("traffico"), "");
  assert.equal(extractTrafficDestination("da qui a ?"), "");
  assert.equal(extractTrafficDestination("da qui a " + "x".repeat(140)), "");
});

test("geocodes a destination using GPS proximity and returns bounded coordinates", async () => {
  let requestedUrl = "";
  const result = await geocodeTrafficDestination("Los Cristianos", { latitude: 28.1, longitude: -16.7 }, {
    fetchImpl: async url => {
      requestedUrl = String(url);
      return {
        ok: true,
        json: async () => ({
          features: [
            { geometry: { coordinates: [-16.71, 28.09] }, properties: { name: "Los Cristianos", city: "Arona", country: "Spain" } },
            { geometry: { coordinates: [-3.7, 40.4] }, properties: { name: "Los Cristianos", country: "Spain" } }
          ]
        })
      };
    }
  });
  assert.match(requestedUrl, /photon\.komoot\.io/);
  assert.equal(result.name, "Los Cristianos");
  assert.equal(result.point.latitude, 28.09);
  assert.equal(result.point.longitude, -16.71);
});

test("rejects geocoder results outside Spain or too far from the current GPS fix", async () => {
  const result = await geocodeTrafficDestination("Los Cristianos", { latitude: 28.1, longitude: -16.7 }, {
    fetchImpl: async () => ({
      ok: true,
      json: async () => ({
        features: [
          { geometry: { coordinates: [-3.7, 40.4] }, properties: { name: "Los Cristianos", country: "Spain" } },
          { geometry: { coordinates: [2.35, 48.86] }, properties: { name: "Los Cristianos", country: "France" } }
        ]
      })
    })
  });
  assert.equal(result, null);
});
