# Prayer Time Widgets — Design Spec

- **Date:** 2026-07-02
- **Status:** Approved (design), pending spec review
- **Author:** Seyed Jafari (with Claude)

## 1. Goal

Show today's prayer times from the Islamic Centre of England timetable
(`ic-el.uk`) as a small, display-only widget on three surfaces:

1. **Homey Pro dashboard** — native Homey app + dashboard widget.
2. **Android** — native home-screen app widget.
3. **iOS** — a **Scriptable** script (no App Store app of our own).

Each widget shows, for one selectable city: **Dawn, Sunrise, Noon, Maghrib,
Midnight**, plus the **city name** and a static **"next prayer"** label.

## 2. Non-goals

- **No alarms, no audio, no notifications, no countdown tick.** Display only.
- No date, Hijri/Solar-Hijri dates, or moon phase (the source has them; we omit them).
- No hosting/server/back-end service. Each widget fetches the source directly.
- No publishing to the Apple App Store or Google Play or Homey App Store.
  Everything runs privately on the user's own devices.

## 3. Data source contract (shared by all three platforms)

This is the single source of truth for how every platform fetches and parses.
If `ic-el.uk` changes its HTML, this section and the three mirrored parsers change.

### 3.1 Endpoint

```
GET https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php?year=<YYYY>&city=<CITY>&month=<M>
```

- `city` ∈ `London | Cardiff | Glasgow | Manchester | Newcastle` (exact case).
- `month` = 1–12, `year` = 4-digit. Always send all three for determinism.
- No auth, no API key, no cookies, no headers required. Public HTML response.
- Compute `<YYYY>`/`<M>`/today's date in **`Europe/London`** timezone (source is UK local),
  so the correct month table and today's row are selected regardless of device timezone.

### 3.2 Response shape

Returns a full-month HTML table. Each day is a row:

```html
<tr ID="OddRows"> | <tr ID="EvenRows"> | <tr ID="TodayRow">
  <td ...>Thu</td>        <!-- 0: day of week -->
  <td ...>2</td>          <!-- 1: day of month -->
  <td ...>02:29</td>      <!-- 2: Dawn     -->
  <td ...>04:48 </td>     <!-- 3: Sunrise (may have trailing space) -->
  <td ...>13:05</td>      <!-- 4: Noon     -->
  <td ...>21:36</td>      <!-- 5: Maghrib  -->
  <td ...>23:55</td>      <!-- 6: Midnight -->
</tr>
```

Today's row is tagged `ID="TodayRow"` (verified present for all 5 cities).

### 3.3 Parsing algorithm

1. Fetch the current month's table for the chosen city.
2. Find today's row: match `<tr ID="TodayRow">…</tr>`.
   - **Fallback:** if `TodayRow` is absent, select the row whose cell[1] equals
     today's day-of-month (`Europe/London`).
3. Extract the 7 `<td>` inner texts, `trim()` each.
4. Map cells 2–6 → `{ dawn, sunrise, noon, maghrib, midnight }` as `"HH:MM"` strings.
5. Apply the repair rule (§3.4).
6. Also parse all rows once (cheap) so repair can read neighbouring days.

### 3.4 Data-quality repair rule

The source occasionally emits a corrupt `00:00` (observed: Cardiff sunrise on
2026-07-02, with neighbours `04:59`/`05:01`).

- **Repair only `dawn`, `sunrise`, `noon`** — these can never legitimately be `00:00`.
  If the value is `00:00` (or not a valid `HH:MM`), replace it with the linear
  interpolation of the same field from the previous and next day in the same table.
  If a neighbour is also invalid, fall back to the nearest valid neighbour; if none, keep as-is.
- **Do not repair `maghrib` or `midnight`** — they legitimately sit near midnight
  (e.g., Midnight `00:12`).

### 3.5 Caching & offline

- Cache the last successful parse per city:
  `{ city, dateISO, dawn, sunrise, noon, maghrib, midnight, fetchedAtISO }`.
- On fetch failure, render the cached values with a subtle "stale" marker (a dimmed dot)
  instead of a blank/error card. If no cache exists, show a minimal "unavailable" state.

### 3.6 "Next prayer" (static, best-effort)

- Convert the five times to minutes-of-day. Treat a `midnight` value < 03:00 as end-of-day
  (add 24h) for ordering.
- `next` = the earliest of the five whose time is later than the current `Europe/London`
  time. If the current time is past all of them, `next` = **Dawn** (shown as `Dawn HH:MM`).
- This label is **static** — computed at render/refresh time only. No per-second ticking.
- The corresponding row is visually highlighted.

## 4. Shared data model

```ts
type PrayerTimes = {
  city: string;        // "London"
  dawn: string;        // "02:29"
  sunrise: string;     // "04:48"
  noon: string;        // "13:05"
  maghrib: string;     // "21:36"
  midnight: string;    // "23:55"
  next: { name: string; time: string };  // { name: "Maghrib", time: "21:36" }
  stale?: boolean;     // true if served from cache after a failed fetch
};
```

## 5. Design system — "Dark celestial"

A single visual language reused across all three platforms.

- **Background:** vertical gradient `#0d1b2a → #1b263b` (deep navy night sky), rounded card.
- **Accent / times:** warm gold `#e0b054`. **Labels:** soft off-white `#e8eef5` at ~70% opacity.
- **City header:** gold, small-caps, letter-spaced, with a small 🌙 glyph.
- **Per-prayer glyphs:** Dawn `☀` · Sunrise `◐` · Noon `☉` · Maghrib `☾` · Midnight `☾`
  (use unicode glyphs; on Android use vector drawables tinted gold for crisp rendering).
- **Rows:** `Glyph  Label ……… Time`, label left, time right-aligned tabular numerals.
- **Next-prayer highlight:** the next row gets a subtle gold-tinted background pill and a
  small `Next` tag. Static.
- **Typography:** system UI font, tabular figures for times. Legible at small sizes.
- **Stale marker:** a dim dot near the city header when showing cached data.

### Responsive behaviour

- **Homey:** one fixed compact card, height ≈ 240 px. City header + 5 rows + next tag.
- **Android:** resizable. Compact (1×1-ish) = tight rows; larger = bigger glyphs/spacing.
  Provide at least two `RemoteViews` layouts mapped by size (S+ size buckets, with a
  portrait/landscape fallback for older APIs).
- **iOS Scriptable:** detect `config.widgetFamily`.
  - `small` → city + next prayer + two most relevant times, compact.
  - `medium` / `large` → all five with glyphs.
  - `accessoryRectangular` (optional, lock screen) → next prayer only.

## 6. Platform: Homey Pro

### 6.1 Structure

A single Homey app (SDK v3, compose), **no drivers/devices** — just an app that hosts one widget.

```
homey/
├── .homeycompose/app.json        # app id, name, sdk 3, permissions
├── app.js                        # App: scheduled refresh + realtime push + shared fetch/parse
├── package.json
├── lib/prayerTimes.js            # shared fetch + parse + repair + next-prayer (Node)
├── locales/en.json
└── widgets/prayer-times/
    ├── widget.compose.json       # id, name, height, settings[city], api[getToday]
    ├── api.js                    # backend endpoint: returns today's PrayerTimes for a city
    ├── public/index.html         # widget UI (dark celestial), onHomeyReady + Homey.on
    ├── preview-light.png
    └── preview-dark.png
```

### 6.2 City selection

`widget.compose.json` `settings`: one `dropdown` with id `city` and `values`
London/Cardiff/Glasgow/Manchester/Newcastle (default London). The frontend reads it via
`Homey.getSettings().city` and passes it to the backend call.

### 6.3 Data flow

- Frontend `onHomeyReady`: `const data = await Homey.api('GET', '/today?city=' + city)` → render.
- Backend `api.js#getToday({ homey, query })`: calls `lib/prayerTimes.js` (fetch + parse + repair),
  caches in app memory / `homey.settings`, returns `PrayerTimes`.

### 6.4 Auto-update strategy (the key requirement)

Problem: the widget iframe's JS timers get throttled/suspended when the dashboard is idle,
so a widget-only refresh loop dies until the screen is touched.

Fix — drive updates from the **always-running app backend**:

1. **`app.js`** sets a persistent `this.homey.setInterval` (the app process runs 24/7 on
   Homey Pro, independent of any dashboard). It refreshes cached times and, at the daily
   rollover (shortly after `Europe/London` midnight) and on an hourly safety tick, emits
   `this.homey.api.realtime('prayertimes:update', payload)` for each city.
2. **Widget frontend** subscribes: `Homey.on('prayertimes:update', (payload) => { if payload.city === myCity render(payload) })`.
   This updates the on-screen widget at midnight **without a touch**, pushed by the live backend.
3. **Belt-and-suspenders in the frontend:** a `setInterval` re-fetch (~30 min, effective while
   the dashboard is visible) and a re-fetch on `visibilitychange`/`focus` for instant refresh on touch.

Together: backend push guarantees the daily change lands on an always-on dashboard; the client
timer + visibility refresh cover the interactive cases.

> Verification during implementation: confirm the exact `homey.api.realtime` signature and the
> widget `Homey.on` event delivery against the installed Homey CLI/SDK before finalizing, since
> dashboard widgets are a newer SDK feature.

### 6.5 Install (documented in README)

```
npm i -g homey
homey login
cd homey && homey app install     # installs onto the user's own Homey Pro (developer mode)
```
Then add the "Prayer Times" widget to a dashboard and pick a city in its settings.

## 7. Platform: iOS (Scriptable)

### 7.1 Structure

```
ios-scriptable/
├── prayer-times.js     # self-contained: fetch + parse + repair + render for all widget families
└── README.md           # install steps
```

### 7.2 Behaviour

- City: read from `args.widgetParameter` (set per-widget in the home-screen config);
  fall back to a `DEFAULT_CITY = "London"` constant at the top of the file.
- Fetch via `new Request(url).loadString()`, parse with the §3.3 regex algorithm, repair (§3.4).
- Cache last-good JSON per city with `FileManager` (local dir) for offline (§3.5).
- Render with `ListWidget`; branch on `config.widgetFamily` (§5 responsive).
- Set `widget.refreshAfterDate = now + ~3h` so iOS refreshes through the day; the daily
  change is picked up on the next system refresh.
- When run in-app (not in a widget), `widget.presentMedium()` for preview.

### 7.3 Install (documented in README)

Install Scriptable (free, App Store) → new script → paste `prayer-times.js` → add a Scriptable
widget to the home screen → Edit Widget → Script = this script, Parameter = city (e.g. `Glasgow`).

## 8. Platform: Android

### 8.1 Structure

A minimal Android Studio (Kotlin) project whose only purpose is the app widget.

```
android/
├── settings.gradle.kts, build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/.../PrayerTimesWidget.kt      # AppWidgetProvider
        ├── java/.../PrayerTimesRepository.kt   # fetch + parse + repair + cache (mirrors §3)
        ├── java/.../ConfigActivity.kt          # city picker shown when adding the widget
        ├── java/.../RefreshWorker.kt           # WorkManager periodic refresh
        └── res/
            ├── layout/widget_small.xml, widget_large.xml
            ├── drawable/                        # gold-tinted vector glyphs, card background
            ├── xml/prayer_widget_info.xml       # resizable, sizes, config activity
            └── values/                          # colors, dimens, strings
```

### 8.2 Behaviour

- **City per widget instance:** `ConfigActivity` (launched on add) writes the chosen city to
  `SharedPreferences` keyed by `appWidgetId`, so multiple widgets (e.g. London + Glasgow) coexist.
- **Fetch/parse:** `PrayerTimesRepository` does an HTTP GET (OkHttp or `HttpURLConnection`) on a
  background coroutine, parses per §3.3, repairs per §3.4, caches JSON per city (§3.5).
- **Rendering:** `RemoteViews`; map `widget_small`/`widget_large` by size bucket (S+),
  with portrait/landscape fallback for older APIs. Dark-celestial theme via drawables.
- **Auto-update:** `RefreshWorker` (WorkManager) periodic ~6h + an update at the daily boundary;
  also refresh on widget tap (`PendingIntent`) and on resize (`onAppWidgetOptionsChanged`).
- **Resizable:** `prayer_widget_info.xml` sets `resizeMode="horizontal|vertical"`,
  `minWidth/minHeight`, `targetCellWidth/Height`, and `configure` = `ConfigActivity`.

### 8.3 Install (documented in README)

Open `android/` in Android Studio → Run to a connected phone, or `./gradlew installDebug`.
Then long-press home screen → Widgets → Prayer Times → place and pick a city.

## 9. Repository layout

```
prayertime-widgets/
├── README.md                 # overview + per-platform quickstart
├── shared/parsing-contract.md# §3 extracted as the canonical reference for all parsers
├── homey/                    # §6
├── ios-scriptable/           # §7
├── android/                  # §8
└── docs/superpowers/specs/   # this spec
```

Single private GitHub repo (monorepo). Local git is initialized now; remote is added later.

## 10. Cross-cutting: refresh & error handling summary

| Platform | Primary refresh | Interactive refresh | Offline behaviour |
|----------|-----------------|---------------------|-------------------|
| Homey    | Backend `setInterval` + `realtime` push (daily + hourly) | client interval + visibility | cached values + stale dot |
| iOS      | `refreshAfterDate` (~3h) system refresh | on widget open | cached values + stale dot |
| Android  | WorkManager (~6h) + daily boundary | tap + resize | cached values + stale dot |

## 11. Risks & verification steps

1. **Homey widget SDK specifics** (newer feature): verify `widget.compose.json` schema,
   `homey.api.realtime`, and `Homey.on` delivery against the installed CLI/SDK during
   implementation, and test on the user's Homey Pro (`homey app run`) before finalizing.
2. **Source HTML drift:** all three parsers follow §3; changes are localized. Parsers must
   fail soft (fall back to cache) rather than crash.
3. **Timezone correctness:** always compute "today"/now in `Europe/London`.
4. **Data repair:** unit-test the `00:00` repair against the known Cardiff case and a clean case.

## 12. Testing approach

- Extract fetch/parse/repair/next-prayer into a pure function per platform and unit-test it with
  saved HTML fixtures (including the Cardiff `00:00` case and a `TodayRow`-missing case).
- Manual end-to-end: install each widget on the real device/Homey and confirm the correct city,
  five times, next-prayer highlight, and a next-day auto-update (Homey especially).
```
