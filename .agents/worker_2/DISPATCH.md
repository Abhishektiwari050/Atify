# Dispatch Assignment - Worker 2

## 2026-08-27T18:44:24Z
Task Assignment: Worker 2: App UI, WebView & CCT
Working directory: c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_2

File Ownership:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/github/sekademi/spotufi/ui/components/CustomTabsHelper.kt`
- `app/src/main/java/io/github/sekademi/spotufi/MainActivity.kt`
- `app/src/main/java/io/github/sekademi/spotufi/ui/navigation/MyNavHost.kt`
- `app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt`

Tasks:
1. Dependencies: Add `browser = "1.8.0"` to `libs.versions.toml` and `implementation(libs.androidx.browser)` to `app/build.gradle.kts`.
2. Create `CustomTabsHelper.kt`: Themed CCT with Atify dark palette (`0xFF18251F` toolbar, `0xFF121212` navigation bar), dark color scheme, title enabled, safe fallback.
3. Manifest & Deep-Links: Add `android:launchMode="singleTask"` and intent-filters for `spotufi` & `atify` schemes (`callback`, `login` hosts). Handle deep links in `MainActivity.kt` and `MyNavHost.kt`.
4. Refactor & Enhance `SpotifyLoginScreen.kt`:
   - R1: 1280px desktop monitor viewport emulation (`useWideViewPort = true`, `loadWithOverviewMode = true`, zoom controls enabled, displayZoomControls = false, JS meta viewport injection).
   - Desktop Chrome UA, WebSettingsCompat UserAgentMetadata, desktop Sec-CH-UA headers, anti-bot JS spoofing (`navigator.userAgentData`, `navigator.webdriver = undefined`, `window.screen.width = 1280`).
   - Android 13-16 dark void fix: `WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)`, `WebSettingsCompat.setForceDark(settings, FORCE_DARK_OFF)`, background `#121212`, `LAYER_TYPE_HARDWARE`.
   - Remove forbidden `@SuppressLint("SetJavaScriptEnabled")`, remove deprecated `databaseEnabled`, disable `allowFileAccess`, add `onRelease` teardown.
   - R2: Integrate CCT action button in Web Login and Cookie tabs.
   - R3: Connect `CookieSanitizer` to manual cookie field & 1-tap clipboard paste button.
   - Multi-domain `CookieManager` sync (`.spotify.com`, `accounts.spotify.com`, `open.spotify.com`, `api.spotify.com`, `spotify.com`) with `cookieManager.flush()`.
5. Compile and build checks:
   - `./gradlew :app:compileDebugKotlin --warning-mode all`
   - `./gradlew :app:assembleRelease --warning-mode all --no-parallel`
