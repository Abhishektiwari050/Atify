# Requirement R1 Technical Investigation Report: Desktop Monitor Emulation & Clean WebView Rendering

## 1. Observation

### 1.1 Current File & Implementation Details
- **Target File**: `app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt` (704 lines)
- **Dependency**: `gradle/libs.versions.toml` line 22 (`webkit = "1.16.0"`) and line 58 (`androidx.webkit:webkit:1.16.0`).
- **Target SDK**: `compileSdk = 37` (Android 17 preview/stable), `minSdk = 26`, running across Android 13 (API 33), 14 (API 34), 15 (API 35), and 16 (API 36).

### 1.2 Observed Code in `SpotifyLoginScreen.kt`
1. **Lint Suppressions (Violation of Project Rules)**:
   - Line 107: `@SuppressLint("SetJavaScriptEnabled")` is applied directly above `fun SpotifyLoginScreen`.
   - Per `AGENTS.md`: *"@Suppress / @SuppressLint never allowed. Fix the code instead. If a warning has no modern replacement, restructure the code to avoid it."*

2. **WebView Initialization & Background**:
   - Lines 267–280:
     ```kotlin
     AndroidView(
         modifier = Modifier.fillMaxSize(),
         factory = { ctx ->
             val cookieManager = CookieManager.getInstance()
             cookieManager.setAcceptCookie(true)

             WebView(ctx).apply {
                 webViewRef = this
                 layoutParams = ViewGroup.LayoutParams(
                     ViewGroup.LayoutParams.MATCH_PARENT,
                     ViewGroup.LayoutParams.MATCH_PARENT
                 )
                 setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                 cookieManager.setAcceptThirdPartyCookies(this, true)
     ```
   - Hardware acceleration layer (`setLayerType(View.LAYER_TYPE_HARDWARE, null)`) is not explicitly specified.
   - Dark/Light mode theme integration with `WebSettingsCompat` is absent.

3. **WebSettings Configuration**:
   - Lines 282–295:
     ```kotlin
     settings.apply {
         javaScriptEnabled = true
         domStorageEnabled = true
         databaseEnabled = true
         loadWithOverviewMode = true
         useWideViewPort = true
         mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
         setSupportMultipleWindows(false)
         javaScriptCanOpenWindowsAutomatically = true
         allowContentAccess = true
         allowFileAccess = true
         cacheMode = WebSettings.LOAD_DEFAULT
         // Use default system user agent so Google & Spotify accept the browser
     }
     ```
   - `settings.userAgentString` is intentionally left as default mobile User-Agent (`Mozilla/5.0 (Linux; Android ...; Mobile ...)`).
   - `settings.databaseEnabled = true` is deprecated since API 19.
   - `settings.allowFileAccess = true` enables filesystem access on a remote HTTPS browser session.
   - Zoom controls (`setSupportZoom`, `builtInZoomControls`, `displayZoomControls`) are not configured.
   - No `WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)` or `WebSettingsCompat.setForceDark` configuration exists.
   - Compiler warnings observed in `./gradlew :app:compileDebugKotlin`:
     - `databaseEnabled` is deprecated in Java.
     - `TabRow` and `tabIndicatorOffset` are deprecated in Material 3 Compose.

4. **URL Loading & HTTP Request Headers**:
   - Line 346:
     ```kotlin
     loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
     ```
   - No Client Hints headers (`Sec-CH-UA`, `Sec-CH-UA-Mobile`, `Sec-CH-UA-Platform`) are provided during initial URL navigation or client requests.

5. **Client Script & Bot Detection Handling**:
   - Lines 304–344: `WebViewClient` implementation does not inject viewport overrides or spoof bot-detection properties (`navigator.webdriver`, `navigator.userAgentData`, `window.screen.width`).

6. **Cookie Syncing & Lifecycle**:
   - Lines 135–154: `LaunchedEffect(Unit)` polls `extractSpotifyDcCookie()` every 500ms.
   - Lines 630–653: `extractSpotifyDcCookie()` queries `cookieManager.getCookie(url)` across 5 Spotify domains without executing `cookieManager.flush()`.
   - AndroidView has no `onRelease` teardown callback, risking WebView leaks on configuration/navigation changes.

---

## 2. Logic Chain

### 2.1 Viewport Simulation (1280px Desktop Monitor Emulation)
1. **Observation**: `SpotifyLoginScreen.kt` currently sets `useWideViewPort = true` and `loadWithOverviewMode = true`, but does not set a desktop User-Agent or inject viewport meta adjustments.
2. **Mechanism**:
   - When loading `accounts.spotify.com`, Spotify's responsive web page contains `<meta name="viewport" content="width=device-width, initial-scale=1">`.
   - On mobile devices, WebView evaluates `width=device-width` using physical screen CSS density (typically 360px–412px width).
   - Even if `useWideViewPort` is true, the presence of `width=device-width` forces Chromium into mobile responsive mode, rendering a cramped mobile page or causing redirection to mobile-specific auth flows.
3. **Inference & Fix**:
   - To achieve a true 1280px desktop monitor simulation:
     a) Configure `WebSettings`:
        - `settings.useWideViewPort = true`
        - `settings.loadWithOverviewMode = true`
        - `settings.setSupportZoom(true)`
        - `settings.builtInZoomControls = true`
        - `settings.displayZoomControls = false` (enables pinch-to-zoom without obsolete +/- on-screen buttons)
     b) Inject JavaScript in `onPageStarted` / `onPageFinished` to force the viewport tag to 1280px:
        ```javascript
        (function() {
            try {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.setAttribute('content', 'width=1280, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
            } catch(e) {}
        })();
        ```
     c) With `width=1280` and `loadWithOverviewMode = true`, Chromium automatically scales the full 1280px desktop web layout to fit the mobile screen width with zero horizontal clipping.

### 2.2 Desktop User-Agent & Client Hints Alignment (Bot-Detection Protection)
1. **Observation**: Spotify and its anti-bot layers (Akamai / Cloudflare / Arkose Labs) cross-examine the HTTP `User-Agent`, HTTP Client Hints (`Sec-CH-UA`, `Sec-CH-UA-Mobile`, `Sec-CH-UA-Platform`), JavaScript `navigator.userAgent`, `navigator.userAgentData`, and `navigator.webdriver`.
2. **Mechanism**:
   - If `settings.userAgentString` is set to a Windows desktop Chrome UA (e.g. `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36`), but the underlying Android WebView sends Client Hints headers with `Sec-CH-UA-Platform: "Android"` and `Sec-CH-UA-Mobile: ?1`, bot detection flags the discrepancy as a bot scraper and serves a blank page or 403 Forbidden.
   - If `navigator.webdriver` is detected as `true`, automated bot blocks are triggered.
3. **Inference & Fix**:
   - a) Set Desktop Chrome User-Agent:
     ```kotlin
     private const val DESKTOP_USER_AGENT =
         "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
     settings.userAgentString = DESKTOP_USER_AGENT
     ```
   - b) Set native `UserAgentMetadata` via `androidx.webkit:webkit:1.16.0`:
     ```kotlin
     if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
         val metadata = UserAgentMetadata.Builder()
             .setPlatform("Windows")
             .setPlatformVersion("10.0.0")
             .setArchitecture("x86")
             .setModel("")
             .setMobile(false)
             .setBitness(64)
             .setFullVersionList(listOf(
                 UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome").setFullVersion("131.0.6778.86").build(),
                 UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setFullVersion("131.0.6778.86").build(),
                 UserAgentMetadata.BrandVersion.Builder().setBrand("Not_A Brand").setFullVersion("24.0.0.0").build()
             ))
             .build()
         WebSettingsCompat.setUserAgentMetadata(settings, metadata)
     }
     ```
   - c) Provide custom HTTP headers on `loadUrl`:
     ```kotlin
     val desktopHeaders = mapOf(
         "Sec-CH-UA" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
         "Sec-CH-UA-Mobile" to "?0",
         "Sec-CH-UA-Platform" to "\"Windows\"",
         "Accept-Language" to "en-US,en;q=0.9",
         "Upgrade-Insecure-Requests" to "1"
     )
     loadUrl(loginUrl, desktopHeaders)
     ```
   - d) Inject early JavaScript in `onPageStarted`:
     ```javascript
     (function() {
         try {
             if (navigator.userAgentData) {
                 Object.defineProperty(navigator, 'userAgentData', {
                     get: function() {
                         return {
                             brands: [
                                 {brand: 'Google Chrome', version: '131'},
                                 {brand: 'Chromium', version: '131'},
                                 {brand: 'Not_A Brand', version: '24'}
                             ],
                             mobile: false,
                             platform: 'Windows'
                         };
                     }
                 });
             }
             Object.defineProperty(navigator, 'webdriver', {
                 get: function() { return undefined; }
             });
             // Spoof desktop screen geometry for responsive JS queries
             Object.defineProperty(window.screen, 'width', { get: function() { return 1280; } });
             Object.defineProperty(window.screen, 'availWidth', { get: function() { return 1280; } });
         } catch(e) {}
     })();
     ```

### 2.3 Dark/Light Mode Theme & Blank Void / Pitch Black Screen Prevention (Android 13–16)
1. **Observation**: On Android 13 (API 33), 14 (API 34), 15 (API 35), and 16 (API 36), WebViews apply "Algorithmic Darkening" when the device is in dark theme.
2. **Mechanism**:
   - Spotify's web authentication pages are already natively styled with a dark theme (dark background `#121212`, light text `#FFFFFF`).
   - When WebView's algorithmic darkening runs on an already-dark page, the algorithmic darkening engine inverts backgrounds or darkens text elements, resulting in black-on-black text or a completely pitch-black/empty void screen.
3. **Inference & Fix**:
   - Explicitly disable algorithmic darkening via `WebSettingsCompat`:
     ```kotlin
     if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
         WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
     }
     if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
         WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
     }
     ```
   - Set the WebView background color to match `#121212` and enable hardware layer acceleration:
     ```kotlin
     setBackgroundColor(android.graphics.Color.parseColor("#121212"))
     setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
     ```
   - Ensure the outer Compose container background matches `AtifyDark` (`#18251F`) to eliminate flashes of white during initial URL load.

### 2.4 Cookie Syncing & Robust Session Capture
1. **Observation**: `CookieManager` in Android stores cookies in an asynchronous in-memory SQLite buffer. `extractSpotifyDcCookie()` reads cookies across 5 endpoints without calling `flush()`.
2. **Mechanism**: If `flush()` is omitted, `sp_dc` set by Spotify's login redirects may not be immediately queryable via `getCookie()`, causing a delay or missing the session capture.
3. **Inference & Fix**:
   - Call `cookieManager.flush()` before querying cookies in `extractSpotifyDcCookie()`.
   - When `sp_dc` is detected:
     a) Store in `SpotifySession.setSpDc(context, spDc)`.
     b) Seed the cookie across all Spotify domains with explicit attributes (`Domain=.spotify.com; Path=/; Secure; SameSite=Lax`).
     c) Call `cookieManager.flush()` so `SpotifyWebPlayer` immediately has the authenticated session.
     d) Trigger `SpotifyAuth.fetchAccessToken(spDc)`.

### 2.5 Code Cleanliness & Rule Compliance
1. **Observation**: Line 107 has `@SuppressLint("SetJavaScriptEnabled")`.
2. **Inference & Fix**:
   - Remove `@SuppressLint("SetJavaScriptEnabled")` and ensure no `@Suppress` annotations are used in the file.
   - Remove deprecated `settings.databaseEnabled = true`.
   - Set `settings.allowFileAccess = false` for improved security.
   - Add `onRelease` handler to `AndroidView` to properly call `stopLoading()`, `removeAllViews()`, and `destroy()`.

---

## 3. Caveats
1. **Google Single Sign-On (SSO) in WebView**:
   - Google maintains an intentional block against OAuth logins inside embedded WebViews (`disallowed_useragent`). Setting a desktop Chrome User-Agent frequently bypasses this, but if a user encounters Google SSO restrictions, the existing direct Cookie (sp_dc) entry tab and external Chrome browser fallback (R2/R3) provide a 100% reliable alternate path.
2. **Physical Device Aspect Ratios**:
   - On ultra-narrow tall screens (e.g. 21:9), scaling a 1280px layout down to screen width results in smaller font sizes; pinch-to-zoom is enabled (`setSupportZoom(true)`) to ensure high readability.
3. **Network / Gist Availability**:
   - `SpotifyAuth.fetchAccessToken` relies on fetching the TOTP secret from the community Gist and server-time from Spotify. If offline or rate-limited, appropriate retry logic is already in place.

---

## 4. Conclusion
1. Requirement R1 can be fully satisfied with precise, non-invasive enhancements to `SpotifyLoginScreen.kt`.
2. **Summary of Recommended Changes**:
   - **Desktop Viewport**: Enable `useWideViewPort`, `loadWithOverviewMode`, `setSupportZoom(true)`, `builtInZoomControls(true)`, `displayZoomControls(false)`, and inject 1280px meta viewport override JS on page start/finish.
   - **Desktop UA & Client Hints**: Set desktop Windows Chrome UA, configure `UserAgentMetadata` via `WebSettingsCompat`, send desktop `Sec-CH-UA` headers on `loadUrl`, and spoof `navigator.userAgentData` / `navigator.webdriver` in early JS.
   - **Rendering & Dark Theme**: Disable `ALGORITHMIC_DARKENING` and `FORCE_DARK` via `WebSettingsCompat`, set background color `#121212`, enable hardware acceleration `LAYER_TYPE_HARDWARE`.
   - **Cookie Synchronization**: Add `cookieManager.flush()` before extraction and after login completion; seed cookies across `.spotify.com`.
   - **Architecture & Rules**: Remove `@SuppressLint("SetJavaScriptEnabled")`, remove deprecated `databaseEnabled`, disable `allowFileAccess`, add `AndroidView` `onRelease` disposal.

---

## 5. Verification Method

### 5.1 Static Verification & Unit Tests
- Execute Spotify module unit tests:
  ```powershell
  .\gradlew :spotify:test --warning-mode all
  ```
  Expected result: 100% test pass (`SpotifyAuthTest.testSpotifyAuthPipeline`).

- Execute Kotlin compilation check:
  ```powershell
  .\gradlew :app:compileDebugKotlin --warning-mode all
  ```
  Expected result: BUILD SUCCESSFUL with 0 compiler errors.

- Check for forbidden `@Suppress` / `@SuppressLint` annotations:
  ```powershell
  git grep "@SuppressLint" app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt
  git grep "@Suppress" app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt
  ```
  Expected result: 0 matches found.

### 5.2 Dynamic Verification & Rendering Checklist
- Run app on Android 13, 14, 15, and 16 (physical device or emulator):
  1. Open Spotify Login screen.
  2. Verify the Web Login tab renders the 1280px desktop layout without pitch-black/blank void screens.
  3. Verify pinch-to-zoom allows smooth zooming into login fields without crashing.
  4. Complete login and verify automatic `sp_dc` capture and transition to `HomeScreen`.

### 5.3 Invalidation Conditions
- Any occurrence of pitch-black / blank screens upon WebView load.
- Any bot detection / 403 Forbidden blocking from Spotify on standard login flow.
- Failure of `./gradlew :spotify:test` or `./gradlew :app:compileDebugKotlin`.
