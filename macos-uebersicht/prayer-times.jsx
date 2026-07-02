// Prayer Times — Übersicht widget (macOS desktop)
// -------------------------------------------------------------
// A standalone Mac desktop widget (no iPhone needed). Runs on Übersicht,
// a free macOS desktop-widget app: https://tracesof.net/uebersicht/
//
// SETUP:
//   1. Install Übersicht (free)  →  brew install --cask ubersicht   (or download).
//   2. Open Übersicht → its menu-bar icon → "Open Widgets Folder".
//   3. Copy this file (prayer-times.jsx) into that folder.
//   4. It appears on your desktop. Drag it where you like.
//   5. To change the city, edit CITY below and save — it reloads automatically.
// -------------------------------------------------------------
import { css } from "uebersicht";

// ===== EDIT ME =====
const CITY = "London"; // London | Cardiff | Glasgow | Manchester | Newcastle
// ===================

// Fetch today's month table for CITY (shell curl → no browser CORS issues).
// Date is computed in Europe/London so the correct month/day are used.
export const command =
  `curl -s "https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php` +
  `?year=$(TZ=Europe/London date +%Y)&city=${CITY}&month=$(TZ=Europe/London date +%m)"`;

export const refreshFrequency = 30 * 60 * 1000; // 30 min (times are per-day)

export const className = css`
  top: 40px;
  left: 40px;
`;

// ===== parser (kept in sync with shared/prayer-core.js) =====
function londonNow(date = new Date()) {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  });
  const p = Object.fromEntries(fmt.formatToParts(date).map((x) => [x.type, x.value]));
  return { y: +p.year, m: +p.month, d: +p.day, minutes: (+p.hour % 24) * 60 + +p.minute };
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
        day: parseInt(cells[1], 10), dawn: cells[2], sunrise: cells[3], noon: cells[4],
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
// ===== end parser =====

const GLYPH = { Dawn: "☀", Sunrise: "◐", Noon: "☉", Maghrib: "☾", Midnight: "☾" };
const ORDER = [["Dawn", "dawn"], ["Sunrise", "sunrise"], ["Noon", "noon"], ["Maghrib", "maghrib"], ["Midnight", "midnight"]];

const S = {
  card: {
    width: "248px", padding: "12px 14px", borderRadius: "16px", color: "#e8eef5",
    background: "linear-gradient(180deg,#0d1b2a 0%,#1b263b 100%)",
    fontFamily: '-apple-system,"SF Pro Text",Helvetica,sans-serif',
    boxShadow: "0 8px 24px rgba(0,0,0,.35)",
  },
  head: { display: "flex", alignItems: "center", padding: "5px 6px", marginBottom: "4px" },
  glyph: { flex: "0 0 26px", textAlign: "center", color: "#e0b054", fontSize: "17px" },
  city: { flex: "1", paddingLeft: "8px", color: "#e0b054", fontWeight: 700, letterSpacing: "1.4px", fontSize: "15px" },
  row: { display: "flex", alignItems: "center", padding: "5px 6px", borderRadius: "8px" },
  nextRow: { background: "rgba(224,176,84,.16)" },
  name: { flex: "1", paddingLeft: "8px", opacity: 0.9, fontSize: "15px" },
  time: { color: "#e0b054", fontWeight: 700, fontSize: "16px", fontVariantNumeric: "tabular-nums" },
  next: { textAlign: "center", color: "#e0b054", opacity: 0.85, marginTop: "6px", fontSize: "12px" },
  msg: { padding: "8px 6px", opacity: 0.7, fontSize: "13px", textAlign: "center" },
};

function shell(city, msg) {
  return (
    <div style={S.card}>
      <div style={S.head}><span style={S.glyph}>🌙</span><span style={S.city}>{city.toUpperCase()}</span></div>
      <div style={S.msg}>{msg}</div>
    </div>
  );
}

export const render = ({ output, error }) => {
  if (error) return shell(CITY, "Error");
  if (!output) return shell(CITY, "Loading…");
  let data;
  try {
    const L = londonNow();
    const { times, next } = extractToday(output, L);
    data = { city: CITY, ...times, next };
  } catch (e) {
    return shell(CITY, "Unavailable");
  }
  return (
    <div style={S.card}>
      <div style={S.head}><span style={S.glyph}>🌙</span><span style={S.city}>{data.city.toUpperCase()}</span></div>
      {ORDER.map(([name, key]) => (
        <div key={name} style={name === data.next.name ? { ...S.row, ...S.nextRow } : S.row}>
          <span style={S.glyph}>{GLYPH[name]}</span>
          <span style={S.name}>{name}</span>
          <span style={S.time}>{data[key]}</span>
        </div>
      ))}
      <div style={S.next}>Next · {data.next.name} {data.next.time}</div>
    </div>
  );
};
