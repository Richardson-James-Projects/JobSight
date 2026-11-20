# Cleanly stops the running Android Emulator.
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$androidCli = "$env:USERPROFILE\AppData\AndroidCLI\android.exe"

Write-Host "Stopping Android Emulator..." -ForegroundColor Yellow
if (Test-Path $androidCli) {
    & $androidCli emulator stop medium_phone 2>$null
}
if (Test-Path $adb) {
    & $adb emu kill 2>$null
}
Write-Host "Emulator stopped." -ForegroundColor Green
