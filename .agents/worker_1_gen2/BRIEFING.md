# BRIEFING — 2026-08-28T17:15:30Z

## Mission
Implement `CookieSanitizer`, refine `SpotifyAuth`, and create an exhaustive test suite in `SpotifyAuthTest` for the `:spotify` module, verifying all tests pass with 100% success.

## 🔒 My Identity
- Archetype: implementer
- Roles: implementer, qa, specialist
- Working directory: c:\Users\abhis\Downloads\MUSIC APP\.agents\worker_1_gen2
- Original parent: 8c9b3f2c-7d16-4b7f-8e9d-a7e90d4b3328
- Milestone: worker_1_gen2

## 🔒 Key Constraints
- @Suppress / @SuppressLint are NEVER allowed per AGENTS.md.
- DO NOT CHEAT: Genuine implementations only, no hardcoded verification strings/facades.
- JDK 21 LTS required.
- All Gradle commands must include `--warning-mode all`.

## Current Parent
- Conversation ID: 8c9b3f2c-7d16-4b7f-8e9d-a7e90d4b3328
- Updated: not yet

## Task Summary
- **What to build**: 
  1. `CookieSanitizer.kt` in `com.metrolist.spotify`: robust parsing of `sp_dc`, `sp_key`, raw tokens, headers, JSON exports, Netscape format, etc.
  2. Update `SpotifyAuth.kt`: expose TOTP / Base32 utilities for testing (e.g. `SpotifyTotp` or internal functions) and integrate `CookieSanitizer` in `fetchAccessToken`.
  3. Expand `SpotifyAuthTest.kt`: RFC 6238 TOTP test vectors, Base32 decoding tests, CookieSanitizer unit tests, JSON model deserialization tests, and end-to-end auth pipeline tests.
- **Success criteria**: `./gradlew :spotify:test --warning-mode all` runs clean with 100% tests passing.
- **Interface contracts**: `PROJECT.md`, `spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt`
- **Code layout**: `spotify/src/main/kotlin/com/metrolist/spotify/`, `spotify/src/test/kotlin/com/metrolist/spotify/`

## Change Tracker
- **Files modified**: None yet
- **Build status**: Pending
- **Pending issues**: None

## Quality Status
- **Build/test result**: Pending
- **Lint status**: None
- **Tests added/modified**: Pending

## Loaded Skills
- None loaded yet

## Key Decisions Made
- Initializing workspace and reviewing existing code.

## Artifact Index
- `.agents/worker_1_gen2/DISPATCH.md` — Assignment instructions
- `.agents/worker_1_gen2/BRIEFING.md` — Agent state and briefing
- `.agents/worker_1_gen2/progress.md` — Liveness and progress heartbeat
