## 2026-08-28T17:15:10Z
You are teamwork_preview_worker (Worker 2 gen 2: App UI, WebView & CCT).
Working directory: c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_2_gen2

File Ownership (you exclusively own and write these files):
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/github/sekademi/spotufi/ui/components/CustomTabsHelper.kt`
- `app/src/main/java/io/github/sekademi/spotufi/MainActivity.kt`
- `app/src/main/java/io/github/sekademi/spotufi/ui/navigation/MyNavHost.kt`
- `app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt`

Reference Files (Read-only):
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\ORIGINAL_REQUEST.md`
- `c:\Users\abhis\Downloads\MUSIC APP\AGENTS.md`
- `c:\Users\abhis\Downloads\MUSIC APP\PROJECT.md`
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\explorer_survey_1\handoff.md`
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\explorer_survey_2\handoff.md`

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Core Tasks:
1. Read reference files and guidelines. Note: @Suppress and @SuppressLint are NEVER allowed per AGENTS.md.
2. Dependencies:
   - Add `browser = "1.8.0"` to `gradle/libs.versions.toml` and `implementation(libs.androidx.browser)` to `app/build.gradle.kts`.
3. Create `CustomTabsHelper.kt`:
   - Implement themed CCT with Atify dark palette (`0xFF18251F` toolbar, `0xFF121212` navigation bar), dark color scheme, title enabled, and safe package fallback.
4. Manifest & Deep-Links:
   - In `app/src/main/AndroidManifest.xml`, add `android:launchMode="singleTask"` and intent-filters for schemes `spotufi` and `atify` (hosts `callback`, `login`).
   - In `MainActivity.kt` and `MyNavHost.kt`, handle deep-link intents (`onNewIntent`, `onCreate`), extract `sp_dc`, and process login.
5. Refactor & Enhance `SpotifyLoginScreen.kt`:
   - Requirement R1: Configure 1280px desktop monitor viewport emulation (`useWideViewPort = true`, `loadWithOverviewMode = true`, `setSupportZoom(true)`, `builtInZoomControls = true`, `displayZoomControls = false`).
   - Inject JavaScript on page start/finish to enforce `<meta name="viewport" content="width=1280, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">`.
   - Set Windows Desktop Chrome User-Agent, `WebSettingsCompat.setUserAgentMetadata`, desktop `Sec-CH-UA` headers on `loadUrl`, and anti-bot JS spoofing (`navigator.userAgentData`, `navigator.webdriver = undefined`, `window.screen.width = 1280`).
   - Prevent blank/black void screens on Android 13–16: `WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)`, `WebSettingsCompat.setForceDark(settings, FORCE_DARK_OFF)`, background `#121212`, `setLayerType(View.LAYER_TYPE_HARDWARE, null)`.
   - Remove forbidden `@SuppressLint("SetJavaScriptEnabled")`, remove deprecated `settings.databaseEnabled`, disable `allowFileAccess = false`, add `onRelease` teardown to `AndroidView`.
   - Requirement R2: Integrate CCT action button in both Web Login and Cookie tabs.
   - Requirement R3: Connect `CookieSanitizer` to manual cookie field and 1-tap clipboard paste button.
   - In `finishLogin`, synchronize `CookieManager` across all 5 Spotify domains (`.spotify.com`, `accounts.spotify.com`, `open.spotify.com`, `api.spotify.com`, `spotify.com`) with `cookieManager.flush()`.
6. Run compile and build checks:
   - `./gradlew :app:compileDebugKotlin --warning-mode all`
   - `./gradlew :app:assembleRelease --warning-mode all --no-parallel`
7. Write a complete handoff report to `c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_2_gen2\handoff.md` and message the orchestrator.
