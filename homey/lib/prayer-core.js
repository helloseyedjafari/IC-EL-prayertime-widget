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
