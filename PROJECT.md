# Project: Comprehensive Spotify Login Architecture & Verification

## Architecture
- **App Module (`:app`)**: Android target (compileSdk 37, minSdk 26, Jetpack Compose, Material 3, MVVM).
  - `io.github.sekademi.spotufi.ui.screens.SpotifyLoginScreen`: Primary login interface with 1280px desktop monitor viewport simulation, anti-bot mitigations, Chrome Custom Tabs fallback, and manual Cookie assist.
  - `io.github.sekademi.spotufi.MainActivity`: SingleTask entrypoint with deep-link intent handling (`spotufi://`, `atify://`).
  - `io.github.sekademi.spotufi.ui.components.CustomTabsHelper`: Themed Chrome Custom Tabs launcher with Atify dark palette.
  - `io.github.sekademi.spotufi.data.api.SpotifySession`: Persistent session manager.
  - `io.github.sekademi.spotufi.di.SpotifyWebPlayer`: Web player session injector.
- **Spotify JVM Module (`:spotify`)**:
  - `com.metrolist.spotify.SpotifyAuth`: TOTP computation (RFC 6238 HMAC-SHA1), server-time synchronization, and access token exchange.
  - `com.metrolist.spotify.CookieSanitizer`: Multi-format cookie parser and sanitization engine.
  - `com.metrolist.spotify.SpotifyAuthTest`: Comprehensive unit and integration test suite.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | 1280px Desktop Viewport Emulation | Configure WebView settings (`useWideViewPort`, `loadWithOverviewMode`, zoom controls) and inject 1280px meta viewport override | M1 | ORIGINAL_REQUEST §R1 |
| 2 | Desktop Chrome User-Agent & Client Hints | Set Windows Desktop Chrome UA, WebSettingsCompat UserAgentMetadata, and Sec-CH-UA headers | M1 | ORIGINAL_REQUEST §R1 |
| 3 | Dark Mode & Black Screen Prevention | Disable algorithmic darkening and force dark via WebSettingsCompat, set #121212 background, enable hardware layer | M1 | ORIGINAL_REQUEST §R1 |
| 4 | Code Quality & Suppress Cleanup | Remove @SuppressLint("SetJavaScriptEnabled"), remove deprecated databaseEnabled, disable allowFileAccess, add onRelease teardown | M1 | ORIGINAL_REQUEST §R1, AGENTS.md |
| 5 | Chrome Custom Tabs (CCT) Integration | Add androidx.browser dependency, implement CustomTabsHelper with Atify branding and fallback package resolution | M2 | ORIGINAL_REQUEST §R2 |
| 6 | Deep-Link Authentication & Callbacks | Add singleTask and intent-filters for spotufi/atify schemes, capture sp_dc deep-link callbacks in MainActivity & MyNavHost | M2 | ORIGINAL_REQUEST §R2 |
| 7 | Cookie Sanitizer & Auto-Extraction | Regex parser supporting raw tokens, key-value headers, Netscape format, JSON exports, URL encoding, and quote unwrapping | M3 | ORIGINAL_REQUEST §R3 |
| 8 | 1-Tap Clipboard Assist & Domain Sync | Paste button auto-sanitization and multi-domain CookieManager sync (.spotify.com, accounts, open, api) with flush() | M3 | ORIGINAL_REQUEST §R3 |
| 9 | Offline RFC 6238 TOTP & Base32 Unit Tests | Internalize TOTP/Base32 helpers, add test vectors, Base32 tests, and JSON model deserialization tests | M4 | ORIGINAL_REQUEST §R4 |
| 10 | End-to-End Build & Release Verification | Pass 100% of :spotify:test and assemble release APK with R8 shrinking (:app:assembleRelease) | M4 | ORIGINAL_REQUEST §R4, Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Desktop Viewport Emulation & Clean WebView Rendering | SpotifyLoginScreen WebView config, 1280px viewport, desktop UA/Client Hints, dark mode fixes, rule compliance | None | PLANNED |
| M2 | Chrome Custom Tabs & Deep-Link Authentication | androidx.browser dependency, CustomTabsHelper, AndroidManifest intent-filters, MainActivity/MyNavHost deep-link handling | None | PLANNED |
| M3 | Instant Cookie Auto-Capture & Multi-Domain Sync | CookieSanitizer, 1-tap clipboard assist, multi-domain CookieManager sync, instant auth trigger | M1 | PLANNED |
| M4 | Automated Test Verification & Integration Tests | SpotifyAuth offline unit tests, RFC 6238 TOTP vectors, CookieSanitizer tests, :spotify:test passing | M3 | PLANNED |
| M5 | Final Verification & Forensic Integrity Audit | Full test suite execution, release APK build with R8, Reviewer, Challenger, and Auditor gating | M1, M2, M3, M4 | PLANNED |

## Interface Contracts
### `CookieSanitizer`
- `fun sanitizeSpDc(rawInput: String): String?`
- `fun sanitizeSpKey(rawInput: String): String?`
- `fun extractCookies(rawInput: String): Map<String, String>`

### `CustomTabsHelper`
- `fun openCustomTab(context: Context, url: String)`

### `MainActivity` ↔ `SpotifyLoginScreen`
- Intent schemes: `spotufi://login?sp_dc=...`, `spotufi://callback?sp_dc=...`, `atify://login?sp_dc=...`, `atify://callback?sp_dc=...`
- Session dispatch: `SpotifySession.setSpDc(context, spDc)` -> `SpotifyAuth.fetchAccessToken(spDc)` -> navigate to `Routes.Home.route`.

## Code Layout
- `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`: Auth engine & TOTP
- `spotify/src/main/kotlin/com/metrolist/spotify/CookieSanitizer.kt`: Cookie extraction utility
- `spotify/src/test/kotlin/com/metrolist/spotify/SpotifyAuthTest.kt`: Unit & integration tests
- `gradle/libs.versions.toml`: Dependency versions
- `app/build.gradle.kts`: App module dependencies & build configuration
- `app/src/main/AndroidManifest.xml`: Activity configuration & intent-filters
- `app/src/main/java/io/github/sekademi/spotufi/MainActivity.kt`: Deep link lifecycle handler
- `app/src/main/java/io/github/sekademi/spotufi/ui/components/CustomTabsHelper.kt`: CCT launcher
- `app/src/main/java/io/github/sekademi/spotufi/ui/navigation/MyNavHost.kt`: Navigation routes & deep links
- `app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt`: Login screen UI & WebView
