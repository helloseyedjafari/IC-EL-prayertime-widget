// Prayer Times — Scriptable widget
// -------------------------------------------------------------
// Shows today's Dawn / Sunrise / Noon / Maghrib / Midnight for a UK city
// (London, Cardiff, Glasgow, Manchester, Newcastle) from ic-el.uk.
// Display only — no alarms, no audio.
//
// SETUP:
//   1. Install Scriptable (free, App Store).
//   2. New script named "Prayer Times", paste this whole file.
//   3. Run once in-app to preview.
//   4. Add a Scriptable widget to your home screen.
//   5. Edit Widget → Script = "Prayer Times", Parameter = a city (e.g. Glasgow).
//      Leave the Parameter blank for London.
// -------------------------------------------------------------

const DEFAULT_CITY = "London";

// ===== shared parser core (kept in sync with shared/prayer-core.js) =====
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
// ===== end shared parser core =====

async function fetchText(url) {
  const req = new Request(url);
  req.headers = { "User-Agent": "Scriptable-PrayerTimes/1.0" };
  return await req.loadString();
}

// ---- cache (offline fallback) ----
const fm = FileManager.local();
const cacheDir = fm.joinPath(fm.cacheDirectory(), "prayertimes");
if (!fm.fileExists(cacheDir)) fm.createDirectory(cacheDir, true);
const cachePath = (city) => fm.joinPath(cacheDir, `${city}.json`);
function readCache(city) {
  try { return JSON.parse(fm.readString(cachePath(city))); } catch (e) { return null; }
}
function writeCache(city, data) {
  try { fm.writeString(cachePath(city), JSON.stringify(data)); } catch (e) { /* ignore */ }
}

// ---- selected city (set via the in-app picker; used when no widget Parameter) ----
const savedCityPath = fm.joinPath(cacheDir, "selected-city.json");
function readSavedCity() {
  try {
    const c = JSON.parse(fm.readString(savedCityPath));
    return CITIES.includes(c.city) ? c.city : null;
  } catch (e) { return null; }
}
function saveSelectedCity(city) {
  try { fm.writeString(savedCityPath, JSON.stringify({ city })); } catch (e) { /* ignore */ }
}
async function pickCity(current) {
  const a = new Alert();
  a.title = "Prayer Times — choose city";
  a.message = "Currently showing: " + current;
  for (const c of CITIES) a.addAction(c === current ? "✓  " + c : c);
  a.addCancelAction("Cancel");
  const idx = await a.presentSheet();
  return idx >= 0 ? CITIES[idx] : null;
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

// ---- design tokens ----
const BG_TOP = new Color("#0d1b2a"), BG_BOT = new Color("#1b263b");
const GOLD = new Color("#e0b054"), LABEL = new Color("#e8eef5");
const GLYPH = { Dawn: "☀", Sunrise: "◐", Noon: "☉", Maghrib: "☾", Midnight: "☾" };
const ROWS = [["Dawn", "dawn"], ["Sunrise", "sunrise"], ["Noon", "noon"], ["Maghrib", "maghrib"], ["Midnight", "midnight"]];

function bgGradient() {
  const g = new LinearGradient();
  g.colors = [BG_TOP, BG_BOT];
  g.locations = [0, 1];
  return g;
}

function addHeader(stack, data, small) {
  const h = stack.addStack();
  h.centerAlignContent();
  const moon = h.addText("🌙 ");
  moon.font = Font.systemFont(small ? 10 : 12);
  const city = h.addText(data.city.toUpperCase());
  city.font = Font.semiboldSystemFont(small ? 11 : 13);
  city.textColor = GOLD;
  if (data.stale) { h.addSpacer(4); const d = h.addText("•"); d.textColor = new Color("#e0b054", 0.4); }
  stack.addSpacer(small ? 4 : 6);
}

function addGlyphRow(stack, name, time, isNext) {
  const r = stack.addStack();
  r.centerAlignContent();
  r.setPadding(4, 8, 4, 8);
  if (isNext) { r.backgroundColor = new Color("#e0b054", 0.14); r.cornerRadius = 6; }
  const g = r.addText(GLYPH[name] + "  ");
  g.textColor = GOLD; g.font = Font.systemFont(14);
  const l = r.addText(name);
  l.textColor = LABEL; l.font = Font.systemFont(14);
  r.addSpacer();
  const t = r.addText(time);
  t.textColor = GOLD; t.font = Font.semiboldSystemFont(15);
  stack.addSpacer(3);
}

function addCompactRow(stack, name, time, isNext) {
  const r = stack.addStack();
  r.centerAlignContent();
  r.setPadding(1, 4, 1, 4);
  if (isNext) { r.backgroundColor = new Color("#e0b054", 0.14); r.cornerRadius = 4; }
  const l = r.addText(name);
  l.textColor = LABEL; l.font = Font.systemFont(11);
  r.addSpacer();
  const t = r.addText(time);
  t.textColor = GOLD; t.font = Font.semiboldSystemFont(11);
  stack.addSpacer(1);
}

function addNextTag(stack, next) {
  const s = stack.addStack();
  s.addSpacer();
  const tag = s.addText(`Next · ${next.name} ${next.time}`);
  tag.font = Font.mediumSystemFont(11);
  tag.textColor = new Color("#e0b054", 0.85);
  s.addSpacer();
}

function buildWidget(data, family) {
  const w = new ListWidget();
  w.backgroundGradient = bgGradient();
  const small = family === "small";
  w.setPadding(small ? 10 : 12, small ? 12 : 14, small ? 10 : 12, small ? 12 : 14);
  addHeader(w, data, small);
  if (small) {
    for (const [name, key] of ROWS) addCompactRow(w, name, data[key], name === data.next.name);
  } else {
    for (const [name, key] of ROWS) addGlyphRow(w, name, data[key], name === data.next.name);
    w.addSpacer(4);
    addNextTag(w, data.next);
  }
  const refresh = new Date();
  refresh.setHours(refresh.getHours() + 3);
  w.refreshAfterDate = refresh;
  return w;
}

function buildError() {
  const w = new ListWidget();
  w.backgroundGradient = bgGradient();
  w.setPadding(16, 16, 16, 16);
  const t = w.addText("Prayer times unavailable");
  t.textColor = LABEL; t.font = Font.systemFont(13);
  return w;
}

// ---- entry ----
(async () => {
  // City precedence: per-widget Parameter (if set) > in-app picker choice > default.
  const paramCity = args.widgetParameter && String(args.widgetParameter).trim();
  let city = paramCity || readSavedCity() || DEFAULT_CITY;
  const family = config.widgetFamily || "medium";

  // Running inside a widget → just render it.
  if (config.runsInWidget) {
    let widget;
    try { widget = buildWidget(await loadData(city), family); }
    catch (e) { widget = buildError(); }
    Script.setWidget(widget);
    Script.complete();
    return;
  }

  // Run in the app (or tapped the widget) → let the user choose a city, save it,
  // then show a preview. A widget Parameter, if set, still overrides this per widget.
  const chosen = await pickCity(city);
  if (chosen) { saveSelectedCity(chosen); city = chosen; }

  let widget;
  try { widget = buildWidget(await loadData(city), family === "small" ? "medium" : family); }
  catch (e) { widget = buildError(); }
  if (family === "large") await widget.presentLarge();
  else await widget.presentMedium();
  Script.complete();
})();
