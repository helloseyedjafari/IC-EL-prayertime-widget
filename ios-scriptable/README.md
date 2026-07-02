# iOS — Scriptable widget

A single self-contained script. No App Store app of your own, no build step.

## Install

1. Install **Scriptable** (free) from the App Store.
2. Open Scriptable → tap **+** (new script) → name it **Prayer Times**.
3. Open [`prayer-times.js`](./prayer-times.js), copy the whole file, and paste it in.
   (Easiest: open this file on your phone and copy, or AirDrop it to yourself.)
4. Tap ▶︎ once to run it in-app — you should see a preview of the widget.

## Add to home screen

1. Long-press the home screen → **+** → search **Scriptable** → pick a size
   (small / medium / large) → **Add Widget**.
2. Long-press the new widget → **Edit Widget**.
3. **Script** → choose **Prayer Times**.
4. **Parameter** → type a city: `London`, `Cardiff`, `Glasgow`, `Manchester`, or
   `Newcastle`. Leave blank for **London**.

Repeat with different parameters to place several cities side by side.

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
