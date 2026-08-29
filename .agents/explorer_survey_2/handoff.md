# Handoff Report: Chrome Custom Tabs (CCT) & Deep-Link Authentication (R2)

## 1. Observation

### 1.1 Dependency & Build Configuration Status
- **File**: `gradle/libs.versions.toml`
  - In `[versions]` (lines 1–28) and `[libraries]` (lines 29–68), there is **no reference** to `androidx.browser:browser`.
  - The latest compatible version in the AndroidX ecosystem is `1.8.0` (compatible with `minSdk = 26`, `compileSdk = 37`, and AGP `9.2.1`).
- **File**: `app/build.gradle.kts`
  - In `dependencies` block (lines 113–186), `libs.webkit` is included (line 164), but `libs.browser` or `androidx.browser:browser` is absent.
  - `namespace = "io.github.sekademi.spotufi"` (line 35) and `applicationId = "com.atify.music"` (line 39).
  - Target SDK is 34, compileSdk is 37, minSdk is 26.

### 1.2 Current Browser Launch Implementation
- **File**: `app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt`
  - Lines 580–589 contain an unstyled generic browser fallback inside the Cookie tab instructions:
    ```kotlin
    OutlinedButton(
        onClick = {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"))
            context.startActivity(browserIntent)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AtifySand)
    ) {
        Text("Open Spotify in External Chrome Browser", fontSize = 12.sp)
    }
    ```
  - This launches an external browser application directly without Custom Tabs styling, animations, toolbar branding, or deep-link callback integration.

### 1.3 AndroidManifest & Activity Launch Configuration
- **File**: `app/src/main/AndroidManifest.xml`
  - `MainActivity` declaration (lines 25–36):
    ```xml
    <activity
        android:name=".MainActivity"
        android:screenOrientation="portrait"
        android:exported="true"
        android:label="@string/app_name"
        android:theme="@style/splashScreenTheme">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    ```
  - `MainActivity` currently has **no deep link intent-filters** (no `<action android:name="android.intent.action.VIEW" />`, no `<category android:name="android.intent.category.BROWSABLE" />`, no `<data android:scheme="..." />`).
  - `MainActivity` does not declare `android:launchMode="singleTask"` or `singleTop`, causing deep links to launch duplicate instances rather than delivering intents via `onNewIntent`.

### 1.4 Deep Link Routing in MainActivity & Compose Navigation
- **File**: `app/src/main/java/io/github/sekademi/spotufi/MainActivity.kt`
  - Lines 31–63: `onCreate` does not inspect `intent.data` for incoming deep links or session tokens.
  - `onNewIntent(intent: Intent)` is not implemented.
- **File**: `app/src/main/java/io/github/sekademi/spotufi/ui/navigation/MyNavHost.kt`
  - Lines 86–88:
    ```kotlin
    composable(Routes.Login.route){
        SpotifyLoginScreen(navHostController)
    }
    ```
  - No `deepLinks` parameter configured in the `composable()` declaration.

### 1.5 Spotify Session Token & Cookie Pipeline
- **File**: `app/src/main/java/io/github/sekademi/spotufi/data/api/SpotifySession.kt`
  - Lines 11–30: Holds `spDc(context)` and `setSpDc(context, value)` in `SharedPreferences("spotify_session")`.
- **File**: `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`
  - Lines 67–105: `SpotifyAuth.fetchAccessToken(spDc)` acquires Spotify server time, fetches TOTP nuance, generates HMAC-SHA1 TOTP, and executes GET to `https://open.spotify.com/api/token` with `Cookie: sp_dc=...` to exchange for `SpotifyInternalToken` (bearer access token).
- **File**: `app/src/main/java/io/github/sekademi/spotufi/di/SpotifyWebPlayer.kt`
  - Lines 228–247: `refreshLogin(context)` seeds `sp_dc` into the `CookieManager` for `.spotify.com` and reloads the web player engine.

---

## 2. Logic Chain

1. **Requirement R2 Specification**:
   - The user requires Chrome Custom Tabs (CCT) as a native browser fallback option for authenticating with Spotify, customized with Atify branding/colors, capturing session callbacks via deep links or redirect interceptors, and storing session tokens/cookies.

2. **Dependency Resolution**:
   - Because `CustomTabsIntent` and `CustomTabColorSchemeParams` require `androidx.browser:browser`, adding `browser = "1.8.0"` to `gradle/libs.versions.toml` and `implementation(libs.androidx.browser)` to `app/build.gradle.kts` provides first-class support for CCT without any deprecated APIs or lint warnings.

3. **CCT Theme & Branding Integration**:
   - Atify's color scheme defines `AtifyDark` (`0xFF18251F`), `AtifySage` (`0xFF6F8067`), `AtifySand` (`0xFFDBC7A8`), and `AppBackground` (`0xFF121212`).
   - `CustomTabsIntent.Builder()` should configure:
     - `setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF18251F.toInt()).setNavigationBarColor(0xFF121212.toInt()).build())`
     - `setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)`
     - `setShowTitle(true)`
     - `setShareState(CustomTabsIntent.SHARE_STATE_OFF)`
     - Fallback detection using package checks for Chrome / Custom Tabs providers.

4. **Deep-Link Architecture & Callback Capture**:
   - When authenticating via CCT or external browser tools, redirecting to custom schemes (`spotufi://callback`, `atify://callback`, `spotufi://login`, `atify://login`) enables seamless app resumption.
   - Adding `<data android:scheme="spotufi" android:host="callback" />` and `<data android:scheme="atify" android:host="callback" />` to `AndroidManifest.xml` under `MainActivity` ensures the Android OS routes redirects back into the running app.
   - Setting `android:launchMode="singleTask"` on `MainActivity` ensures the activity is brought to the front without recreation, delivering new deep links into `onNewIntent`.
   - `MainActivity.kt` and `MyNavHost.kt` extract `sp_dc` (and/or OAuth `code` / `access_token`) from query parameters (`Uri.getQueryParameter("sp_dc")`), immediately executing `SpotifySession.setSpDc(context, spDc)` and `SpotifyAuth.fetchAccessToken(spDc)`.

5. **User Flow & Fallback Architecture in `SpotifyLoginScreen.kt`**:
   - **Tab 1 (Web Login)**: Add a CCT action button ("Open in Chrome Custom Tab") alongside the embedded WebView. If a user encounters bot-detection/CAPTCHA in WebView, tapping CCT opens Spotify in their authentic Chrome environment with Atify dark toolbar.
   - **Tab 2 (Cookie / Direct)**: Enhance the "Open in External Chrome Browser" button to launch CCT with custom Atify styling, giving users the easiest path to log into their web account and return.
   - **Deep Link Handler**: On receiving `spotufi://login?sp_dc=...` or `spotufi://callback?sp_dc=...`, trigger `finishLogin()` automatically, pop the backstack, and transition to `Routes.Home.route`.

---

## 3. Caveats

1. **Chrome Sandbox & Cookie Isolation**:
   - Chrome Custom Tabs runs inside the external browser process (e.g. Chrome) and isolates its CookieJar from native Android applications for security. An Android app cannot directly query Chrome's `CookieManager` via CCT.
   - Deep-link redirection (`spotufi://callback` / `spotufi://login?sp_dc=...`) or user-assisted cookie capture is the proven, standard method for completing session transfer when CCT is used.
2. **Multiple Browser Support**:
   - Some devices may not have Chrome installed (e.g. de-Googled devices with Brave or Firefox). The implementation must use a safe fallback that resolves available Custom Tabs packages, falling back to `Intent.ACTION_VIEW` if no CCT package exists.
3. **No `@Suppress` / `@SuppressLint` Policy**:
   - All CCT and intent-handling code must strictly comply with project guidelines: no suppression annotations, explicit types, and clean resource management.

---

## 4. Conclusion

The existing implementation relies on a generic `Intent.ACTION_VIEW` without CCT integration, lacks `androidx.browser:browser` in the build system, has no deep link intent filters in `AndroidManifest.xml`, and does not handle deep-link intents in `MainActivity` / `MyNavHost`.

To implement Requirement R2 cleanly and durably:
1. **Dependencies**: Add `browser = "1.8.0"` to `gradle/libs.versions.toml` and `implementation(libs.androidx.browser)` to `app/build.gradle.kts`.
2. **Manifest**: Add `android:launchMode="singleTask"` and intent-filters for schemes `spotufi` and `atify` (hosts: `callback`, `login`) to `MainActivity` in `app/src/main/AndroidManifest.xml`.
3. **CCT Launcher Utility**: Create a reusable `CustomTabsHelper` with Atify Dark styling (`0xFF18251F` toolbar, `0xFF121212` navigation bar, dark color scheme, title enabled, graceful fallback).
4. **Deep-Link Callback Handler**:
   - Implement `onNewIntent` and intent extraction in `MainActivity.kt`.
   - Add `navDeepLink` patterns to `Routes.Login.route` in `MyNavHost.kt`.
   - Wire deep-link parameter capture directly into `finishLogin` in `SpotifyLoginScreen.kt`.
5. **UI Integration**: Add CCT launch buttons to both Web Login and Cookie tabs in `SpotifyLoginScreen.kt`.

---

## 5. Verification Method

To independently verify the architecture and implementation:

1. **Dependency Verification**:
   ```bash
   ./gradlew :app:dependencies --configuration compileClasspath --warning-mode all
   ```
   Verify `androidx.browser:browser:1.8.0` is present.

2. **Deep-Link Intent Filter Verification via ADB**:
   ```bash
   adb shell am start -W -a android.intent.action.VIEW -d "spotufi://login?sp_dc=AQB_TEST_COOKIE" com.atify.music
   adb shell am start -W -a android.intent.action.VIEW -d "atify://callback?sp_dc=AQB_TEST_COOKIE" com.atify.music
   ```
   Verify the app opens directly to `SpotifyLoginScreen` / `MainActivity` and captures the `sp_dc` parameter.

3. **Compilation & Build Verification**:
   ```bash
   ./gradlew :spotify:test --warning-mode all
   ./gradlew :app:compileDebugKotlin --warning-mode all
   ./gradlew :app:assembleRelease --no-daemon --warning-mode all
   ```
   Confirm zero compilation errors, zero lint suppression violations, and clean R8 minification.
