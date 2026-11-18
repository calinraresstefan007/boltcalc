# BoltCalc — Bolt Driver Earnings Calculator

Android app that overlays real-time profitability on Bolt driver offers using Accessibility Service.

## Features

- **Auto-reads offers** from the Bolt driver app (`ee.mtakso.driver`)
- **Parses** price, pickup distance, ride distance, time, rating from screen text
- **Calculates** profit/km after fuel, commission
- **Shows overlay verdict**: 🟢 EXCELENTĂ / 🟡 BUNĂ / 🔴 REFUZĂ
- **Configurable**: fuel consumption, fuel price, Bolt commission, thresholds, address filters
- **History** of all seen offers with profit tracking
- **Gauge visualization** of efficiency (lei/km)

## How it works

1. Grant **Accessibility** permission → Bolt Calc
2. Grant **Display over other apps** permission
3. Open Bolt driver app → offers appear with overlay verdict

## Calculation

```
dist = pickup_km + ride_km
cost = dist × (consumption_l/100km / 100) × fuel_price_lei/l
net  = gross_price × (1 - commission)    // unless "NET" toggle on
profit = net - cost
efficiency = profit / dist
```

Verdict thresholds (configurable):
- `efficiency ≥ minExcelent` → 🟢 EXCELENTĂ
- `efficiency ≥ minBun` → 🟡 BUNĂ
- `pickup > maxPreluare` or `efficiency < minBun` or filters match → 🔴 REFUZĂ

## Build

### Method A: Android Studio (local)
1. Install Android Studio from developer.android.com/studio
2. Extract `boltcalc.zip` → open folder in Android Studio
3. Wait for Gradle sync
4. Build → Build Bundle(s)/APK(s) → Build APK(s)
5. Find APK at `app/build/outputs/apk/debug/app-debug.apk`

### Method B: GitHub Actions (no install)
1. Create GitHub repo named `boltcalc` (Private)
2. Upload all files from extracted `boltcalc` folder
3. Go to Actions tab → enable workflows
4. Workflow runs on push → download `BoltCalc-apk` artifact
5. Extract → get `app-debug.apk`

## Install on phone

1. Transfer `app-debug.apk` to phone (USB, Drive, Telegram)
2. Open APK → allow "Install from unknown sources" → Install
3. Open "Bolt Calc" app
4. Tap "Activează serviciul" → enable Bolt Calc in Accessibility
5. Allow "Display over other apps" if prompted
6. Configure fuel price, consumption, thresholds → Save
7. Open Bolt Driver → wait for offers → overlay appears

## Project structure

```
app/src/main/
├── java/ro/marius/boltcalc/
│   ├── MainActivity.kt          # Settings UI
│   ├── Analyzer.kt              # Parsing + calculation (pure logic)
│   ├── BoltReaderService.kt     # AccessibilityService + overlay
│   ├── HistoryActivity.kt       # History of offers
│   ├── Istoric.kt               # History data model + storage
│   └── GaugeView.kt             # Circular gauge visualization
├── res/
│   ├── xml/accessibility_config.xml
│   └── values/strings.xml
└── AndroidManifest.xml
```

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| Consum (l/100 km) | Fuel consumption | 6.5 |
| Preț motorină (lei/l) | Fuel price | 10.90 |
| Comision Bolt | Bolt commission (0.25 = 25%) | 0.25 |
| Prețul e NET | Screen price is already net | ON |
| Prag excelent (lei/km) | Excellent threshold | 2.5 |
| Prag acceptabil (lei/km) | Good threshold | 1.5 |
| Preluare maximă (km) | Max pickup distance | 3.5 |
| Preț minim (lei) | Min price filter (empty = off) | - |
| Preț maxim (lei) | Max price filter (empty = off) | - |
| Rating minim | Min client rating (empty = off) | - |
| Adrese evitate | Streets to avoid (one per line) | - |

## Troubleshooting

- **No overlay**: Check Accessibility service is ON, grant "Display over other apps"
- **Wrong numbers**: Use "Calibrare" button → send last read text for debugging
- **Filter rejects all**: Empty filter fields = disabled
- **Build fails**: Check Actions log, ensure JDK 17, Android SDK 34

## License

MIT
