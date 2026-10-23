# PowerShell script to install and launch Tourist Safety & Assistance App on connected device
param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$ApkPath = "$PSScriptRoot\android\app\build\outputs\apk\debug\app-debug.apk"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   TOURIST SAFETY & ASSISTANCE - RUN ON ANDROID DEVICE    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (-not (Test-Path $AdbPath)) {
    Write-Error "adb.exe not found at $AdbPath. Please verify Android SDK path."
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK not found at $ApkPath. Please run Gradle assembleDebug first."
    exit 1
}

Write-Host "`nChecking for connected Android devices..." -ForegroundColor Yellow
$devices = & $AdbPath devices
Write-Host $devices

$deviceCount = ($devices -split "`n" | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }).Count

if ($deviceCount -eq 0) {
    Write-Host "`nWaiting for device to be connected and authorized..." -ForegroundColor Yellow
    Write-Host "Please ensure:" -ForegroundColor Gray
    Write-Host "  1. USB Debugging is ENABLED in Developer Options." -ForegroundColor Gray
    Write-Host "  2. USB mode is set to 'File Transfer' / 'MTP' (not charge-only or tethering)." -ForegroundColor Gray
    Write-Host "  3. On Vivo / iQOO devices: 'Install via USB' is toggled ON." -ForegroundColor Gray
    Write-Host "  4. You accepted the 'Allow USB debugging?' RSA prompt on the phone screen." -ForegroundColor Gray
    Write-Host "`nWaiting (press Ctrl+C to cancel)..." -ForegroundColor Yellow
    & $AdbPath wait-for-device
}

Write-Host "`n[1/4] Device connected! Setting up reverse port forwarding (tcp:8000)..." -ForegroundColor Green
& $AdbPath reverse tcp:8000 tcp:8000
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Could not establish reverse port forward. The mobile will connect to backend via local network IP."
} else {
    Write-Host "   -> localhost:8000 on phone forwarded to localhost:8000 on laptop." -ForegroundColor Green
}

Write-Host "`n[2/4] Installing Tourist Safety APK ($ApkPath)..." -ForegroundColor Green
& $AdbPath install -r -t "$ApkPath"

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nStandard install failed. Trying install with Vivo workaround flags..." -ForegroundColor Yellow
    & $AdbPath install -r -d -g "$ApkPath"
}

Write-Host "`n[3/4] Launching MainActivity..." -ForegroundColor Green
& $AdbPath shell am start -n com.touristsafety.app/.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

Write-Host "`n[4/4] Verifying app process on device..." -ForegroundColor Green
Start-Sleep -Seconds 2
$pidOutput = & $AdbPath shell pidof com.touristsafety.app
if ($pidOutput) {
    Write-Host "SUCCESS! App is running with PID: $pidOutput" -ForegroundColor Green
} else {
    Write-Host "App launched. Check phone screen." -ForegroundColor Cyan
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "   APP IS READY ON YOUR MOBILE DEVICE!                   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

