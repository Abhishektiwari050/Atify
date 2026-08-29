# Survey Explorer 3: Cookie Assist & Auth Test Suite Report

## 1. Observation

### 1.1 Requirement R3: Instant Cookie Auto-Capture & Clipboard Assist
We investigated the manual cookie entry, clipboard handling, and multi-domain cookie synchronization implementations across `SpotifyLoginScreen.kt`, `SpotifySession.kt`, `SpotifyTokenProvider.kt`, and `SpotifyWebPlayer.kt`.

1. **Manual Cookie Entry & Validation (`SpotifyLoginScreen.kt:431-594`)**:
   - The UI provides a dedicated tab `Cookie (sp_dc)` featuring an `OutlinedTextField`, a `Paste Clipboard` button (`OutlinedButton`), and a `Sign In` button (`Button`).
   - In `SpotifyLoginScreen.kt:534-538`, the input handling logic currently only performs basic whitespace trimming:
     ```kotlin
     val clean = cookieInput.trim()
     if (clean.isBlank()) {
         cookieError = "Please paste a valid sp_dc cookie"
         return@Button
     }
     ```
   - When a user pastes a full cookie string copied from DevTools (e.g., `sp_dc=AQB6...; sp_key=...` or `sp_dc=AQB6...`), the literal string is passed to `finishLogin` -> `SpotifyAuth.fetchAccessToken(spDc)`.
   - In `SpotifyAuth.kt:84-89`, `fetchAccessToken` constructs the HTTP Cookie header:
     ```kotlin
     val cookieHeader = buildString {
         append("sp_dc=$spDc")
         if (spKey.isNotEmpty()) {
             append("; sp_key=$spKey")
         }
     }
     ```
   - **Vulnerability / Defect**: Passing `sp_dc=AQB...` results in `Cookie: sp_dc=sp_dc=AQB...`, causing Spotify's `/api/token` endpoint to reject the request with HTTP 401 Unauthorized / Anonymous Token. No regex or multi-format parser (Netscape format, JSON cookie export, URL-encoded string, or quotes) currently exists.

2. **Clipboard Paste Assist (`SpotifyLoginScreen.kt:512-524`)**:
   - `Paste Clipboard` accesses `(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip`.
   - It reads `clip.getItemAt(0).text?.toString().orEmpty().trim()`.
   - It does not automatically sanitize or extract the `sp_dc` value upon pasting, leaving the raw unparsed string in the text field.

3. **Multi-Domain Session Capture & Sync (`SpotifyLoginScreen.kt:136-154, 627-653`, `SpotifyWebPlayer.kt:122-138, 233-247`)**:
   - `extractSpotifyDcCookie()` scans Android `CookieManager` across 5 target URLs:
     - `https://accounts.spotify.com`
     - `https://open.spotify.com`
     - `https://spotify.com`
     - `https://api.spotify.com`
     - `https://.spotify.com`
   - A `LaunchedEffect` polls every 500ms to detect `sp_dc` when web login or redirects complete.
   - However, when manual cookie login succeeds in `finishLogin`:
     - `SpotifySession.setSpDc(activity, spDc)` stores the cookie in `SharedPreferences("spotify_session")`.
     - `SpotifyWebPlayer.refreshLogin(context)` sets the cookie into `CookieManager` only for `https://open.spotify.com` and `https://spotify.com`.
     - It does not explicitly seed `CookieManager` for `https://accounts.spotify.com`, `https://api.spotify.com`, or root `.spotify.com` directly during manual login before web views or account checks might load.

### 1.2 Requirement R4: Automated Test Verification
We inspected the test suite and authentication mechanics in `:spotify` (`SpotifyAuthTest.kt`, `SpotifyAuth.kt`, `SpotifyMapperMatchScoreTest.kt`, `SpotifyMapperPerformanceTest.kt`).

1. **Current Test Coverage (`spotify/src/test/kotlin/com/metrolist/spotify/SpotifyAuthTest.kt:7-23`)**:
   - Currently contains a single integration test: `testSpotifyAuthPipeline()`.
   - It calls `SpotifyAuth.fetchAccessToken(spDc = "TEST_DUMMY_SP_DC")` over the live network to verify that the GitHub Gist secret fetch, Spotify server-time sync, and token endpoint respond.
   - **Gaps Identified**:
     - No offline unit tests for the TOTP algorithm (`generateTotp`) using standard RFC 6238 test vectors.
     - No offline unit tests for Base32 decoding (`base32Decode`) across padding and case variations.
     - No tests verifying JSON deserialization of `Nuance`, `GistFiles`, `ServerTimeResponse`, and `SpotifyInternalToken`.
     - No unit tests for error conditions (401 anonymous token, 500 gist missing, 503 network failure).
     - `generateTotp` and `base32Decode` in `SpotifyAuth.kt` are private, preventing direct unit test execution.

2. **Build and Test Execution Results**:
   - `./gradlew :spotify:test --rerun-tasks --warning-mode all`:
     - **Status**: PASSED (Exit code: 0)
     - **Execution time**: 3m 2s
     - **Output**: 4 test classes passed (including `SpotifyAuthTest` confirming HTTP 401 on dummy cookie, and extensive `SpotifyMapperPerformanceTest` benchmarks).
   - `./gradlew :app:compileReleaseKotlin --warning-mode all`:
     - **Status**: PASSED (Exit code: 0, 2m 30s).
   - `./gradlew :app:assembleRelease --warning-mode all --no-parallel`:
     - **Status**: PASSED (Exit code: 0, 6m 51s).
     - Full release APK assembly with R8 minification, ProGuard shrinking, and resource optimization completed successfully.

---

## 2. Logic Chain

1. **R3 Logic Chain**:
   - *Observation*: Users copy cookies from different environments:
     - Chrome DevTools Application tab (`sp_dc` column value) -> `AQB...`
     - Network tab request headers -> `Cookie: sp_dc=AQB...; sp_key=xyz; ...`
     - Browser cookie extensions / Netscape export -> `.spotify.com TRUE / TRUE ... sp_dc AQB...`
     - Raw JSON exports -> `[{"name":"sp_dc","value":"AQB..."}]`
     - Accidental wrapping quotes -> `"AQB..."`
   - *Inference*: Relying on exact raw input string breaks authentication when header syntax or prefix `sp_dc=` is included.
   - *Recommendation*: Introduce a dedicated `CookieSanitizer` utility (or robust parser) that extracts `sp_dc` and optional `sp_key` using comprehensive regex patterns, automatically unwrapping quotes, prefixes, and multi-cookie header syntax.
   - *Domain Sync Inference*: When a valid cookie is entered manually, syncing it immediately across all Spotify domains in `CookieManager` (`accounts.spotify.com`, `open.spotify.com`, `spotify.com`, `api.spotify.com`, `.spotify.com`) with `CookieManager.getInstance().flush()` guarantees consistency across all WebViews and background services.

2. **R4 Logic Chain**:
   - *Observation*: `SpotifyAuthTest.kt` relies on live network access to GitHub and Spotify. In restricted or offline test environments (or when GitHub Gist rate limits unauthenticated requests to 60/hr), integration tests can fail or become non-deterministic.
   - *Inference*: Pure unit tests for the cryptographic and serialization logic must run offline without network I/O.
   - *Recommendation*:
     - Expose TOTP generation and Base32 decoding as `internal` functions (or in an internal helper `SpotifyTotp`).
     - Add unit tests verifying standard RFC 6238 test vectors (e.g. SHA-1 HMAC with known seeds and timestamps), verifying exact 6-digit outputs and padding.
     - Add unit tests for `CookieSanitizer` verifying all cookie input formats.
     - Add unit tests for JSON models (`SpotifyInternalToken`, `ServerTimeResponse`, `Nuance`).

---

## 3. Caveats

1. **GitHub Gist Rate Limiting**: The TOTP secret is hosted on a public GitHub Gist (`api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef`). Unauthenticated GitHub API calls are limited to 60 requests per hour per IP. In high-frequency testing, live calls could return HTTP 403 unless cached or mocked.
2. **Spotify `sp_dc` Cookie Validity**: Spotify session cookies typically last several months, but can be revoked if the user logs out from all devices or resets their password.
3. **Android CookieManager Process Flush**: In Android WebKit, `CookieManager.setCookie()` is asynchronous in memory. `CookieManager.flush()` must be called to ensure immediate persistence across process boundaries.

---

## 4. Conclusion

1. **Requirement R3 Readiness**:
   - Manual cookie entry UI exists in `SpotifyLoginScreen.kt` with a 2-tab layout ("Web Login" and "Cookie (sp_dc)").
   - **Enhancements Needed**:
     - Add input sanitization and regex extraction for `sp_dc` and `sp_key` (supporting raw token, `sp_dc=value`, cookie header string, JSON export, and Netscape format).
     - Auto-sanitize immediately when the "Paste Clipboard" button is pressed.
     - Sync the validated cookie to `CookieManager` across all Spotify domains (`.spotify.com`, `accounts.spotify.com`, `open.spotify.com`, `api.spotify.com`) with `flush()`.

2. **Requirement R4 Readiness**:
   - Test execution (`./gradlew :spotify:test`) and release compilation (`./gradlew :app:assembleRelease`) are 100% clean and passing.
   - **Enhancements Needed**:
     - Make `generateTotp` and `base32Decode` `internal` in `:spotify` to enable offline RFC 6238 unit testing.
     - Expand `SpotifyAuthTest.kt` with parameterized test cases for TOTP calculation, Base32 decoding, cookie parsing, and mock token deserialization.

---

## 5. Verification Method

To independently verify the findings and test suite:

1. **Execute `:spotify` Unit & Integration Tests**:
   ```bash
   ./gradlew :spotify:test --rerun-tasks --warning-mode all
   ```
   *Expected result*: `BUILD SUCCESSFUL`, `SpotifyAuthTest` and `SpotifyMapperPerformanceTest` pass.

2. **Verify Release Compilation with R8 Shrinking**:
   ```bash
   ./gradlew :app:assembleRelease --warning-mode all --no-parallel
   ```
   *Expected result*: `BUILD SUCCESSFUL`, outputs release APKs in `app/build/outputs/apk/release/`.

3. **Inspect Cookie Parsing and Authentication Code**:
   - `app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt`
   - `app/src/main/java/io/github/sekademi/spotufi/data/api/SpotifySession.kt`
   - `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`
   - `spotify/src/test/kotlin/com/metrolist/spotify/SpotifyAuthTest.kt`
