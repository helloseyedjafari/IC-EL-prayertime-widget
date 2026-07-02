# Parsing Contract (canonical)

This is the single source of truth for how **every** platform fetches and parses
prayer times. The reference implementation is [`prayer-core.js`](./prayer-core.js).
Homey and iOS reuse that JS; Android ports it to Kotlin
(`android/.../PrayerCore.kt`). If `ic-el.uk` changes its HTML, update this doc and
all three parsers together.

## 1. Endpoint

```
GET https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php?year=<YYYY>&city=<CITY>&month=<M>
```

- `city` ∈ `London | Cardiff | Glasgow | Manchester | Newcastle` (exact case).
- `month` = 1–12, `year` = 4-digit. Always send all three.
- No auth, no API key, no cookies, no special headers. Public HTML response.
- Compute `<YYYY>`/`<M>`/today's date and "now" in **`Europe/London`** (source is UK local),
  independent of device timezone.

## 2. Response shape

A full-month HTML table; each day is one row:

```html
<tr ID="OddRows"> | <tr ID="EvenRows"> | <tr ID="TodayRow">
  <td ...>Thu</td>     <!-- 0: day of week      -->
  <td ...>2</td>       <!-- 1: day of month     -->
  <td ...>02:29</td>   <!-- 2: Dawn             -->
  <td ...>04:48 </td>  <!-- 3: Sunrise (may have trailing space) -->
  <td ...>13:05</td>   <!-- 4: Noon             -->
  <td ...>21:36</td>   <!-- 5: Maghrib          -->
  <td ...>23:55</td>   <!-- 6: Midnight         -->
</tr>
```

Today's row is tagged `ID="TodayRow"` (verified present for all 5 cities).

## 3. Parse algorithm

1. Fetch the current month's table for the chosen city.
2. Match rows with `/<tr ID="(OddRows|EvenRows|TodayRow)">([\s\S]*?)<\/tr>/g`.
3. For each row, extract `<td>` inner texts with `/<td[^>]*>([\s\S]*?)<\/td>/g`,
   strip any nested tags, `trim()`.
4. Today's row = the one with `ID="TodayRow"`.
   **Fallback:** if absent, the row whose cell[1] equals today's day-of-month (Europe/London).
5. Map cells 2–6 → `{ dawn, sunrise, noon, maghrib, midnight }` (`"HH:MM"`).
6. Apply the repair rule (§4).

## 4. Data-quality repair

The source occasionally emits a corrupt `00:00` (observed: Cardiff sunrise
2026-07-02, neighbours `04:59`/`05:01`).

- **Repair only `dawn`, `sunrise`, `noon`.** If the value is `00:00` or not valid
  `HH:MM`, replace with the linear interpolation of the same field from the nearest
  valid previous and next day in the same table. If only one neighbour is valid, use it.
  If none, keep as-is.
- **Never repair `maghrib` or `midnight`** — they legitimately sit near midnight (e.g. `00:12`).

## 5. Cache & offline

- Cache the last successful parse per city:
  `{ city, dawn, sunrise, noon, maghrib, midnight, next:{name,time} }`.
- On fetch/parse failure, render cached values with a `stale` marker instead of blanking.

## 6. Next prayer (static, best-effort)

- Convert the five times to minutes-of-day. Treat a `midnight` value < 03:00 as
  end-of-day (add 24h) for ordering.
- `next` = the earliest of the five later than the current Europe/London time.
  If all have passed, `next` = **Dawn**.
- Recomputed only at refresh time — never ticks per second.
