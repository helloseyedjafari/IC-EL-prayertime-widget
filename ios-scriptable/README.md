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
4. (Optional) **Parameter** → a city — see below. Leave blank to use the in-app picker.

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

## Notes

- **Display only** — no alarms, no sound.
- Updates itself through the day (iOS refreshes widgets on its own schedule; the
  script requests a refresh roughly every 3 hours). The new day's times appear at
  the next system refresh.
- If the source is unreachable, it shows the **last successful** times with a small
  dim dot next to the city, instead of going blank.
- The parser logic is kept in sync with [`../shared/prayer-core.js`](../shared/prayer-core.js)
  (see [`../shared/parsing-contract.md`](../shared/parsing-contract.md)).
