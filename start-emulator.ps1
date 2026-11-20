# Launches a visible Android Emulator window, builds & installs JobSight, and opens the app.
$ErrorActionPreference = "Stop"

$sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdkDir\platform-tools\adb.exe"
$emulator = "$sdkDir\emulator\emulator.exe"
$javaHome = "C:\Users\20jam\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"

if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
}

Write-Host "1. Building latest JobSight Debug APK..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug

# Check if a visible emulator window exists; if a headless/background one is stuck, stop it first.
$qemuProc = Get-Process -Name "qemu-system-x86_64" -ErrorAction SilentlyContinue
$hasVisibleWindow = $qemuProc | Where-Object { $_.MainWindowHandle -ne 0 }

if ($qemuProc -and -not $hasVisibleWindow) {
    Write-Host "   Stopping headless background emulator so a visible window can open..." -ForegroundColor Yellow
    & $adb emu kill 2>$null
    Start-Sleep -Seconds 3
}

$devices = & $adb devices | Select-String "emulator-"
if (-not $devices) {
    Write-Host "2. Opening 'medium_phone' Android Emulator window..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList "-avd", "medium_phone" -WindowStyle Normal
    & $adb wait-for-device
    Write-Host "   Waiting for Android OS to finish booting..." -ForegroundColor Yellow
    while ((& $adb shell getprop sys.boot_completed).Trim() -ne "1") {
        Start-Sleep -Seconds 1
    }
    Start-Sleep -Seconds 2
} else {
    Write-Host "2. Visible emulator window is already open." -ForegroundColor Green
}

Write-Host "3. Installing APK and granting Camera + Location permissions..." -ForegroundColor Cyan
& $adb install -r -g "app\build\outputs\apk\debug\app-debug.apk"

Write-Host "4. Setting default GPS coordinates (Apex Commercial Plaza)..." -ForegroundColor Cyan
& $adb emu geo fix -122.084 37.4219983

Write-Host "5. Launching JobSight..." -ForegroundColor Green
& $adb shell am force-stop com.jobsight.app
& $adb shell am start -n com.jobsight.app/.MainActivity
