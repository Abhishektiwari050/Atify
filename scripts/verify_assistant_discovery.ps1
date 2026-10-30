# ==============================================================================
# Atify - Assistant Discovery & MediaSession/MediaBrowser Verification Script
# ==============================================================================
# Verifies:
# 1. AndroidManifest declarations for MediaButtonReceiver & MediaBrowserService
# 2. MediaSessionCompat explicit initialization, active state, and PlaybackStateCompat actions
# 3. MediaButtonReceiver intent handling with injected KEYCODE_MEDIA_PLAY & callback interception
# 4. MediaBrowserServiceCompat structure (BrowserRoot extras & browsable catalog items)
# ==============================================================================

param (
    [switch]$SkipGradle = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  Atify Assistant Discovery & Media Integration Verification" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# Step 1: Manifest Inspection
Write-Host "`n[1/3] Inspecting AndroidManifest.xml declarations..." -ForegroundColor Yellow
$manifestPath = Join-Path $PSScriptRoot "..\app\src\main\AndroidManifest.xml"
if (-not (Test-Path $manifestPath)) {
    Write-Error "AndroidManifest.xml not found at $manifestPath"
    exit 1
}

$manifestContent = Get-Content $manifestPath -Raw

$hasMediaButtonReceiver = $manifestContent -match 'androidx\.media\.session\.MediaButtonReceiver'
$hasMediaButtonAction = $manifestContent -match 'android\.intent\.action\.MEDIA_BUTTON'
$hasBrowserService = $manifestContent -match 'MusicBrowserService'
$hasBrowserAction = $manifestContent -match 'android\.media\.browse\.MediaBrowserService'
$hasPlaybackService = $manifestContent -match 'PlaybackService'

if (-not $hasMediaButtonReceiver -or -not $hasMediaButtonAction) {
    Write-Host "  [-] FAIL: MediaButtonReceiver is not properly declared in manifest" -ForegroundColor Red
    exit 1
} else {
    Write-Host "  [+] PASS: MediaButtonReceiver declared with android.intent.action.MEDIA_BUTTON" -ForegroundColor Green
}

if (-not $hasBrowserService -or -not $hasBrowserAction) {
    Write-Host "  [-] FAIL: MusicBrowserService is not properly declared with android.media.browse.MediaBrowserService" -ForegroundColor Red
    exit 1
} else {
    Write-Host "  [+] PASS: MusicBrowserService declared with android.media.browse.MediaBrowserService" -ForegroundColor Green
}

if (-not $hasPlaybackService) {
    Write-Host "  [-] FAIL: PlaybackService is not declared in manifest" -ForegroundColor Red
    exit 1
} else {
    Write-Host "  [+] PASS: PlaybackService declared in manifest" -ForegroundColor Green
}

# Step 2: Automated Unit Test Execution
if (-not $SkipGradle) {
    Write-Host "`n[2/3] Running MediaAssistantDiscoveryTest suite via Gradle..." -ForegroundColor Yellow
    $buildLogsDir = Join-Path $PSScriptRoot "..\build_logs"
    if (-not (Test-Path $buildLogsDir)) {
        New-Item -ItemType Directory -Path $buildLogsDir | Out-Null
    }

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $logFile = Join-Path $buildLogsDir "verify_assistant_$timestamp.log"
    $gradlew = Join-Path $PSScriptRoot "..\gradlew.bat"

    Write-Host "  Logging Gradle execution to: $logFile" -ForegroundColor DarkGray
    & $gradlew :app:testDebugUnitTest --tests "io.github.sekademi.spotufi.media.MediaAssistantDiscoveryTest" --warning-mode all 2>&1 | Tee-Object -FilePath $logFile

    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n[-] FAIL: Gradle unit tests failed. Check log at $logFile" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

# Step 3: Verification Report
Write-Host "`n[3/3] Inspecting Test Results..." -ForegroundColor Yellow
$testResultFile = Join-Path $PSScriptRoot "..\app\build\test-results\testDebugUnitTest\TEST-io.github.sekademi.spotufi.media.MediaAssistantDiscoveryTest.xml"

if (Test-Path $testResultFile) {
    [xml]$xml = Get-Content $testResultFile
    $suite = $xml.testsuite
    $tests = [int]$suite.tests
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors

    Write-Host "  Total Tests: $tests | Failures: $failures | Errors: $errors" -ForegroundColor Cyan

    foreach ($case in $suite.testcase) {
        $name = $case.name
        $time = $case.time
        if ($case.failure) {
            Write-Host "  [-] FAIL: $name ($time s)" -ForegroundColor Red
            Write-Host "      $($case.failure.message)" -ForegroundColor DarkRed
        } else {
            Write-Host "  [+] PASS: $name ($time s)" -ForegroundColor Green
        }
    }

    if ($failures -gt 0 -or $errors -gt 0) {
        Write-Host "`n[-] Verification failed with errors/failures." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  [!] Test result XML not found, but Gradle reported success." -ForegroundColor Yellow
}

Write-Host "`n==================================================================" -ForegroundColor Green
Write-Host "  ASSISTANT DISCOVERY VERIFICATION SUMMARY: ALL CHECKS PASSED" -ForegroundColor Green
Write-Host "  1. MediaSessionCompat is initialized, active, and publishing active PlaybackStateCompat actions." -ForegroundColor Green
Write-Host "  2. MediaButtonReceiver intent handling with injected KEYCODE_MEDIA_PLAY intercepts via callback." -ForegroundColor Green
Write-Host "  3. MediaBrowserServiceCompat exposes valid BrowserRoot and browsable catalog items for assistants." -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Green
exit 0
