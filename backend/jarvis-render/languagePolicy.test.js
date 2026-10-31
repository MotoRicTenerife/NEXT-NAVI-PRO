import test from "node:test";
import assert from "node:assert/strict";
import { normalizeLanguageTag } from "./languagePolicy.js";

test("keeps valid device language tags", () => {
  assert.equal(normalizeLanguageTag("it-IT"), "it-IT");
  assert.equal(normalizeLanguageTag("es-ES"), "es-ES");
  assert.equal(normalizeLanguageTag("zh-Hant-TW"), "zh-Hant-TW");
});

test("normalizes locale underscores and uses Accept-Language when payload is absent", () => {
  assert.equal(normalizeLanguageTag("pt_BR"), "pt-BR");
  assert.equal(normalizeLanguageTag("", "de-DE,de;q=0.9,en;q=0.8"), "de-DE");
});

test("rejects malformed language tags and falls back safely", () => {
  assert.equal(normalizeLanguageTag("it<script>"), "en-GB");
  assert.equal(normalizeLanguageTag(""), "en-GB");
});
