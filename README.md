# Prayer Time Widgets

Display-only "today's prayer times" widgets for **Homey Pro**, **Android**, and
**iOS** (via [Scriptable](https://scriptable.app) — no App Store app of my own).

Each widget shows **Dawn · Sunrise · Noon · Maghrib · Midnight** for a chosen UK
city, the city name, and a static **next-prayer** label. No alarms, no audio — just
a beautiful little card.

<p align="center">
  <img src="homey/widgets/prayer-times/preview-dark.png" width="340" alt="Prayer Times widget — dark celestial">
</p>

Cities: **London · Cardiff · Glasgow · Manchester · Newcastle** (default London).

Data comes straight from the [Islamic Centre of England timetable](https://ic-el.uk/prayer_times/)
— see [`shared/parsing-contract.md`](shared/parsing-contract.md). No server, no API key.

---

## Repository layout

| Path | What |
|------|------|
| [`shared/`](shared/) | Canonical parser (`prayer-core.js`) + the parsing contract + HTML fixtures + tests |
| [`homey/`](homey/) | Homey Pro app hosting the dashboard widget |
| [`ios-scriptable/`](ios-scriptable/) | Single Scriptable script |
| [`android/`](android/) | Android Studio project (home-screen widget) |
| [`docs/superpowers/`](docs/superpowers/) | Design spec + implementation plan |

---

## Run it

### 🟦 Homey Pro

The widget lives inside a small Homey app you install on **your own** Homey Pro in
developer mode (nothing is published to the Homey App Store).

```bash
npm install -g homey            # Homey CLI
homey login                     # your (free) Homey account
cd homey
homey app install               # builds + installs onto your Homey Pro
```

Then on your Homey **dashboard**: add a widget → **Prayer Times** → open its
settings → pick a **City**. Done.

- **Daily auto-update without touching the screen:** the app backend refreshes on
  its own (hourly + a precise 00:05 London rollover) and *pushes* new times to the
  widget, so an always-on dashboard updates itself. (Details: spec §6.4.)
- To try it live first without installing: `cd homey && homey app run`.
- If `homey app install` complains about `compatibility`, lower `">=12.0.0"` in
  `homey/.homeycompose/app.json` to match your Homey firmware.

### 🍏 iOS — Scriptable

1. Install **Scriptable** (free) from the App Store.
2. New script → name it **Prayer Times** → paste
   [`ios-scriptable/prayer-times.js`](ios-scriptable/prayer-times.js) → run once to preview.
3. Add a Scriptable widget to the home screen → **Edit Widget** → Script = **Prayer
   Times**, **Parameter** = a city (e.g. `Glasgow`; blank = London).

Small size shows all five compactly; medium/large add the glyphs + a `Next · …` tag.
Full details: [`ios-scriptable/README.md`](ios-scriptable/README.md).

### 🤖 Android

**Easiest — install the prebuilt debug APK** (no Android Studio needed):

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

(Or copy the APK to the phone and tap it — allow "install from this source".)

**Or build it yourself:**

```bash
cd android
./gradlew installDebug        # to a connected phone/emulator
# or: ./gradlew assembleDebug  → app/build/outputs/apk/debug/app-debug.apk
```

Then long-press the home screen → **Widgets** → **Prayer Times** → place it → pick a
city in the config screen. The widget is **resizable** (compact vs. roomy layouts),
refreshes ~every 6h via WorkManager, and updates on tap. Place several for different
cities. More: [`android/README.md`](android/README.md).

---

## Behaviour notes

- **Display only.** No alarms, notifications, or audio. The "next prayer" is a static
  label recomputed on refresh — it never ticks.
- **Timezone.** "Today" and "now" are always computed in `Europe/London`, so the
  right day and next-prayer show regardless of device timezone.
- **Offline.** Each widget caches the last good times per city; if the source is
  unreachable it shows those with a small dim dot instead of going blank.
- **Source quirks self-heal.** The timetable occasionally emits a bad `00:00` for
  dawn/sunrise/noon; the parser repairs it by interpolating the neighbouring days.

## Development

```bash
npm test          # runs the shared JS parser tests (Node, zero deps)
cd android && ./gradlew testDebugUnitTest   # Kotlin parser parity tests
```

The JS core (`shared/prayer-core.js`), its inlined copy in the Scriptable file, the
Homey copy (`homey/lib/prayer-core.js`), and the Kotlin port
(`android/.../PrayerCore.kt`) all implement the same
[`shared/parsing-contract.md`](shared/parsing-contract.md). Change them together.
