## 2026-08-28T17:15:10Z

You are teamwork_preview_worker (Worker 1 gen 2: Spotify Core & Test Suite).
Working directory: c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_1_gen2

File Ownership (you exclusively own and write these files):
- `spotify/src/main/kotlin/com/metrolist/spotify/CookieSanitizer.kt`
- `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`
- `spotify/src/test/kotlin/com/metrolist/spotify/SpotifyAuthTest.kt`

Reference Files (Read-only):
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\ORIGINAL_REQUEST.md`
- `c:\Users\abhis\Downloads\MUSIC APP\AGENTS.md`
- `c:\Users\abhis\Downloads\MUSIC APP\PROJECT.md`
- `c:\Users\abhis\Downloads\MUSIC APP\.agents\explorer_survey_3\handoff.md`

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Core Tasks:
1. Read the reference files and guidelines. Note: @Suppress and @SuppressLint are NEVER allowed per AGENTS.md.
2. Implement `com.metrolist.spotify.CookieSanitizer`:
   - `sanitizeSpDc(rawInput: String): String?`
   - `sanitizeSpKey(rawInput: String): String?`
   - `extractCookies(rawInput: String): Map<String, String>`
   - Parse raw tokens (e.g., `AQB...`), key-value pairs (`sp_dc=AQB...`), Cookie headers (`Cookie: sp_dc=AQB...; sp_key=xyz`), Netscape cookies, JSON exports (`[{"name":"sp_dc","value":"..."}]`), URL-encoded values, wrapping quotes, etc.
3. Update `com.metrolist.spotify.SpotifyAuth`:
   - Make `generateTotp` and `base32Decode` internal (or provide internal helper `SpotifyTotp`) for unit testing.
   - Use `CookieSanitizer` in `fetchAccessToken` to ensure robust handling of raw vs formatted `sp_dc`/`sp_key`.
4. Update and expand `spotify/src/test/kotlin/com/metrolist/spotify/SpotifyAuthTest.kt`:
   - Add RFC 6238 TOTP verification test vectors (known secrets, timestamps, 6-digit outputs).
   - Add Base32 decoding tests across varying padding and casing.
   - Add exhaustive unit tests for `CookieSanitizer` across all input formats and edge cases.
   - Add JSON model deserialization tests for `SpotifyInternalToken`, `ServerTimeResponse`, `Nuance`, and `GistFiles`.
   - Maintain `testSpotifyAuthPipeline()`.
5. Run `./gradlew :spotify:test --warning-mode all` and verify all tests pass with 100% success.
6. Write a complete handoff report to `c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_1_gen2\handoff.md` and message the orchestrator.
