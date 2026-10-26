import test from "node:test";
import assert from "node:assert/strict";
import { isUnauthenticatedArchiveWrite } from "./archiveWritePolicy.js";

test("blocks anonymous archive upserts", () => {
  assert.equal(isUnauthenticatedArchiveWrite("POST", "/speed-limit-segments/upsert"), true);
});

test("blocks anonymous archive deletions", () => {
  assert.equal(isUnauthenticatedArchiveWrite("DELETE", "/speed-limit-segments"), true);
});

test("does not classify archive reads as writes", () => {
  assert.equal(isUnauthenticatedArchiveWrite("GET", "/speed-limit-segments"), false);
});

test("does not classify unrelated endpoints as archive writes", () => {
  assert.equal(isUnauthenticatedArchiveWrite("POST", "/jarvis"), false);
  assert.equal(isUnauthenticatedArchiveWrite("POST", "/speed-limit"), false);
  assert.equal(isUnauthenticatedArchiveWrite("PUT", "/speed-limit-segments/upsert"), false);
});
