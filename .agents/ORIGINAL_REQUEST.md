# Original User Request

## Initial Request — 2026-08-27T18:22:06Z

# Comprehensive Spotify Login Architecture & Verification

Implement, optimize, and verify a robust Spotify authentication system for the Atify Android music player supporting desktop monitor viewport emulation, Chrome Custom Tabs fallback, and automated token verification.

Working directory: c:\Users\abhis\Downloads\MUSIC APP
Integrity mode: development

## Requirements

### R1. Desktop Monitor Emulation & Clean WebView Rendering
Configure the Android WebView login interface to emulate a 1280px wide desktop monitor viewport with desktop Chrome headers, ensuring Spotify's responsive web portal renders completely without blank screens or bot-detection blocking.

### R2. Chrome Custom Tabs & Deep-Link Authentication
Integrate Chrome Custom Tabs (CCT) as a native browser fallback option, allowing users to authenticate via their installed Chrome browser and automatically capture the session callback.

### R3. Instant Cookie Auto-Capture & Clipboard Assist
Provide a dedicated manual cookie entry interface with 1-tap clipboard paste, input validation, and automatic session capture across all Spotify domains (accounts.spotify.com, open.spotify.com, .spotify.com).

### R4. Automated Test Verification
Ensure all automated unit and integration tests in the :spotify module pass cleanly, confirming TOTP generation, server-time synchronization, and access token exchange.

## Verification Resources
- Unit test suite located at spotify/src/test/kotlin/com/metrolist/spotify/SpotifyAuthTest.kt.
- Spotify authentication engine located at spotify/src/main/kotlin/com/metrolist/spotify/SpotifyAuth.kt.
- Android app target at app/src/main/java/io/github/sekademi/spotufi/ui/screens/SpotifyLoginScreen.kt.

## Acceptance Criteria

### Rendering & User Interface
- [ ] Spotify login page renders reliably without pitch black or blank void screens across Android 13, 14, 15, and 16.
- [ ] Viewport simulation renders the full Spotify desktop login layout on mobile screens without clipping.
- [ ] Direct cookie paste authenticates the user instantly when a valid sp_dc string is provided.

### Build & Test Integrity
- [ ] ./gradlew :spotify:test passes 100% of test cases.
- [ ] ./gradlew :app:assembleRelease compiles successfully with R8 shrinking enabled.
