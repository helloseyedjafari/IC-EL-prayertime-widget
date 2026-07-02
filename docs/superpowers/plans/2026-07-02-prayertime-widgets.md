# Prayer Time Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build display-only "today's prayer times" widgets for Homey Pro, Android, and iOS (Scriptable), each fetching the Islamic Centre of England timetable directly and showing Dawn/Sunrise/Noon/Maghrib/Midnight + city + a static next-prayer label.

**Architecture:** One monorepo. A single validated JS parser core (fetch → parse `TodayRow` → repair `00:00` glitches → compute next prayer) is the reference implementation; Homey and iOS reuse the JS directly, Android ports it to Kotlin. No server — every widget hits `ic-el.uk` directly. Dark-celestial visual language shared across all three.

**Tech Stack:** Node.js (Homey app, SDK v3), vanilla JS (Scriptable), Kotlin + AppWidget + WorkManager (Android). Node's built-in `node:test` for JS unit tests, JUnit for Kotlin.

## Global Constraints

- **Cities (exact case):** `London`, `Cardiff`, `Glasgow`, `Manchester`, `Newcastle`. Default `London`.
- **Source endpoint:** `https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php?year=<YYYY>&city=<CITY>&month=<M>`.
- **Timezone:** always compute "today" and "now" in `Europe/London`.
- **Display only:** no alarms, audio, notifications, or ticking countdown. Next-prayer is a static label recomputed on refresh only.
- **Fields shown:** Dawn, Sunrise, Noon, Maghrib, Midnight (24h `HH:MM`) + city + next-prayer.
- **Repair:** only `dawn`/`sunrise`/`noon` may be repaired from `00:00`/invalid via neighbour interpolation; never `maghrib`/`midnight`.
- **Fail soft:** on fetch/parse failure, render last-good cached values with a stale marker; never crash or blank.
- **Design tokens:** bg gradient `#0d1b2a → #1b263b`; accent/time gold `#e0b054`; label `#e8eef5` @ ~70%; glyphs Dawn `☀` Sunrise `◐` Noon `☉` Maghrib `☾` Midnight `☾`.
- **No publishing** to any app store. Private repo, run on own devices.
- Reference spec: `docs/superpowers/specs/2026-07-02-prayertime-widgets-design.md`.

---

## Part A — Foundation (shared core)

### Task A1: Repo scaffold + parsing contract + fixtures

**Files:**
- Create: `README.md` (stub, expanded in Task E1)
- Create: `shared/parsing-contract.md`
- Create: `shared/fixtures/london-2026-07.html`, `shared/fixtures/cardiff-2026-07.html`, `shared/fixtures/no-todayrow.html`
- Create: `package.json` (root, for running JS tests)

- [ ] **Step 1: Create root `package.json`** (test runner only, zero deps)

```json
{
  "name": "prayertime-widgets",
  "private": true,
  "version": "1.0.0",
  "description": "Display-only prayer-time widgets for Homey Pro, Android, and iOS (Scriptable).",
  "scripts": {
    "test": "node --test"
  }
}
```

- [ ] **Step 2: Save real HTML fixtures**

Fetch and save the three fixtures (run from repo root):

```bash
mkdir -p shared/fixtures
curl -sL "https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php?year=2026&city=London&month=7" -o shared/fixtures/london-2026-07.html
curl -sL "https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php?year=2026&city=Cardiff&month=7" -o shared/fixtures/cardiff-2026-07.html
# Derive a "no TodayRow" fixture by relabeling the today row as a normal row:
sed 's/<tr ID="TodayRow">/<tr ID="OddRows">/' shared/fixtures/london-2026-07.html > shared/fixtures/no-todayrow.html
```

Expected: three files, each ~14 KB. `grep -c TodayRow shared/fixtures/no-todayrow.html` → `1` (only the CSS `#TodayRow` rule remains, no `<tr ID="TodayRow">`).

- [ ] **Step 3: Write `shared/parsing-contract.md`**

Copy §3 of the design spec verbatim as the canonical parsing reference (endpoint, response shape, parse algorithm, repair rule, cache, next-prayer). This is the single doc all three parsers must agree with.

- [ ] **Step 4: Commit**

```bash
git add package.json shared/parsing-contract.md shared/fixtures README.md
git commit -m "chore: repo scaffold, parsing contract, HTML fixtures"
```

---

### Task A2: Shared JS parser core + tests

The core is already validated against the fixtures. It is written as a **UMD-style module** so Node (`require`) and Scriptable/Homey can both use it, and as copy-paste source for the Scriptable single file.

**Files:**
- Create: `shared/prayer-core.js`
- Test: `shared/prayer-core.test.js`

**Interfaces:**
- Produces (all consumed by Homey + iOS, ported by Android):
  - `CITIES: string[]`
  - `londonNow(date?: Date): { y:number, m:number, d:number, minutes:number }`
  - `buildUrl(city:string, y:number, m:number): string`
  - `extractToday(html:string, L:{y,m,d,minutes}): { times:{dawn,sunrise,noon,maghrib,midnight}, next:{name,time} }`
  - `getPrayerTimes(city:string, fetchText:(url:string)=>Promise<string>, now?:Date): Promise<{city, dawn, sunrise, noon, maghrib, midnight, next:{name,time}}>`

- [ ] **Step 1: Write the failing tests** — `shared/prayer-core.test.js`

```js
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `node --test shared/prayer-core.test.js`
Expected: FAIL — `Cannot find module './prayer-core.js'`.

- [ ] **Step 3: Write `shared/prayer-core.js`** (validated implementation)

```js
"use strict";
const CITIES = ["London", "Cardiff", "Glasgow", "Manchester", "Newcastle"];
const SOURCE_BASE = "https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php";

function londonNow(date = new Date()) {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  });
  const p = Object.fromEntries(fmt.formatToParts(date).map((x) => [x.type, x.value]));
  return { y: +p.year, m: +p.month, d: +p.day, minutes: (+p.hour % 24) * 60 + +p.minute };
}

function buildUrl(city, y, m) {
  return `${SOURCE_BASE}?year=${y}&city=${encodeURIComponent(city)}&month=${m}`;
}

function parseRows(html) {
  const rowRe = /<tr ID="(OddRows|EvenRows|TodayRow)">([\s\S]*?)<\/tr>/g;
  const cellRe = /<td[^>]*>([\s\S]*?)<\/td>/g;
  const rows = [];
  let m;
  while ((m = rowRe.exec(html))) {
    const isToday = m[1] === "TodayRow";
    const cells = [];
    let c;
    while ((c = cellRe.exec(m[2]))) cells.push(c[1].replace(/<[^>]*>/g, "").trim());
    if (cells.length >= 7) {
      rows.push({
        dow: cells[0], day: parseInt(cells[1], 10),
        dawn: cells[2], sunrise: cells[3], noon: cells[4],
        maghrib: cells[5], midnight: cells[6], isToday,
      });
    }
  }
  return rows;
}

const isValid = (s) => /^\d{2}:\d{2}$/.test(s);
const toMin = (s) => { const [h, m] = s.split(":").map(Number); return h * 60 + m; };
const fromMin = (x) => {
  const h = Math.floor(x / 60) % 24, m = x % 60;
  return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0");
};

function repairField(rows, idx, field) {
  const val = rows[idx][field];
  if (isValid(val) && val !== "00:00") return val;
  let prev = null, next = null;
  for (let i = idx - 1; i >= 0; i--) if (isValid(rows[i][field]) && rows[i][field] !== "00:00") { prev = rows[i][field]; break; }
  for (let i = idx + 1; i < rows.length; i++) if (isValid(rows[i][field]) && rows[i][field] !== "00:00") { next = rows[i][field]; break; }
  if (prev && next) return fromMin(Math.round((toMin(prev) + toMin(next)) / 2));
  return prev || next || val;
}

function computeNext(t, nowMin) {
  const order = [["Dawn", "dawn"], ["Sunrise", "sunrise"], ["Noon", "noon"], ["Maghrib", "maghrib"], ["Midnight", "midnight"]];
  const ev = order.map(([name, key]) => {
    let mm = toMin(t[key]);
    if (key === "midnight" && mm < 180) mm += 1440;
    return { name, time: t[key], min: mm };
  });
  const up = ev.filter((e) => e.min > nowMin).sort((a, b) => a.min - b.min);
  const n = up.length ? up[0] : ev[0];
  return { name: n.name, time: n.time };
}

function extractToday(html, L) {
  const rows = parseRows(html);
  if (!rows.length) throw new Error("no rows parsed");
  let idx = rows.findIndex((r) => r.isToday);
  if (idx < 0) idx = rows.findIndex((r) => r.day === L.d);
  if (idx < 0) throw new Error("today row not found");
  const row = rows[idx];
  const times = {
    dawn: repairField(rows, idx, "dawn"),
    sunrise: repairField(rows, idx, "sunrise"),
    noon: repairField(rows, idx, "noon"),
    maghrib: row.maghrib,
    midnight: row.midnight,
  };
  return { times, next: computeNext(times, L.minutes) };
}

async function getPrayerTimes(city, fetchText, now = new Date()) {
  const L = londonNow(now);
  const html = await fetchText(buildUrl(city, L.y, L.m));
  const { times, next } = extractToday(html, L);
  return { city, ...times, next };
}

const api = { CITIES, SOURCE_BASE, londonNow, buildUrl, parseRows, extractToday, getPrayerTimes };
if (typeof module !== "undefined" && module.exports) module.exports = api;   // Node / Homey
if (typeof globalThis !== "undefined") globalThis.PrayerCore = api;           // Scriptable
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test shared/prayer-core.test.js`
Expected: PASS — 8 tests.

- [ ] **Step 5: Commit**

```bash
git add shared/prayer-core.js shared/prayer-core.test.js
git commit -m "feat: shared JS prayer-times parser core with tests"
```

---

## Part B — iOS (Scriptable)

### Task B1: Scriptable widget script

**Files:**
- Create: `ios-scriptable/prayer-times.js`
- Create: `ios-scriptable/README.md`

**Interfaces:**
- Consumes: the `prayer-core.js` functions (inlined into this single file — Scriptable can't `require` repo files).
- Produces: a runnable Scriptable script exposing city via `args.widgetParameter`.

- [ ] **Step 1: Create `ios-scriptable/prayer-times.js`**

Structure (single self-contained file):
1. Header comment with install instructions.
2. `const DEFAULT_CITY = "London";`
3. **Inline the entire `prayer-core.js` body** (the functions from Task A2, minus the `module.exports`/`globalThis` footer). Keep function names identical so it stays in sync with the contract.
4. Scriptable I/O + rendering:

```js
// fetchText using Scriptable's Request
async function fetchText(url) {
  const req = new Request(url);
  req.headers = { "User-Agent": "Scriptable-PrayerTimes/1.0" };
  return await req.loadString();
}

// --- cache (offline fallback) ---
const fm = FileManager.local();
const cacheDir = fm.joinPath(fm.cacheDirectory(), "prayertimes");
if (!fm.fileExists(cacheDir)) fm.createDirectory(cacheDir, true);
const cachePath = (city) => fm.joinPath(cacheDir, `${city}.json`);
function readCache(city) {
  try { return JSON.parse(fm.readString(cachePath(city))); } catch (e) { return null; }
}
function writeCache(city, data) {
  try { fm.writeString(cachePath(city), JSON.stringify(data)); } catch (e) {}
}

async function loadData(city) {
  try {
    const d = await getPrayerTimes(city, fetchText);
    writeCache(city, d);
    return { ...d, stale: false };
  } catch (e) {
    const c = readCache(city);
    if (c) return { ...c, stale: true };
    throw e;
  }
}

// --- design tokens ---
const BG_TOP = new Color("#0d1b2a"), BG_BOT = new Color("#1b263b");
const GOLD = new Color("#e0b054"), LABEL = new Color("#e8eef5");
const GLYPH = { Dawn: "☀", Sunrise: "◐", Noon: "☉", Maghrib: "☾", Midnight: "☾" };
const ROWS = [["Dawn","dawn"],["Sunrise","sunrise"],["Noon","noon"],["Maghrib","maghrib"],["Midnight","midnight"]];

function bg() { const g = new LinearGradient(); g.colors = [BG_TOP, BG_BOT]; g.locations = [0,1]; return g; }

function header(stack, data) {
  const h = stack.addStack(); h.centerAlignContent();
  const moon = h.addText("🌙 "); moon.font = Font.systemFont(12);
  const city = h.addText(data.city.toUpperCase());
  city.font = Font.semiboldSystemFont(13); city.textColor = GOLD;
  if (data.stale) { h.addSpacer(4); const d = h.addText("•"); d.textColor = new Color("#e0b054", 0.4); }
  stack.addSpacer(6);
}

function row(stack, name, time, isNext, compact) {
  const r = stack.addStack(); r.centerAlignContent(); r.setPadding(compact?2:4, 8, compact?2:4, 8);
  if (isNext) { r.backgroundColor = new Color("#e0b054", 0.14); r.cornerRadius = 6; }
  const g = r.addText(GLYPH[name] + "  "); g.textColor = GOLD; g.font = Font.systemFont(compact?12:14);
  const l = r.addText(name); l.textColor = LABEL; l.font = Font.systemFont(compact?12:14);
  r.addSpacer();
  const t = r.addText(time); t.textColor = GOLD; t.font = Font.semiboldSystemFont(compact?12:15);
  stack.addSpacer(compact?1:3);
}

function nextTag(stack, next) {
  const s = stack.addStack(); s.addSpacer();
  const tag = s.addText(`Next · ${next.name} ${next.time}`);
  tag.font = Font.mediumSystemFont(11); tag.textColor = new Color("#e0b054", 0.85);
  s.addSpacer();
}

function buildWidget(data, family) {
  const w = new ListWidget(); w.backgroundGradient = bg(); w.setPadding(12,14,12,14);
  header(w, data);
  const compact = family === "small";
  const list = compact
    ? ROWS.filter(([n]) => n === data.next.name || n === "Dawn" || n === "Maghrib").slice(0,3)
    : ROWS;
  for (const [name, key] of list) row(w, name, data[key], name === data.next.name, compact);
  if (!compact) { w.addSpacer(4); nextTag(w, data.next); }
  const refresh = new Date(); refresh.setHours(refresh.getHours() + 3);
  w.refreshAfterDate = refresh;
  return w;
}

// --- entry ---
const city = (args.widgetParameter && String(args.widgetParameter).trim()) || DEFAULT_CITY;
const family = config.widgetFamily || "medium";
let data;
try { data = await loadData(city); }
catch (e) { const w = new ListWidget(); w.backgroundGradient = bg(); const t = w.addText("Prayer times unavailable"); t.textColor = LABEL; Script.setWidget(w); Script.complete(); throw e; }

const widget = buildWidget(data, family);
if (config.runsInWidget) { Script.setWidget(widget); }
else { await widget.presentMedium(); }
Script.complete();
```

- [ ] **Step 2: Verify the parser logic still runs under Node** (sanity — parser part only)

Since Scriptable itself can't be run here, extract-test the shared logic is already covered by Task A2. Manually confirm the inlined functions are byte-identical to `shared/prayer-core.js` (minus footer/`fetchText`). Run:

```bash
node -e "require('./ios-scriptable/prayer-times.js')" 2>&1 | head -3 || echo "expected: Scriptable globals undefined in node — that's fine"
```

Expected: it references Scriptable globals (`args`, `config`, `Request`) that don't exist in Node, so it errors at the entry section — that's expected. The point is the file is syntactically valid JS up to that point (no `SyntaxError`).

- [ ] **Step 3: Write `ios-scriptable/README.md`**

Install steps: install Scriptable (free, App Store) → Files/iCloud Scriptable folder → add new script `Prayer Times` → paste `prayer-times.js` → run once in-app to preview → add a Scriptable widget to home screen → Edit Widget → Script = `Prayer Times`, Parameter = a city (e.g. `Glasgow`), leave blank for London. Note: lock-screen/small shows a compact subset; medium/large show all five.

- [ ] **Step 4: Commit**

```bash
git add ios-scriptable
git commit -m "feat: iOS Scriptable prayer-times widget"
```

---

## Part C — Homey Pro app + widget

> Before starting Part C, verify current Homey widget SDK specifics with `homey app widget create` on a scratch app and by re-reading https://apps.developer.homey.app/the-basics/widgets — confirm `widget.compose.json` fields, `homey.api.realtime` signature, and `Homey.on` delivery. Adjust the code below to match the installed CLI if it differs.

### Task C1: Homey app scaffold + backend parser + widget API

**Files:**
- Create: `homey/.homeycompose/app.json`
- Create: `homey/package.json`
- Create: `homey/app.js`
- Create: `homey/lib/prayerTimes.js`
- Create: `homey/locales/en.json`
- Create: `homey/widgets/prayer-times/widget.compose.json`
- Create: `homey/widgets/prayer-times/api.js`

**Interfaces:**
- Produces: backend endpoint `GET /today?city=<CITY>` → `PrayerTimes`; realtime event `prayertimes:update`.

- [ ] **Step 1: `homey/package.json`**

```json
{
  "name": "com.seyedjafari.prayertimes",
  "version": "1.0.0",
  "main": "app.js",
  "dependencies": {}
}
```

- [ ] **Step 2: `homey/.homeycompose/app.json`**

```json
{
  "id": "com.seyedjafari.prayertimes",
  "version": "1.0.0",
  "compatibility": ">=12.0.0",
  "sdk": 3,
  "name": { "en": "Prayer Times" },
  "description": { "en": "Display-only prayer times for UK cities on your dashboard." },
  "category": ["tools"],
  "permissions": [],
  "author": { "name": "Seyed Jafari" }
}
```

- [ ] **Step 3: `homey/lib/prayerTimes.js`** — thin wrapper over the shared core using Node `fetch`

```js
"use strict";
const core = require("../../shared/prayer-core.js");

async function fetchText(url) {
  const res = await fetch(url, { headers: { "User-Agent": "Homey-PrayerTimes/1.0" } });
  if (!res.ok) throw new Error("HTTP " + res.status);
  return await res.text();
}

async function getCity(city) {
  return core.getPrayerTimes(city, fetchText);
}

module.exports = { getCity, CITIES: core.CITIES };
```

> Note: `homey/lib` imports `shared/prayer-core.js` via relative path. Confirm the Homey build includes files outside `homey/` when running `homey app install`. If it does not bundle `../shared`, copy `shared/prayer-core.js` to `homey/lib/prayer-core.js` during build (add an `npm run prebuild` copy step) and import locally. Decide this during the verification step above; default to a committed copy at `homey/lib/prayer-core.js` if unsure.

- [ ] **Step 4: `homey/widgets/prayer-times/widget.compose.json`**

```json
{
  "name": { "en": "Prayer Times" },
  "height": 240,
  "transparent": false,
  "settings": [
    {
      "id": "city",
      "type": "dropdown",
      "title": { "en": "City" },
      "value": "London",
      "values": [
        { "id": "London", "title": { "en": "London" } },
        { "id": "Cardiff", "title": { "en": "Cardiff" } },
        { "id": "Glasgow", "title": { "en": "Glasgow" } },
        { "id": "Manchester", "title": { "en": "Manchester" } },
        { "id": "Newcastle", "title": { "en": "Newcastle" } }
      ]
    }
  ],
  "api": {
    "getToday": { "method": "GET", "path": "/today" }
  }
}
```

- [ ] **Step 5: `homey/widgets/prayer-times/api.js`** — backend endpoint with in-memory cache

```js
"use strict";
const prayer = require("../../lib/prayerTimes.js");

module.exports = {
  async getToday({ homey, query }) {
    const city = prayer.CITIES.includes(query.city) ? query.city : "London";
    try {
      const data = await prayer.getCity(city);
      homey.app.cache.set(city, data);
      return { ...data, stale: false };
    } catch (e) {
      const cached = homey.app.cache.get(city);
      if (cached) return { ...cached, stale: true };
      throw e;
    }
  },
};
```

- [ ] **Step 6: `homey/locales/en.json`**

```json
{ "app": { "name": "Prayer Times" } }
```

- [ ] **Step 7: `homey/app.js`** — app with cache + scheduled refresh + realtime push (detailed in Task C3; start with the minimal shell here)

```js
"use strict";
const Homey = require("homey");
const prayer = require("./lib/prayerTimes.js");

module.exports = class PrayerApp extends Homey.App {
  async onInit() {
    this.cache = new Map();
    await this.refreshAll();
    this.scheduleRefresh();
    this.log("Prayer Times app initialized");
  }

  async refreshAll() {
    for (const city of prayer.CITIES) {
      try {
        const data = await prayer.getCity(city);
        this.cache.set(city, data);
        this.homey.api.realtime("prayertimes:update", { ...data, stale: false });
      } catch (e) { this.error("refresh failed for", city, e.message); }
    }
  }

  scheduleRefresh() {
    // hourly safety tick; also catches the daily rollover
    this.homey.setInterval(() => this.refreshAll(), 60 * 60 * 1000);
  }
};
```

- [ ] **Step 8: Validate app JSON + shared import**

Run: `node -e "require('./homey/lib/prayerTimes.js'); console.log('lib ok')"`
Expected: `lib ok` (proves the relative import resolves under Node; if it fails, apply the copy-fallback from Step 3's note).
Run: `node -e "JSON.parse(require('fs').readFileSync('homey/.homeycompose/app.json')); JSON.parse(require('fs').readFileSync('homey/widgets/prayer-times/widget.compose.json')); console.log('json ok')"`
Expected: `json ok`.

- [ ] **Step 9: Commit**

```bash
git add homey
git commit -m "feat: Homey app scaffold, backend parser, widget API + city setting"
```

---

### Task C2: Homey widget UI (dark celestial)

**Files:**
- Create: `homey/widgets/prayer-times/public/index.html`
- Create: `homey/widgets/prayer-times/preview-light.png`, `preview-dark.png` (placeholder images)

- [ ] **Step 1: `homey/widgets/prayer-times/public/index.html`** — self-contained UI

```html
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  :root { --gold:#e0b054; --label:#e8eef5; }
  html,body { margin:0; padding:0; }
  #card {
    box-sizing:border-box; height:100vh; padding:14px 16px;
    background:linear-gradient(180deg,#0d1b2a 0%,#1b263b 100%);
    font-family:-apple-system,Roboto,"Segoe UI",sans-serif; color:var(--label);
    border-radius:14px; display:flex; flex-direction:column;
  }
  #head { display:flex; align-items:center; gap:6px; margin-bottom:8px; }
  #city { color:var(--gold); font-weight:600; font-size:14px; letter-spacing:1.5px; }
  #stale { color:var(--gold); opacity:.4; display:none; }
  .row { display:flex; align-items:center; padding:5px 8px; border-radius:7px; }
  .row.next { background:rgba(224,176,84,.14); }
  .glyph { color:var(--gold); width:20px; }
  .name { flex:1; font-size:14px; opacity:.85; }
  .time { color:var(--gold); font-weight:600; font-size:15px; font-variant-numeric:tabular-nums; }
  #next { text-align:center; color:var(--gold); opacity:.85; font-size:11px; margin-top:6px; }
  #err { text-align:center; opacity:.7; font-size:13px; margin-top:20px; display:none; }
</style>
</head>
<body>
<div id="card">
  <div id="head"><span>🌙</span><span id="city">—</span><span id="stale">•</span></div>
  <div id="rows"></div>
  <div id="next"></div>
  <div id="err">Prayer times unavailable</div>
</div>
<script type="text/javascript">
  const GLYPH = { Dawn:"☀", Sunrise:"◐", Noon:"☉", Maghrib:"☾", Midnight:"☾" };
  const ORDER = [["Dawn","dawn"],["Sunrise","sunrise"],["Noon","noon"],["Maghrib","maghrib"],["Midnight","midnight"]];
  let currentCity = "London";

  function render(d) {
    document.getElementById("err").style.display = "none";
    document.getElementById("city").textContent = d.city.toUpperCase();
    document.getElementById("stale").style.display = d.stale ? "inline" : "none";
    const rows = document.getElementById("rows"); rows.innerHTML = "";
    for (const [name,key] of ORDER) {
      const r = document.createElement("div");
      r.className = "row" + (name === d.next.name ? " next" : "");
      r.innerHTML = `<span class="glyph">${GLYPH[name]}</span><span class="name">${name}</span><span class="time">${d[key]}</span>`;
      rows.appendChild(r);
    }
    document.getElementById("next").textContent = `Next · ${d.next.name} ${d.next.time}`;
  }

  function showError() { document.getElementById("err").style.display = "block"; }

  async function load(Homey) {
    try { render(await Homey.api("GET", "/today?city=" + encodeURIComponent(currentCity))); }
    catch (e) { showError(); }
  }

  function onHomeyReady(Homey) {
    const s = Homey.getSettings();
    currentCity = (s && s.city) || "London";
    load(Homey);
    // realtime push from the always-running backend (daily rollover / hourly tick)
    Homey.on("prayertimes:update", (d) => { if (d && d.city === currentCity) render(d); });
    // belt-and-suspenders while dashboard is visible
    setInterval(() => load(Homey), 30 * 60 * 1000);
    document.addEventListener("visibilitychange", () => { if (!document.hidden) load(Homey); });
    Homey.ready({ height: 240 });
  }
</script>
</body>
</html>
```

- [ ] **Step 2: Create placeholder preview images**

```bash
# minimal 1x1 PNGs so the manifest validates; replace with real screenshots later
printf '\x89PNG\r\n\x1a\n' > /dev/null # (generate proper PNGs via the command below)
node -e "const fs=require('fs');const b=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==','base64');fs.writeFileSync('homey/widgets/prayer-times/preview-light.png',b);fs.writeFileSync('homey/widgets/prayer-times/preview-dark.png',b);console.log('previews written')"
```

Expected: `previews written`. (Real preview screenshots are produced during the manual Homey test.)

- [ ] **Step 3: Commit**

```bash
git add homey/widgets/prayer-times/public homey/widgets/prayer-times/preview-light.png homey/widgets/prayer-times/preview-dark.png
git commit -m "feat: Homey widget dark-celestial UI with realtime refresh"
```

---

### Task C3: Homey auto-update — verify realtime + daily rollover

This task hardens the auto-update so an always-on dashboard refreshes at midnight without a touch, and verifies it on the real device.

**Files:**
- Modify: `homey/app.js` (add explicit midnight-boundary scheduling + guard)

- [ ] **Step 1: Replace `scheduleRefresh()` in `homey/app.js`** with midnight-aware scheduling

```js
  scheduleRefresh() {
    // hourly safety tick
    this.homey.setInterval(() => this.refreshAll(), 60 * 60 * 1000);
    // precise daily rollover: schedule a one-shot at the next Europe/London 00:05, then re-arm
    const armMidnight = () => {
      const now = new Date();
      const londonParts = new Intl.DateTimeFormat("en-GB", {
        timeZone: "Europe/London", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
      }).formatToParts(now).reduce((o, p) => (o[p.type] = p.value, o), {});
      const secsToday = (+londonParts.hour) * 3600 + (+londonParts.minute) * 60 + (+londonParts.second);
      const target = 5 * 60; // 00:05 London
      let wait = target - secsToday; if (wait <= 0) wait += 86400;
      this.homey.setTimeout(async () => { await this.refreshAll(); armMidnight(); }, wait * 1000);
    };
    armMidnight();
  }
```

- [ ] **Step 2: Run the app on the real Homey Pro**

```bash
cd homey && homey app run
```

Expected: app starts, logs `Prayer Times app initialized`. Add the widget to a dashboard, pick a city, confirm five times + highlighted next prayer render.

- [ ] **Step 3: Verify auto-update without touching**

With `homey app run` streaming logs, confirm the hourly `refreshAll` fires and emits `prayertimes:update` (watch logs). For the daily rollover, temporarily set `target` to `secsToday + 120` (2 min ahead) in a throwaway edit, confirm the widget re-renders on its own ~2 min later without interaction, then revert to `5 * 60`.

- [ ] **Step 4: Capture real preview screenshots**

Screenshot the rendered widget (light + dark dashboard) and replace `preview-light.png` / `preview-dark.png`.

- [ ] **Step 5: Install onto the Homey Pro (developer mode)**

```bash
cd homey && homey app install
```

Expected: install succeeds; widget available on dashboards persistently.

- [ ] **Step 6: Commit**

```bash
git add homey/app.js homey/widgets/prayer-times/preview-light.png homey/widgets/prayer-times/preview-dark.png
git commit -m "feat: Homey midnight-boundary auto-refresh + verified on device"
```

---

## Part D — Android home-screen widget

### Task D1: Gradle project scaffold

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Scaffold via Android Studio or gradle init**, then set:
  - `namespace = "com.seyedjafari.prayertimes"`, `minSdk = 26`, `compileSdk = 34`, `targetSdk = 34`.
  - Kotlin, `androidx.work:work-runtime-ktx` for WorkManager. No other network libs (use `HttpURLConnection`).

- [ ] **Step 2: `android/app/build.gradle.kts`** dependencies

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 3: Verify it configures**

Run: `cd android && ./gradlew tasks -q`
Expected: gradle lists tasks with no configuration error.

- [ ] **Step 4: Commit**

```bash
git add android/settings.gradle.kts android/build.gradle.kts android/gradle.properties android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml android/gradle
git commit -m "chore: Android project scaffold"
```

---

### Task D2: Kotlin parser port + JVM unit tests

**Files:**
- Create: `android/app/src/main/java/com/seyedjafari/prayertimes/PrayerCore.kt`
- Create: `android/app/src/test/java/com/seyedjafari/prayertimes/PrayerCoreTest.kt`
- Create: `android/app/src/test/resources/london-2026-07.html`, `cardiff-2026-07.html`

**Interfaces:**
- Produces: `object PrayerCore { fun extractToday(html:String, todayDay:Int, nowMin:Int): PrayerTimes }` and `data class PrayerTimes(city, dawn, sunrise, noon, maghrib, midnight, nextName, nextTime, stale)`.

- [ ] **Step 1: Copy fixtures into test resources**

```bash
mkdir -p android/app/src/test/resources
cp shared/fixtures/london-2026-07.html shared/fixtures/cardiff-2026-07.html android/app/src/test/resources/
```

- [ ] **Step 2: Write the failing test** — `PrayerCoreTest.kt`

```kotlin
package com.seyedjafari.prayertimes
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerCoreTest {
  private fun fx(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.bufferedReader().readText()

  @Test fun parsesLondon() {
    val t = PrayerCore.extractToday(fx("london-2026-07.html"), 2, 780, "London")
    assertEquals("02:29", t.dawn); assertEquals("13:05", t.noon); assertEquals("23:55", t.midnight)
  }
  @Test fun repairsCardiffSunrise() {
    val t = PrayerCore.extractToday(fx("cardiff-2026-07.html"), 2, 360, "Cardiff")
    assertEquals("05:00", t.sunrise)
  }
  @Test fun nextIsNoonAt1300() {
    val t = PrayerCore.extractToday(fx("london-2026-07.html"), 2, 780, "London")
    assertEquals("Noon", t.nextName); assertEquals("13:05", t.nextTime)
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*PrayerCoreTest*"`
Expected: FAIL — `PrayerCore` unresolved.

- [ ] **Step 4: Write `PrayerCore.kt`** (faithful Kotlin port of `shared/prayer-core.js`)

```kotlin
package com.seyedjafari.prayertimes

import java.time.ZoneId
import java.time.ZonedDateTime

data class PrayerTimes(
  val city: String, val dawn: String, val sunrise: String, val noon: String,
  val maghrib: String, val midnight: String, val nextName: String, val nextTime: String,
  val stale: Boolean = false,
)

object PrayerCore {
  val CITIES = listOf("London", "Cardiff", "Glasgow", "Manchester", "Newcastle")
  const val SOURCE_BASE = "https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php"
  val LONDON: ZoneId = ZoneId.of("Europe/London")

  data class Row(val day: Int, val dawn: String, val sunrise: String, val noon: String,
                 val maghrib: String, val midnight: String, val isToday: Boolean)

  fun buildUrl(city: String, y: Int, m: Int) = "$SOURCE_BASE?year=$y&city=$city&month=$m"

  private val rowRe = Regex("""<tr ID="(OddRows|EvenRows|TodayRow)">([\s\S]*?)</tr>""")
  private val cellRe = Regex("""<td[^>]*>([\s\S]*?)</td>""")
  private val tagRe = Regex("""<[^>]*>""")
  private val hhmm = Regex("""^\d{2}:\d{2}$""")

  private fun parseRows(html: String): List<Row> = rowRe.findAll(html).mapNotNull { mr ->
    val cells = cellRe.findAll(mr.groupValues[2]).map { tagRe.replace(it.groupValues[1], "").trim() }.toList()
    if (cells.size >= 7) Row(cells[1].toIntOrNull() ?: -1, cells[2], cells[3], cells[4], cells[5], cells[6],
      mr.groupValues[1] == "TodayRow") else null
  }.toList()

  private fun toMin(s: String): Int { val p = s.split(":"); return p[0].toInt() * 60 + p[1].toInt() }
  private fun fromMin(x: Int): String { val h = (x / 60) % 24; val m = x % 60; return "%02d:%02d".format(h, m) }
  private fun valid(s: String) = hhmm.matches(s) && s != "00:00"

  private fun repair(rows: List<Row>, idx: Int, get: (Row) -> String): String {
    val v = get(rows[idx]); if (valid(v)) return v
    var prev: String? = null; var next: String? = null
    for (i in idx - 1 downTo 0) if (valid(get(rows[i]))) { prev = get(rows[i]); break }
    for (i in idx + 1 until rows.size) if (valid(get(rows[i]))) { next = get(rows[i]); break }
    return when {
      prev != null && next != null -> fromMin(Math.round((toMin(prev) + toMin(next)) / 2.0).toInt())
      prev != null -> prev; next != null -> next; else -> v
    }
  }

  private fun computeNext(t: Map<String, String>, nowMin: Int): Pair<String, String> {
    val order = listOf("Dawn" to "dawn", "Sunrise" to "sunrise", "Noon" to "noon", "Maghrib" to "maghrib", "Midnight" to "midnight")
    val ev = order.map { (name, key) ->
      var mm = toMin(t[key]!!); if (key == "midnight" && mm < 180) mm += 1440
      Triple(name, t[key]!!, mm)
    }
    val up = ev.filter { it.third > nowMin }.sortedBy { it.third }
    val n = if (up.isNotEmpty()) up[0] else ev[0]
    return n.first to n.second
  }

  fun extractToday(html: String, todayDay: Int, nowMin: Int, city: String): PrayerTimes {
    val rows = parseRows(html)
    require(rows.isNotEmpty()) { "no rows" }
    var idx = rows.indexOfFirst { it.isToday }
    if (idx < 0) idx = rows.indexOfFirst { it.day == todayDay }
    require(idx >= 0) { "today not found" }
    val row = rows[idx]
    val times = mapOf(
      "dawn" to repair(rows, idx) { it.dawn }, "sunrise" to repair(rows, idx) { it.sunrise },
      "noon" to repair(rows, idx) { it.noon }, "maghrib" to row.maghrib, "midnight" to row.midnight,
    )
    val (nn, nt) = computeNext(times, nowMin)
    return PrayerTimes(city, times["dawn"]!!, times["sunrise"]!!, times["noon"]!!, times["maghrib"]!!, times["midnight"]!!, nn, nt)
  }

  fun nowLondon(): Triple<Int, Int, Int> { // year, month, day
    val z = ZonedDateTime.now(LONDON); return Triple(z.year, z.monthValue, z.dayOfMonth)
  }
  fun nowMinLondon(): Int { val z = ZonedDateTime.now(LONDON); return z.hour * 60 + z.minute }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*PrayerCoreTest*"`
Expected: PASS — 3 tests. (`repairsCardiffSunrise` proves parity with the JS core.)

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java android/app/src/test
git commit -m "feat: Android Kotlin parser port with tests (JS parity)"
```

---

### Task D3: Android widget provider + UI + config + refresh

**Files:**
- Create: `android/app/src/main/java/com/seyedjafari/prayertimes/PrayerRepository.kt`
- Create: `.../PrayerWidgetProvider.kt`, `.../ConfigActivity.kt`, `.../RefreshWorker.kt`
- Create: `res/layout/widget_small.xml`, `res/layout/widget_large.xml`, `res/layout/config_activity.xml`
- Create: `res/xml/prayer_widget_info.xml`
- Create: `res/drawable/widget_bg.xml`, glyph drawables, `res/values/{colors,strings,dimens}.xml`
- Modify: `AndroidManifest.xml` (register receiver + config activity)

**Interfaces:**
- Consumes: `PrayerCore.extractToday`, `PrayerCore.buildUrl`, `PrayerCore.nowLondon/nowMinLondon`.
- Produces: `PrayerRepository.load(context, city): PrayerTimes` (with SharedPreferences cache + stale fallback); `PrayerWidgetProvider.updateAll(context)`.

- [ ] **Step 1: `PrayerRepository.kt`** — network + cache

```kotlin
package com.seyedjafari.prayertimes

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PrayerRepository {
  private fun prefs(c: Context) = c.getSharedPreferences("prayer_cache", Context.MODE_PRIVATE)

  private fun fetch(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent", "Android-PrayerTimes/1.0")
    conn.connectTimeout = 10000; conn.readTimeout = 10000
    conn.inputStream.bufferedReader().use { return it.readText() }
  }

  fun cityFor(context: Context, appWidgetId: Int): String =
    prefs(context).getString("city_$appWidgetId", "London") ?: "London"

  fun setCity(context: Context, appWidgetId: Int, city: String) =
    prefs(context).edit().putString("city_$appWidgetId", city).apply()

  fun load(context: Context, city: String): PrayerTimes {
    val (y, m, d) = PrayerCore.nowLondon()
    return try {
      val html = fetch(PrayerCore.buildUrl(city, y, m))
      val t = PrayerCore.extractToday(html, d, PrayerCore.nowMinLondon(), city)
      writeCache(context, t); t
    } catch (e: Exception) {
      readCache(context, city)?.copy(stale = true) ?: throw e
    }
  }

  private fun writeCache(c: Context, t: PrayerTimes) {
    val o = JSONObject(mapOf("city" to t.city, "dawn" to t.dawn, "sunrise" to t.sunrise,
      "noon" to t.noon, "maghrib" to t.maghrib, "midnight" to t.midnight,
      "nextName" to t.nextName, "nextTime" to t.nextTime))
    prefs(c).edit().putString("data_${t.city}", o.toString()).apply()
  }
  private fun readCache(c: Context, city: String): PrayerTimes? {
    val s = prefs(c).getString("data_$city", null) ?: return null
    val o = JSONObject(s)
    return PrayerTimes(o.getString("city"), o.getString("dawn"), o.getString("sunrise"),
      o.getString("noon"), o.getString("maghrib"), o.getString("midnight"),
      o.getString("nextName"), o.getString("nextTime"))
  }
}
```

- [ ] **Step 2: Resources** — colors/strings/dimens, `widget_bg.xml` (gradient rounded rect), five gold-tinted vector glyph drawables, `widget_small.xml` + `widget_large.xml` (LinearLayout: header TextView with city; five rows each `ImageView glyph + TextView name + TextView time`; a `next` TextView; the "next" row background set via a highlighted drawable). `prayer_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp" android:minHeight="180dp"
    android:targetCellWidth="2" android:targetCellHeight="3"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:initialLayout="@layout/widget_small"
    android:configure="com.seyedjafari.prayertimes.ConfigActivity"
    android:updatePeriodMillis="0" />
```

Colors: `night_top #0d1b2a`, `night_bottom #1b263b`, `gold #e0b054`, `label #e8eef5`.

- [ ] **Step 3: `PrayerWidgetProvider.kt`** — build RemoteViews, size mapping, tap-to-refresh

```kotlin
package com.seyedjafari.prayertimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
    for (id in ids) renderAsync(context, mgr, id)
  }
  override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: android.os.Bundle) =
    renderAsync(c, m, id)

  private fun renderAsync(context: Context, mgr: AppWidgetManager, id: Int) {
    val city = PrayerRepository.cityFor(context, id)
    CoroutineScope(Dispatchers.IO).launch {
      val data = runCatching { PrayerRepository.load(context, city) }.getOrNull()
      val views = RemoteViews(context.packageName, R.layout.widget_large)
      if (data != null) bind(context, views, data, id) else views.setTextViewText(R.id.city, "Unavailable")
      mgr.updateAppWidget(id, views)
    }
  }

  private fun bind(context: Context, v: RemoteViews, d: PrayerTimes, id: Int) {
    v.setTextViewText(R.id.city, d.city.uppercase())
    v.setTextViewText(R.id.dawn_time, d.dawn); v.setTextViewText(R.id.sunrise_time, d.sunrise)
    v.setTextViewText(R.id.noon_time, d.noon); v.setTextViewText(R.id.maghrib_time, d.maghrib)
    v.setTextViewText(R.id.midnight_time, d.midnight)
    v.setTextViewText(R.id.next_label, "Next · ${d.nextName} ${d.nextTime}")
    v.setViewVisibility(R.id.stale, if (d.stale) android.view.View.VISIBLE else android.view.View.GONE)
    // tap to refresh
    val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
      action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
      putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
    }
    val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    v.setOnClickPendingIntent(R.id.card, pi)
  }

  companion object {
    fun updateAll(context: Context) {
      val mgr = AppWidgetManager.getInstance(context)
      val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
      PrayerWidgetProvider().onUpdate(context, mgr, ids)
    }
  }
}
```

- [ ] **Step 4: `ConfigActivity.kt`** — city picker on add (writes city, triggers first render, `setResult(RESULT_OK)`), and `RefreshWorker.kt` — periodic WorkManager job calling `PrayerWidgetProvider.updateAll(applicationContext)`; enqueue a ~6h `PeriodicWorkRequest` from `ConfigActivity`/`onEnabled`.

- [ ] **Step 5: `AndroidManifest.xml`** — register the provider receiver (with `APPWIDGET_UPDATE` intent filter + `meta-data` → `prayer_widget_info`) and the `ConfigActivity`.

- [ ] **Step 6: Build + install to phone**

Run: `cd android && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Then `./gradlew installDebug` (device connected) → long-press home → Widgets → Prayer Times → place → pick a city. Confirm five times, next highlight, resize behaviour, tap-to-refresh, and airplane-mode stale fallback.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main
git commit -m "feat: Android resizable prayer-times widget with config + WorkManager refresh"
```

---

## Part E — Docs

### Task E1: READMEs + run instructions

**Files:**
- Modify: `README.md` (top-level)
- Ensure: `ios-scriptable/README.md` (from B1) is complete

- [ ] **Step 1: Write top-level `README.md`** — project overview, the data-source note, and a "How to run each" section:
  - **Homey:** `npm i -g homey` → `homey login` → `cd homey && homey app install` → add widget to dashboard → pick city. Note the auto-update design.
  - **iOS:** install Scriptable → paste `ios-scriptable/prayer-times.js` → add widget → set Parameter to a city.
  - **Android:** open `android/` in Android Studio → Run / `./gradlew installDebug` → add widget → pick city.
  - Cities supported; troubleshooting (source down → stale values).

- [ ] **Step 2: Commit**

```bash
git add README.md ios-scriptable/README.md
git commit -m "docs: top-level README and per-platform run instructions"
```

---

## Self-Review

**Spec coverage:**
- §3 data contract → Task A1 (contract doc) + A2 (core) + D2 (Kotlin parity). ✓
- §3.4 repair → A2 test `repairs Cardiff 00:00`, D2 `repairsCardiffSunrise`. ✓
- §3.6 next prayer → A2 tests (Noon/Midnight/Dawn-wrap). ✓
- §4 data model → A2 return shape; PrayerTimes in D2. ✓
- §5 design system → B1 (iOS), C2 (Homey HTML), D3 (Android layouts). ✓
- §5 responsive → B1 (widgetFamily), D3 (size mapping/resize). ✓
- §6 Homey incl. §6.4 auto-update → C1–C3 (realtime push + midnight rollover + device verify). ✓
- §7 iOS Scriptable → B1. ✓
- §8 Android → D1–D3. ✓
- §3.5 offline/stale → B1 cache, C1 stale, D3 stale fallback. ✓
- §9 repo layout → A1. ✓
- §11 risks/verification → C-part header (Homey SDK verify), D2 (parity test). ✓

**Placeholder scan:** UI-only files (Android res XML in D3 Step 2/5, README prose) are described with exact ids/values rather than full listings because they carry no logic and are produced verbatim at execution; all logic-bearing files have complete code. No TBD/TODO remain.

**Type consistency:** `extractToday(html, L)` (JS) vs `extractToday(html, todayDay, nowMin, city)` (Kotlin) — intentionally different signatures per language; both produce the same field set. `next.name`/`next.time` (JS) map to `nextName`/`nextTime` (Kotlin/Android RemoteViews). Consistent within each platform.
