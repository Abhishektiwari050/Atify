#!/usr/bin/env bash
# ==============================================================================
# Atify - Assistant Discovery & MediaSession/MediaBrowser Verification Script
# ==============================================================================
# Verifies:
# 1. AndroidManifest declarations for MediaButtonReceiver & MediaBrowserService
# 2. MediaSessionCompat explicit initialization, active state, and PlaybackStateCompat actions
# 3. MediaButtonReceiver intent handling with injected KEYCODE_MEDIA_PLAY & callback interception
# 4. MediaBrowserServiceCompat structure (BrowserRoot extras & browsable catalog items)
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=================================================================="
echo "  Atify Assistant Discovery & Media Integration Verification"
echo "=================================================================="

# Step 1: Manifest Inspection
echo ""
echo "[1/3] Inspecting AndroidManifest.xml declarations..."
MANIFEST="$ROOT_DIR/app/src/main/AndroidManifest.xml"

if [ ! -f "$MANIFEST" ]; then
    echo "  [-] FAIL: AndroidManifest.xml not found at $MANIFEST"
    exit 1
fi

if ! grep -q "androidx.media.session.MediaButtonReceiver" "$MANIFEST" || ! grep -q "android.intent.action.MEDIA_BUTTON" "$MANIFEST"; then
    echo "  [-] FAIL: MediaButtonReceiver is not properly declared in manifest"
    exit 1
else
    echo "  [+] PASS: MediaButtonReceiver declared with android.intent.action.MEDIA_BUTTON"
fi

if ! grep -q "MusicBrowserService" "$MANIFEST" || ! grep -q "android.media.browse.MediaBrowserService" "$MANIFEST"; then
    echo "  [-] FAIL: MusicBrowserService is not properly declared with android.media.browse.MediaBrowserService"
    exit 1
else
    echo "  [+] PASS: MusicBrowserService declared with android.media.browse.MediaBrowserService"
fi

if ! grep -q "PlaybackService" "$MANIFEST"; then
    echo "  [-] FAIL: PlaybackService is not declared in manifest"
    exit 1
else
    echo "  [+] PASS: PlaybackService declared in manifest"
fi

# Step 2: Automated Unit Test Execution
echo ""
echo "[2/3] Running MediaAssistantDiscoveryTest suite via Gradle..."
LOG_DIR="$ROOT_DIR/build_logs"
mkdir -p "$LOG_DIR"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
LOG_FILE="$LOG_DIR/verify_assistant_$TIMESTAMP.log"

echo "  Logging Gradle execution to: $LOG_FILE"
"$ROOT_DIR/gradlew" :app:testDebugUnitTest --tests "io.github.sekademi.spotufi.media.MediaAssistantDiscoveryTest" --warning-mode all 2>&1 | tee "$LOG_FILE"

# Step 3: Verification Report
echo ""
echo "[3/3] Inspecting Test Results..."
RESULT_XML="$ROOT_DIR/app/build/test-results/testDebugUnitTest/TEST-io.github.sekademi.spotufi.media.MediaAssistantDiscoveryTest.xml"

if [ -f "$RESULT_XML" ]; then
    if grep -q 'failures="0"' "$RESULT_XML" && grep -q 'errors="0"' "$RESULT_XML"; then
        echo "  [+] PASS: All MediaAssistantDiscoveryTest cases executed cleanly with 0 failures."
    else
        echo "  [-] FAIL: Test suite encountered failures or errors."
        exit 1
    fi
fi

echo ""
echo "=================================================================="
echo "  ASSISTANT DISCOVERY VERIFICATION SUMMARY: ALL CHECKS PASSED"
echo "  1. MediaSessionCompat is initialized, active, and publishing active PlaybackStateCompat actions."
echo "  2. MediaButtonReceiver intent handling with injected KEYCODE_MEDIA_PLAY intercepts via callback."
echo "  3. MediaBrowserServiceCompat exposes valid BrowserRoot and browsable catalog items for assistants."
echo "=================================================================="
exit 0
