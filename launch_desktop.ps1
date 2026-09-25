# Atify Desktop Launcher for Windows PowerShell
$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "               Atify Desktop Launcher                   " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$adb = "C:\Users\abhis\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$emulator = "C:\Users\abhis\AppData\Local\Android\Sdk\emulator\emulator.exe"
$avd = "Fast_Test_Device"

if (-not (Test-Path $adb)) {
    Write-Host "[ERROR] adb.exe not found at $adb" -ForegroundColor Red
    exit 1
}

Write-Host "[1/4] Checking for running Android devices / emulators..." -ForegroundColor Yellow
$devices = & $adb devices | Select-String -Pattern "device$"
if (-not $devices) {
    Write-Host "[INFO] Starting Android Emulator ($avd)..." -ForegroundColor Green
    Start-Process $emulator -ArgumentList "-avd", $avd
    Write-Host "[INFO] Waiting for emulator to connect to adb..." -ForegroundColor Green
    & $adb wait-for-device
} else {
    Write-Host "[INFO] Active device/emulator found!" -ForegroundColor Green
}

Write-Host "[2/4] Waiting for Android system to complete boot..." -ForegroundColor Yellow
while ($true) {
    $boot = & $adb shell getprop sys.boot_completed
    if ($boot -and $boot.Trim() -eq "1") { break }
    Start-Sleep -Seconds 2
}
Write-Host "[INFO] Android system is fully booted and ready!" -ForegroundColor Green

Write-Host ""
Write-Host "[3/4] Checking APK..." -ForegroundColor Yellow
$candidates = @(
    "app\build\outputs\apk\release\app-universal-release.apk",
    "app\build\outputs\apk\release\app-x86_64-release.apk",
    "app\build\outputs\apk\debug\app-universal-debug.apk",
    "app\build\outputs\apk\debug\app-x86_64-debug.apk"
)

$targetApk = $null
foreach ($path in $candidates) {
    if (Test-Path $path) {
        $targetApk = $path
        break
    }
}

if ($targetApk) {
    Write-Host "[INFO] Installing $targetApk..." -ForegroundColor Green
    & $adb install -r $targetApk
} else {
    Write-Host "[WARN] No APK found. Compiling debug build..." -ForegroundColor Yellow
    .\gradlew.bat :app:assembleDebug --warning-mode all
    $found = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" -Recurse | Select-Object -First 1
    if ($found) {
        & $adb install -r $found.FullName
    }
}

Write-Host ""
Write-Host "[4/4] Launching Atify on Desktop..." -ForegroundColor Yellow
& $adb shell am start -n com.atify.music.debug/io.github.sekademi.spotufi.MainActivity
if ($LASTEXITCODE -ne 0) {
    & $adb shell am start -n com.atify.music/io.github.sekademi.spotufi.MainActivity
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   Atify is now running on your Desktop! Enjoy the music! " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
