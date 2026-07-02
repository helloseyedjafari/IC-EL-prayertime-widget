const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const core = require("./prayer-core.js");

const fx = (f) => fs.readFileSync(path.join(__dirname, "fixtures", f), "utf8");
const L = (min) => ({ y: 2026, m: 7, d: 2, minutes: min });

test("parses London today row", () => {
  const { times } = core.extractToday(fx("london-2026-07.html"), L(780));
  assert.deepStrictEqual(times, {
    dawn: "02:29", sunrise: "04:48", noon: "13:05", maghrib: "21:36", midnight: "23:55",
  });
});

test("repairs Cardiff 00:00 sunrise by interpolation", () => {
  const { times } = core.extractToday(fx("cardiff-2026-07.html"), L(360));
  assert.strictEqual(times.sunrise, "05:00"); // between 04:59 and 05:01
  assert.notStrictEqual(times.sunrise, "00:00");
});

test("does NOT repair legitimate near-midnight fields", () => {
  const { times } = core.extractToday(fx("cardiff-2026-07.html"), L(360));
  assert.strictEqual(times.midnight, "00:12");
});

test("next prayer: 13:00 London -> Noon 13:05", () => {
  const { next } = core.extractToday(fx("london-2026-07.html"), L(780));
  assert.deepStrictEqual(next, { name: "Noon", time: "13:05" });
});

test("next prayer: 22:00 London -> Midnight 23:55", () => {
  const { next } = core.extractToday(fx("london-2026-07.html"), L(1320));
  assert.deepStrictEqual(next, { name: "Midnight", time: "23:55" });
});

test("next prayer wraps to Dawn when all times passed", () => {
  const { next } = core.extractToday(fx("london-2026-07.html"), L(1439)); // 23:59
  assert.strictEqual(next.name, "Dawn");
});

test("falls back to day-of-month when TodayRow missing", () => {
  const { times } = core.extractToday(fx("no-todayrow.html"), L(780));
  assert.strictEqual(times.noon, "13:05"); // July 2 row found by day==2
});

test("getPrayerTimes composes city + times + next via injected fetch", async () => {
  const fakeFetch = async () => fx("london-2026-07.html");
  const now = new Date("2026-07-02T12:00:00Z"); // 13:00 Europe/London (BST)
  const r = await core.getPrayerTimes("London", fakeFetch, now);
  assert.strictEqual(r.city, "London");
  assert.strictEqual(r.dawn, "02:29");
  assert.strictEqual(r.next.name, "Noon");
});
