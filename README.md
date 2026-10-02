# Aurora Browser

A real, installable Android web browser — not a demo. Renders the real web with the
Android System WebView (Chromium), with real multi-tab browsing, bookmarks, history,
downloads, incognito mode, per-site permissions, and find-in-page. **100% on-device:
no backend, no accounts, no analytics, no cloud SDKs.**

> **Placeholder branding.** The app name "Aurora Browser" and the package
> `com.aurora.browser` are placeholders — replace them with your own before any
> public release (search the repo for `com.aurora.browser`).

## Features

- Real web rendering (System WebView + androidx.webkit) with JavaScript toggle,
  Safe Browsing, mixed-content blocking, and algorithmic web darkening
- Address bar with exact URL/search resolution and 4 built-in engines + custom
- Multi-tab browsing (20 normal + 10 incognito, LRU discard) with tab switcher
- Bookmarks with folders, full history with search, system DownloadManager integration
- Incognito mode: no history writes; cookies/cache/site data wiped when the last
  incognito tab closes (honest scope — see Privacy note)
- Per-site camera / microphone / location permissions with remember-per-origin
- File upload, fullscreen video, popup-windows-as-tabs, desktop-site toggle, text scaling
- Local offline/SSL/crash error pages with working retry
- Find in page, share, light/dark/system theme

## Prerequisites

- **JDK 17** (Temurin or similar)
- **Android SDK** with platform 35, e.g. via Android Studio or `sdkmanager`
- **Gradle 8.10+** on your PATH (CI provisions it automatically; optionally run
  `gradle wrapper` once on a connected machine to generate `./gradlew`)

## Build the debug APK

```bash
git clone <your-repo-url>
cd aurora-browser
gradle assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Every push/PR to `main` also builds this in GitHub Actions and uploads it as the
`app-debug` artifact — download it without touching a terminal.

## Install on your phone

1. Copy `app-debug.apk` to your phone (USB, Drive, etc.).
2. Open it on the phone and allow "Install unknown apps" when asked.
3. Or via ADB: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Requires Android 7.0 (API 24) or newer.

## Release signing (one time)

Generate a keystore (back it up — **losing it means you can never update the app**):

```bash
keytool -genkeypair -v -keystore aurora-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias aurora
```

Create `local.properties` in the project root (**never commit this file**):

```properties
storeFile=/absolute/path/to/aurora-release.jks
storePassword=<your keystore password>
keyAlias=aurora
keyPassword=<your key password>
```

Then:

```bash
gradle assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

For CI releases, use the **Release build** workflow (Actions → Release build →
Run workflow) with these repository secrets set (values never appear in the repo):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 aurora-release.jks` output |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Release checklist: bump `versionCode`/`versionName` in `app/build.gradle.kts`, test the
**release** build on a real phone (R8 can break reflection), back up the keystore,
tag the release (`v1.0.0`).

## Project layout

```
app/src/main/java/com/aurora/browser/
  MainActivity.kt        single Activity: theme + nav + file-chooser launchers + fullscreen
  AuroraApp.kt           Application + AppContainer (manual DI, no Hilt in v1)
  navigation/            Routes + NavGraph
  ui/theme/              Material 3 color schemes (dynamic + seed fallback), typography
  ui/components/         toolbar, address bar, tab badge, find-in-page bar, offline banner
  ui/screens/            Splash, Home, Browser, Tabs (+ Bookmarks/History/Downloads/
                         Settings*/About from the settings worker)
  browser/               TabManager, AuroraWebViewClient/ChromeClient, UrlResolver,
                         SearchEngines, FileChooserDelegate, FullscreenHandler
  viewmodel/             BrowserViewModel (owns TabManager), TabsViewModel
  data/                  Room database + repositories (data worker)
  permissions/           PermissionManager (permissions worker)
  downloads/             DownloadHandler + tracker (downloads worker)
```

## Privacy note

All browsing data (history, bookmarks, downloads list, tabs, settings) stays on your
device — there is no server to send it to. Search suggestions come from your local
history/bookmarks only. **Incognito keeps this device from remembering your session;
it does not hide you from your network, employer, or the websites you visit.**

## License

Apache 2.0 — see [LICENSE](LICENSE).
