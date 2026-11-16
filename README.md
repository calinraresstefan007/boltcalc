# BoltCalc — Bolt Driver Earnings Calculator

Android app that overlays real-time profitability on Bolt driver offers using Accessibility Service.

## Features

- **Auto-reads offers** from the Bolt driver app (`ee.mtakso.driver`)
- **Parses** price, pickup distance, ride distance from screen text
- **Calculates** profit/km after fuel, commission, active bonuses
- **Shows overlay verdict**: ⭐ EXCELENT / ✔️ BUN / ❌ REFUZĂ
- **Configurable**: fuel consumption, fuel price, Bolt commission, bonus campaigns, thresholds

## How it works

1. Grant **Accessibility** permission → Bolt Calc
2. Grant **Display over other apps** permission
3. Open Bolt driver app → offers appear with overlay verdict

## Calculation

```
dist = pickup_km + ride_km
cost = dist × (consumption_l/100km / 100) × fuel_price_lei/l
net  = gross_price × (1 - commission)    // unless "NET" toggle on
bonus = sum(active_bonus_amount / remaining_rides)
profit = net + bonus - cost
efficiency = profit / dist
```

Verdict thresholds (configurable):
- `efficiency ≥ minExcelent` → ⭐ EXCELENT
- `efficiency ≥ minBun` → ✔️ BUN
- `pickup > maxPreluare` or `efficiency < minBun` → ❌ REFUZĂ

## Build

```bash
./gradlew assembleDebug
```

APK at `app/build/outputs/apk/debug/app-debug.apk`

## CI

GitHub Actions builds on every push to `main`/`master`. Artifact: `BoltCalc-apk`.

## Project structure

```
app/src/main/
├── java/ro/marius/boltcalc/
│   ├── MainActivity.kt          # Settings UI
│   ├── Analyzer.kt              # Parsing + calculation (pure logic)
│   ├── BoltReaderService.kt     # AccessibilityService + overlay
│   └── Params.kt                # Data classes + SharedPreferences
├── res/
│   ├── xml/accessibility_config.xml
│   └── values/strings.xml
└── AndroidManifest.xml
```

## License

MIT# Build fix
