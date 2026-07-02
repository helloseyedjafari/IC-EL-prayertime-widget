# Android — home-screen widget

A minimal Android Studio (Kotlin) project whose only purpose is a resizable
home-screen prayer-times widget. No launcher activity — it only adds a widget.

## Requirements

- Android Studio (Ladybug or newer) **or** just the Android SDK + JDK 17.
- Builds with: AGP 8.7.2, Gradle 8.10.2 (wrapper included), Kotlin 2.1.10,
  `compileSdk 35`, `minSdk 26`.

## Build & install

```bash
# to a connected device / running emulator:
./gradlew installDebug

# or produce an APK and install it manually:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

If the SDK isn't auto-detected, create `local.properties` with:

```
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Use

Long-press home screen → **Widgets** → **Prayer Times** → drag it out → the config
screen appears → tap a city. Resize by long-pressing the placed widget and dragging
the handles: a compact layout is used at small sizes, a roomier one (with glyphs and
a `Next · …` tag) at larger sizes.

- Tap the widget to force a refresh.
- A WorkManager job refreshes every ~6 hours so the day's times stay current.
- Add multiple widgets for different cities — the city is stored per widget instance.

## Tests

```bash
./gradlew testDebugUnitTest
```

`PrayerCoreTest` verifies the Kotlin parser against saved HTML fixtures (including the
Cardiff `00:00` sunrise repair and the next-prayer logic) — parity with the shared JS
core in [`../shared/prayer-core.js`](../shared/prayer-core.js).

## Layout of the code

| File | Responsibility |
|------|----------------|
| `PrayerCore.kt` | Pure parser: fetch URL build, HTML parse, `00:00` repair, next-prayer (JVM-only, unit-tested) |
| `PrayerRepository.kt` | Network fetch + per-city SharedPreferences cache / stale fallback |
| `PrayerWidgetProvider.kt` | `AppWidgetProvider`: `goAsync()` fetch, size→layout, row highlight, tap refresh |
| `ConfigActivity.kt` | City picker shown when the widget is added |
| `RefreshWorker.kt` | WorkManager periodic refresh + scheduler |
| `res/layout/widget_*.xml` | Dark-celestial compact / roomy layouts |
