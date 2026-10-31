# Plavio

🌐 **Website:** https://plavio.site

A beautiful open-source streaming app for Android with a built-in player. Install addons to browse movies, series, and live TV. An account is optional; the app also offers optional cloud sync and opt-in telemetry.

> This source package is the phone app. Plavio TV is a separate app — out now for Android TV and Fire TV; its source ships with the TV releases.

## Preview

| Home | Live |
|---|---|
| ![Plavio home screen](screenshots/pulse-home.png) | ![Plavio live TV](screenshots/pulse-live.png) |
| Settings | Addons |
| ![Plavio settings](screenshots/pulse-settings.png) | ![Plavio addon manager](screenshots/pulse-addons.png) |

## Features

- **Install addons** — paste any addon manifest URL (`…/manifest.json`) and browse its catalogs
- **Multi-addon merging** — separate addons can provide catalogs, metadata, and streams; Plavio combines them (details are fetched from every metadata addon, sources merged from every stream addon)
- **Movies, series & live TV** — details, posters, backdrops, genres, ratings; a dedicated Live tab for TV channels
- **Episode browser** — season / episode picker for series
- **One-tap Play** — the Play button starts the first available stream instantly
- **Gesture-rich built-in player** — ExoPlayer with HLS, DASH and progressive streams; swipe horizontally to seek, swipe right side for volume, left side for brightness, double-tap for ±10s, playback speed 0.5x–2x
- **Aspect-ratio button** — cycle Fit → Fill → Zoom while playing
- **Subtitle & audio track switching** — right from the player, plus in-player subtitle search
- **Skip intro / recap / outro** — Netflix-style skip button via the community IntroDB database
- **Continue watching** — your progress is saved on-device and surfaced on Home, with resume prompts
- **Watchlist** — bookmark movies and shows for later
- **Search** — across every catalog of every installed addon, with Movies / Series / Live TV filters
- **Settings** — autoplay, keep-screen-on, skip-intro toggle, and more
- **Material 3 dark glass-glow design** — deep blacks, frosted-glass cards, cyan→magenta neon accents

## Get the app

Download the latest APK from the [Releases](../../releases) page and install it on your Android phone (Android 8.0+).

**Plavio TV** is out too — for Android TV and Fire TV, with the same addons on the big screen. Get it from [plavio.site](https://plavio.site) or the TV releases here.

## Add an addon

1. Open **Addons** → tap **+**
2. Paste the addon's manifest URL (it usually ends with `/manifest.json`)
3. Plavio validates it and adds its catalogs to your Home screen

Plavio ships with catalog, metadata, and subtitle addons pre-installed so it works out of the box — add more any time, or switch any addon off from the Addons screen.

## Build from source

Requirements: JDK 17, Android SDK (compileSdk 35), Gradle 8.10+.

```bash
chmod +x gradlew   # the executable bit isn't preserved by all push methods
./gradlew :app:assembleMobileDebug :app:testMobileDebugUnitTest
# APK: app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
```

The complete Gradle 8.10.2 wrapper is included. Set `ANDROID_HOME` to your SDK
or create an untracked `local.properties` with `sdk.dir=/path/to/android-sdk`.
Install Android SDK Platform 35 and Build Tools 35.0.0. The first build needs
internet access to download dependencies.

Debug builds use Android's local debug key by default. To sign with the existing
owner key, set `PLAVIO_SIGNING_STORE`, `PLAVIO_SIGNING_STORE_PASSWORD`,
`PLAVIO_SIGNING_KEY_ALIAS`, and `PLAVIO_SIGNING_KEY_PASSWORD` in your private
`~/.gradle/gradle.properties`. Release builds are unsigned unless that key is
configured. No private keystore is included. A build with a different signing
key cannot update an already-installed owner-signed app; do not uninstall it
without backing up your data. See `FIXES-AND-TESTING.md` for this ZIP's changes
and verification results.

## Tech

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · Media3 ExoPlayer · OkHttp · Room · Coil · kotlinx.serialization

## Architecture

```
app/src/main/java/com/pulsestream/app/
├── data/
│   ├── Addon API client - manifest / catalog / meta / stream requests
│   ├── Addon response parser - resilient parsing for real-world addon data
│   ├── StreamRepository.kt  # multi-addon merging (below)
│   └── db/                  # Room: installed addons (+capabilities), continue-watching
├── ui/
│   ├── screens/     # Home, Search, Addons, Detail, Player, Settings…
│   ├── components/  # glassmorphism cards, SafePosterImage
│   └── theme/       # Material 3 dark glass-glow theme
└── MainActivity.kt  # NavHost + bottom navigation
```

- **Multi-addon merging** — each addon declares its capabilities (catalog / metadata / stream). Catalogs aggregate across addons, metadata is tried across addons until one answers, streams are fetched concurrently from all stream addons and merged.
- **Player** — Media3 ExoPlayer: gesture controls (seek / volume / brightness), double-tap ±10s, speed 0.5x–2x, external subtitles, resume playback.
- **Offline-first** — core library data persists in Room, but addon, metadata, playback, integration, updater and optional sync features make network requests.

## Data use and privacy

Plavio can be used without a Plavio Cloud account. Signing in enables profile restore and automatic versioned backups of portable settings, watchlist, Continue Watching, custom lists/membership and addon metadata to its Supabase-backed service. Ordinary snapshots omit supported integration secrets and protect configured addon URL components. You can separately enable passphrase-protected, end-to-end encrypted sync of supported integration keys and full configured addon URLs; another device needs the same passphrase to unlock them after profile restore. Ordinary library metadata is not covered by that credential encryption. Downloads, device permissions and cloud-login sessions are not portable backup content. Profile switching uses replace-and-restore with local recovery, but is not a parental-control or device-security boundary; real multi-device conflicts and backend authorization remain to be verified. Optional telemetry is disabled by default; enabling it allows Firebase Analytics/Crashlytics collection and optional install-statistics requests. See the [Privacy Policy](site/privacy.html) for data fields, third-party requests, retention uncertainties and controls. Bundled policy changes are source-only until separately published.

The app ships with configured addons. Addon URLs and requests can contain credentials or viewing/search identifiers; addon operators receive the requests sent to their servers. Do not publicly share an addon URL containing a key or token. Addon bundle sharing blocks URLs that appear to contain credentials, but detection is heuristic and not exhaustive.

## License

MIT — built for the love of streaming.
