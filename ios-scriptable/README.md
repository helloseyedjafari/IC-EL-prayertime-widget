# iOS — Scriptable widget

A single self-contained script. No App Store app of your own, no build step.

## Install

Install **Scriptable** (free) from the App Store, then pick one method.

### ⭐ Method A — one-time installer (easiest)

No downloading or file-hunting. In Scriptable, tap **+**, paste the installer, run it once,
and it creates the **"Prayer Times"** script for you (re-run to update).

1. Open [`install.js`](./install.js) on GitHub → use the **Copy raw file** button
   (top-right of the code box) to copy its contents.
2. Scriptable → **+** (new script) → paste → tap **▶︎ Run**.
3. A **"Prayer Times"** script appears. You can delete the installer.

### Method B — paste the script directly

Copy the **code** (the file's *contents*, ~230 lines — not the file itself):

1. Open the raw script:
   [prayer-times.js (raw)](https://raw.githubusercontent.com/helloseyedjafari/IC-EL-prayertime-widget/main/ios-scriptable/prayer-times.js)
   → **Select All** → **Copy**
   (or the **Copy raw file** button on the
   [file page](https://github.com/helloseyedjafari/IC-EL-prayertime-widget/blob/main/ios-scriptable/prayer-times.js)).
2. Scriptable → **+** new script → name it **Prayer Times** → paste.
3. Tap ▶︎ once to preview.

## Add to home screen

1. Long-press the home screen → **+** → search **Scriptable** → pick a size
   (small / medium / large) → **Add Widget**.
2. Long-press the new widget → **Edit Widget**.
3. **Script** → choose **Prayer Times**.
4. **When Interacting** → choose **Run Script**. ⚠️ The default is *Open App*, which just
   opens Scriptable. **Run Script** makes a tap actually run the widget — it refreshes the
   times and opens the city picker.
5. (Optional) **Parameter** → a city — see below. Leave blank to use the in-app picker.

## Choosing the city

Two ways:

- **Tap the widget** (or run the script inside Scriptable) → a **“choose city”** menu
  appears. Pick one; it's saved and the widget updates on its next refresh. Easiest.
- **Or** set the widget’s **Parameter** to `London`, `Cardiff`, `Glasgow`, `Manchester`,
  or `Newcastle`. A Parameter, if set, **always wins** for that widget — use it to place
  several cities side by side (blank = use the picker’s choice, default London).

## Sizes

- **Small** — city + all five times, compact (next prayer highlighted).
- **Medium / Large** — all five with glyphs + a `Next · …` tag.

## macOS (via Continuity)

Scriptable isn't a Mac app, but **macOS Sonoma (14)+** can show your **iPhone's** widget on
the Mac via **Continuity** — no Mac install:

1. Set it up on the **iPhone** first.
2. Keep the iPhone nearby / same Wi‑Fi + Apple ID.
3. Mac → click the **date/time** in the menu bar (or right‑click desktop) → **Edit Widgets**
   → find **Scriptable** in the gallery → drag **Prayer Times** out.

It mirrors the iPhone widget (city/size follow the iPhone). Tap‑to‑run needs a live link to
the iPhone; otherwise the Mac shows the last streamed times.

## Notes

- **Display only** — no alarms, no sound.
- **Auto‑update:** best‑effort, on **iOS's** schedule. The script asks iOS to refresh
  roughly every 3 hours, but iOS decides the actual timing (battery/usage), so it's not
  the guaranteed cadence the Homey/Android versions have — usually several times a day,
  enough to roll over to the new day. **Tap the widget** (with *Run Script* set) to force
  a fresh run any time.
- If the source is unreachable, it shows the **last successful** times with a small
  dim dot next to the city, instead of going blank.
- The parser logic is kept in sync with [`../shared/prayer-core.js`](../shared/prayer-core.js)
  (see [`../shared/parsing-contract.md`](../shared/parsing-contract.md)).
