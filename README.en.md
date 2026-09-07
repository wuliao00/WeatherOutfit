# 简衣 · WeatherOutfit

[简体中文](README.md) | [English](README.en.md) | [Русский](README.ru.md)

![平台](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-4285F4)
![API](https://img.shields.io/badge/API-26%20~%2035-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**A native Android app that intelligently recommends outfits based on real-time weather.** Built on the China Meteorological Administration data API (apihz.cn), it generates outfit plans for three scenarios — commute, outdoor, and casual — across four dimensions: temperature, humidity, UV index, and wind, with personalized adjustments for cold/heat tolerance, preferred style, and more. The app strictly follows four design principles: "clarity, obedience, depth, and minimalism": the home screen shows only the temperature, weather condition, and outfit recommendation — no social features, no ads, no news feed — and the whole app uses no more than three primary colors.

> Works out of the box: the project ships with public test credentials, so you can clone, build, and run it directly; for production use, please register your own personal credentials (see below). This app is completely free, its sole author is "Mo" (莫), and any attempt to charge money in this app's name is a scam.

##  Download
> 蓝奏云 (Lanzou Cloud, a Chinese file-hosting service): https://wwazj.lanzoum.com/b01eupxd2f   Password: 5pfd
> 夸克网盘 (Quark Netdisk, a Chinese cloud-drive service): https://pan.quark.cn/s/9a513e59fe90?pwd=J1xs  Access code: J1xs
     <img width="156" height="149" alt="image" src="https://github.com/user-attachments/assets/2a947a91-5300-45b7-bfc7-b8bbc2fe0b96" />


## Feature Overview

| Module | Description |
|------|------|
| Home | Large centered temperature + weather condition + four indicators (feels-like/humidity/wind/UV) + outfit recommendation card (long-press to save as a template) |
| Outfit Details | Outfit plans for three scenarios (commute / outdoor / casual) with checkable item lists; supports custom outfit templates (swipe left to delete) |
| City Management | Province + city search, GPS location, quick switching between recent cities; on cold start, auto-locates with "GPS first → IP fallback" |
| Settings | Cold/heat tolerance / preferred style / gender, temperature and wind units, daily outfit push notification (time selectable), severe weather alerts, custom API credentials, re-view the usage notice |
| Usage Notice | First-launch dialog: author "Mo" (莫), free-of-charge statement, anti-scam reminder, data source and privacy notes; checking "Don't show again" persists the setting and the dialog never appears again |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM (ViewModel + Repository + Data Source)
- **Networking**: Retrofit + OkHttp + Kotlin Coroutines
- **Background tasks**: WorkManager (persistent scheduling of daily push notifications, automatically restored after reboot)
- **Image loading**: Coil (weather icons)
- **Local storage**: Room (recent cities, outfit templates, weather cache) + DataStore (lightweight settings)
- **Location**: FusedLocationProviderClient (Google Play Services)
- **Compatibility**: minSdk 26 (Android 8.0) ~ targetSdk 35 (Android 15), adaptive layouts for phones/foldables/tablets

## Quick Start

1. Clone the repository and open it in Android Studio, then wait for Gradle sync to finish.
2. Check the weather API credentials in `local.properties` (the project ships with official public test credentials, ready to use out of the box).
3. Connect a device or emulator and click Run.

 Command-line build: `./gradlew assembleDebug` (requires JDK 17+).
> Unit tests: `./gradlew test` (outfit recommendation engine).

📖 For the full user manual, see [docs/天气穿搭助手使用说明文档.docx](docs/天气穿搭助手使用说明文档.docx) (the Chinese filename is kept to avoid breaking historical links).

## How to Register and Obtain API Credentials

1. Visit [apihz.cn](https://cn.apihz.cn) to register an account and log in to the dashboard.
2. In the "接口盒子" (API Box) dashboard, obtain your personal `id` and `key` (dedicated rate limits, suitable for release builds).
3. Open `local.properties` in the project root and replace the following two entries:

```properties
WEATHER_API_ID=your ID
WEATHER_API_KEY=your KEY
```

Credentials are injected into the code via `BuildConfig` (see `app/build.gradle.kts`) and never enter version control.

> You can also skip rebuilding: simply fill in your personal `appid` / `appkey` / API endpoint under "Settings → API Credentials (optional)" in the app; user configuration takes priority. If left empty, the app falls back to the built-in default credentials (stored only in local DataStore).

> The project ships with the official public test credentials `88888888 / 88888888`, which **share rate limits with all developers** (during peak hours it may return "calls too frequent"). They are for development and debugging only and must not be used in release builds.

## How to Change the Theme Color

Theme colors are defined centrally in `app/src/main/java/com/jianyi/outfit/ui/theme/Color.kt`:

```kotlin
val MorandiBlue = Color(0xFF6B82A6)   // Change this line to recolor the whole app (light-mode primary color)
val DarkPrimary = Color(0xFFA7BCDA)   // Dark-mode primary color (keep sufficient contrast)
```

Also update the resource file `app/src/main/res/values/colors.xml` (referenced by the splash icon/window background) and `drawable/ic_launcher_background.xml` (icon background color).

Per the design specification, the whole app uses no more than 3 primary colors:

- 1 theme color: Morandi blue `#6B82A6`
- 2 status colors: light orange for high temperature `#F5A67A`, light blue for low temperature `#7EB3D5` (used for hints only)

## How to Modify the Recommendation Rules

The recommendation logic is fully centralized in `app/src/main/java/com/jianyi/outfit/engine/OutfitRecommendationEngine.kt`, a pure Kotlin implementation with the accompanying unit test `app/src/test/.../OutfitRecommendationEngineTest.kt`:

- **Temperature buckets**: modify `temperatureBucketItems()` and `temperatureBucketRange()`
- **UV / wind / humidity adjustment rules**: modify step 3 of `recommend()`
- **Scenario tailoring** (structured commute wear, quick-dry outdoor wear, loose casual wear): modify `commuteItems()` / `outdoorItems()` / `casualItems()`
- **Cold/heat tolerance offset**: modify `toleranceOffset` (currently ±2℃)

After making changes, run `./gradlew test` to verify the rules still hold.

## Project Structure

```
app/src/main/java/com/jianyi/outfit/
├── data/
│   ├── remote/          # Retrofit API definitions, response models, RetrofitClient
│   ├── local/           # Room database, DAOs, entities (cities/templates/cache)
│   ├── repository/      # Repository layer (weather/cities/settings/templates)
│   └── model/           # Domain models (weather, preferences, recommendation results)
├── ui/
│   ├── home/            # Home screen (weather + outfit card)
│   ├── detail/          # Outfit details screen (scenario plans + template management)
│   ├── city/            # City management (search/GPS/recent cities)
│   ├── settings/        # Settings screen (preferences/units/notifications)
│   ├── theme/           # Theme, color, and typography definitions
│   ├── navigation/      # Navigation graph and transition animations
│   └── components/      # Reusable UI components
├── engine/              # Outfit recommendation engine (pure Kotlin, unit-testable)
├── notification/        # Notification channels, daily push scheduling (WorkManager), and boot receiver
├── di/                  # Manual dependency container + ViewModel factories
└── util/                # Utilities (formatting, location)
```

## Weather Data API

Data source: China Meteorological Administration data provided by apihz.cn (free registration). Three live-weather endpoints plus a 7-day forecast are wrapped:

| Endpoint | Purpose |
|------|------|
| `GET /api/tianqi/tqybip.php` | IP-based auto-location (first launch) |
| `GET /api/tianqi/tqyb.php?sheng=&place=` | Query by address (city search) |
| `GET /api/tianqi/tqybjw1.php?lat=&lon=` | Query by coordinates (GPS location) |
| `GET /api/tianqi/tqyb.php?...&day=7` | 7-day forecast |

- A response with `code=200` means success, `code=400` means failure; the failure reason is in `msg` and shown to the user via a Snackbar/error panel.
- Weather data is cached locally for 30 minutes and refreshed automatically when stale; when offline, stale cache is used as a fallback to keep the app usable.

## Known Notes

- **Location strategy (ordered by reliability)**: manually selected cached city → GPS coordinates → IP fallback. IP-based location relies on carrier IP databases and may drift across cities (e.g., showing Zhanjiang while in Zhongshan), so it is used only as a fallback when location permission is denied / Play Services is unavailable / location services are off; in IP mode, a Wi-Fi icon appears next to the city name along with a "Tap to use precise GPS location" banner.
- **The coordinates endpoint uses a global data source** (`tqybjw1.php`): its response structure differs from the IP/address endpoints — no province, no day/night temperature range, no weather alerts — and **city names are shown in pinyin** (e.g., Zhongshan); missing feels-like temperature falls back to air temperature, and wind force is converted from wind speed to the Beaufort scale.
- **Rate-limit protection**: when the public credentials are rate-limited during peak hours, the repository layer automatically retries once after backing off for the wait time suggested by the API (`s` field); when there is no data on screen, a countdown card is shown (automatic retry happens only once, then it switches to manual mode); when cached data exists, it degrades to a hint and keeps showing the cache; manual refresh and "use precise location" share a 10-second throttle.
- **UV index is an estimate**: the API does not provide a UV field, so the engine estimates it from "weather phenomenon + time of day" (sunny 7 / cloudy 5 / overcast 2 / rain or snow 1, zero at night), displayed as "index·level".
- **GPS depends on Google Play Services**: on devices without GMS, location fails and the app prompts the user to switch to search or IP-based location; functionality is not blocked.
- **Daily push notifications** are based on a persistent WorkManager periodic task (every 24 hours; the push time can be chosen in Settings and may drift a few minutes under battery-saving policies); the task is persisted with the system and **automatically restored after device reboot without reopening the app** (the boot receiver realigns it to the configured time); on Android 13+, notification permission is requested the first time the feature is enabled.
- **Dark mode** follows the system (background `#121212`, cards `#1E1E1E`, text `#E0E0E0`, avoiding pure black).

## License

This project is open-sourced under the [MIT License](LICENSE), author "Mo" (莫).

## Acknowledgements

- Weather data: China Meteorological Administration data provided by [apihz.cn 接口盒子](https://cn.apihz.cn)
- Icons and design guidelines: [Material Design 3](https://m3.material.io/)
