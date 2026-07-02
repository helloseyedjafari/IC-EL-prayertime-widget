# macOS — Übersicht widget (standalone, no iPhone)

A real Mac **desktop** widget that works on its own — no iPhone, no Continuity. It runs
on [**Übersicht**](https://tracesof.net/uebersicht/), a free macOS app that shows
HTML/JS widgets on your desktop. This widget reuses the exact same parser and
dark‑celestial design as the others.

<p align="center">
  <img src="../homey/widgets/prayer-times/preview-dark.png" width="300" alt="Prayer Times widget">
</p>

## Install

1. **Install Übersicht** (free):
   ```bash
   brew install --cask ubersicht
   ```
   …or download it from <https://tracesof.net/uebersicht/> and drag it to Applications.
2. **Launch Übersicht.** It adds a menu‑bar icon (a small eye).
3. Menu‑bar icon → **Open Widgets Folder**.
4. Copy [`prayer-times.jsx`](./prayer-times.jsx) into that folder.
5. The widget appears on your desktop. **Drag it** wherever you like.

That's it — it fetches and refreshes on its own.

## Change the city

Open `prayer-times.jsx` in any text editor and change the line near the top:

```js
const CITY = "London"; // London | Cardiff | Glasgow | Manchester | Newcastle
```

Save — Übersicht reloads the widget automatically. (Want several cities? Duplicate the
file, e.g. `prayer-times-glasgow.jsx`, and set a different `CITY` in each.)

## Move / position

Drag it anywhere on the desktop. To fine‑tune, edit the `top` / `left` values in the
`className` block near the top of the file.

## Notes

- **Display only** — no alarms, no sound.
- **Auto‑updates** every 30 minutes (and whenever Übersicht reloads). Times are per‑day,
  so that's plenty; the new day's times appear at the next refresh.
- Fetches via a shell `curl` (so there are **no browser CORS problems**); the date is
  computed in `Europe/London`, so the correct day is always used regardless of your
  Mac's timezone.
- The parser is kept in sync with [`../shared/prayer-core.js`](../shared/prayer-core.js)
  (see [`../shared/parsing-contract.md`](../shared/parsing-contract.md)).

## Why Übersicht (and not a native macOS widget)?

Apple's native Notification‑Center/desktop widgets require a full **Swift/Xcode app** to
be built and code‑signed — heavy for a display‑only card, and it would mean shipping
another app. Übersicht gives a native‑feeling desktop widget with zero of that overhead.
If you'd rather have a true native macOS app widget, that's buildable too — just ask.
