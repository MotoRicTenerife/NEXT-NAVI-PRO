import test from "node:test";
import assert from "node:assert/strict";
import { isTrafficQuery } from "./trafficIntent.js";

test("recognizes live traffic questions in supported device languages", () => {
  for (const phrase of [
    "Che traffico c'è da qui a Los Cristianos?",
    "¿Qué tráfico hay hasta Los Cristianos?",
    "What's the traffic like from here to Los Cristianos?",
    "Quelle est la circulation vers Los Cristianos ?",
    "Wie ist der Verkehr nach Los Cristianos?",
    "Como está o trânsito até Los Cristianos?",
    "Hoe is het verkeer naar Los Cristianos?"
  ]) assert.equal(isTrafficQuery(phrase), true, phrase);
});

test("does not classify unrelated short chat as traffic", () => {
  assert.equal(isTrafficQuery("Ciao, come stai?"), false);
  assert.equal(isTrafficQuery("What time is it?"), false);
  assert.equal(isTrafficQuery(""), false);
});
