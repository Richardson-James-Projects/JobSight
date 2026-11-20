# Builds the clean JobSight APK (no demo buttons), copies it to .\JobSight.apk,
# and installs it directly onto a connected physical Android phone via USB.
$ErrorActionPreference = "Stop"

$sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdkDir\platform-tools\adb.exe"
$javaHome = "C:\Users\20jam\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"

if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
}

Write-Host "1. Building clean JobSight APK..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug

$apkSource = "app\build\outputs\apk\debug\app-debug.apk"
$apkDest = "JobSight.apk"
Copy-Item -Path $apkSource -Destination $apkDest -Force
Write-Host "   Saved installable APK to: $PWD\$apkDest" -ForegroundColor Green

Write-Host "2. Checking for connected Android phone via USB..." -ForegroundColor Cyan
$lines = & $adb devices
$physicalDevices = $lines | Where-Object { $_ -match "\tdevice$" -and $_ -notmatch "^emulator-" }

if (-not $physicalDevices) {
    Write-Host ""
    Write-Host "No physical phone detected over USB yet." -ForegroundColor Yellow
    Write-Host "To install via USB:" -ForegroundColor White
    Write-Host "  1. On your phone, go to Settings -> About Phone -> tap 'Build Number' 7 times."
    Write-Host "  2. Go to Settings -> System -> Developer Options -> turn ON 'USB Debugging'."
    Write-Host "  3. Plug your phone into this computer via USB and tap 'Allow' on the prompt."
    Write-Host "  4. Re-run .\install-to-phone.ps1"
    Write-Host ""
    Write-Host "OR install without a cable:" -ForegroundColor White
    Write-Host "  Send '$PWD\JobSight.apk' to your phone (via Google Drive, Email, etc.) and tap it to install!"
    exit 0
}

$serial = ($physicalDevices | Select-Object -First 1).Split("`t")[0].Trim()
Write-Host "3. Found physical phone ($serial)! Installing JobSight..." -ForegroundColor Cyan
& $adb -s $serial install -r -g $apkDest

Write-Host "4. Launching JobSight on your phone..." -ForegroundColor Green
& $adb -s $serial shell am start -n com.jobsight.app/.MainActivity
