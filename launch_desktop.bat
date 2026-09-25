@echo off
setlocal enabledelayedexpansion
title Atify Desktop Launcher

echo ========================================================
echo               Atify Desktop Launcher
echo ========================================================
echo.

set ADB="C:\Users\abhis\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set EMULATOR="C:\Users\abhis\AppData\Local\Android\Sdk\emulator\emulator.exe"
set AVD_NAME=Fast_Test_Device

if not exist %ADB% (
    echo [ERROR] adb.exe not found at %ADB%
    pause
    exit /b 1
)

echo [1/4] Checking for active Android devices or emulators...
%ADB% get-state >nul 2>&1
if errorlevel 1 (
    echo [INFO] No running device detected. Starting Android Emulator (%AVD_NAME%)...
    start "" %EMULATOR% -avd %AVD_NAME%
    echo [INFO] Waiting for emulator to connect to adb...
    %ADB% wait-for-device
) else (
    echo [INFO] Active device/emulator found!
)

echo [2/4] Waiting for Android system to finish booting...
:wait_boot
for /f "tokens=*" %%a in ('%ADB% shell getprop sys.boot_completed 2^>nul') do set BOOT=%%a
if not "!BOOT!"=="1" (
    timeout /t 2 /nobreak >nul
    goto wait_boot
)
echo [INFO] Android system is fully booted and responsive!

echo.
echo [3/4] Locating APK to install...
set APK_TARGET=""
if exist "app\build\outputs\apk\release\app-universal-release.apk" (
    set APK_TARGET="app\build\outputs\apk\release\app-universal-release.apk"
) else if exist "app\build\outputs\apk\release\app-x86_64-release.apk" (
    set APK_TARGET="app\build\outputs\apk\release\app-x86_64-release.apk"
) else if exist "app\build\outputs\apk\debug\app-universal-debug.apk" (
    set APK_TARGET="app\build\outputs\apk\debug\app-universal-debug.apk"
) else if exist "app\build\outputs\apk\debug\app-x86_64-debug.apk" (
    set APK_TARGET="app\build\outputs\apk\debug\app-x86_64-debug.apk"
)

if not !APK_TARGET!=="" (
    echo [INFO] Installing !APK_TARGET!...
    %ADB% install -r !APK_TARGET!
) else (
    echo [WARN] No prebuilt APK found. Building debug APK now...
    call .\gradlew.bat :app:assembleDebug --warning-mode all
    for /f "tokens=*" %%f in ('dir /b /s app\build\outputs\apk\debug\*.apk 2^>nul') do (
        set APK_TARGET="%%f"
    )
    if not !APK_TARGET!=="" (
        echo [INFO] Installing !APK_TARGET!...
        %ADB% install -r !APK_TARGET!
    )
)

echo.
echo [4/4] Launching Atify...
%ADB% shell am start -n com.atify.music.debug/io.github.sekademi.spotufi.MainActivity >nul 2>&1
if errorlevel 1 (
    %ADB% shell am start -n com.atify.music/io.github.sekademi.spotufi.MainActivity >nul 2>&1
)

echo.
echo ========================================================
echo   Atify is now running on your Desktop! Enjoy the music!
echo ========================================================
echo.
pause
