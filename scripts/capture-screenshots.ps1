# Capture phone + Wear OS screenshots for README / GitHub Release.
# Requires: Android SDK (emulator, adb), AVDs Phone_API_35_GA and Wear_OS_6_Round.

param(
    [string]$PhoneAvd = "Phone_API_35_GA",
    [string]$WearAvd = "Wear_OS_6_Round",
    [string]$PhoneSerial = "emulator-5554",
    [string]$WearSerial = "emulator-5556",
    [switch]$SkipEmulatorStart
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root "docs\screenshots"
$sdk = $env:LOCALAPPDATA + "\Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emu = Join-Path $sdk "emulator\emulator.exe"
$apk = Join-Path $root "build\apk\debug\wear-alarm-sync-universal-debug.apk"

if (-not (Test-Path $adb)) { throw "adb not found: $adb" }
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Capture-Screen([string]$serial, [string]$path) {
    Start-Process -FilePath $adb -ArgumentList @("-s", $serial, "exec-out", "screencap", "-p") `
        -RedirectStandardOutput $path -NoNewWindow -Wait | Out-Null
}

function Wait-Boot([string]$serial, [int]$timeoutSec = 180) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        $line = & $adb devices | Select-String "$serial\s+device"
        if ($line) {
            $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
            if ($boot -eq "1") { return }
        }
        Start-Sleep -Seconds 4
    }
    throw "Timeout waiting for $serial"
}

if (-not $SkipEmulatorStart) {
    if (-not (Test-Path $emu)) { throw "emulator not found: $emu" }
    $running = & $adb devices | Select-String "emulator-\d+\s+device"
    if (-not $running) {
        Write-Host "Starting emulators..."
        Start-Process -FilePath $emu -ArgumentList "-avd", $PhoneAvd, "-no-audio", "-gpu", "swiftshader_indirect", "-no-boot-anim" -WindowStyle Minimized
        Start-Process -FilePath $emu -ArgumentList "-avd", $WearAvd, "-no-audio", "-gpu", "swiftshader_indirect", "-no-boot-anim", "-port", "5556" -WindowStyle Minimized
    }
    Wait-Boot $PhoneSerial
    Wait-Boot $WearSerial
}

if (-not (Test-Path $apk)) {
    Push-Location $root
    & .\gradlew.bat assembleDebug
    Pop-Location
}

& $adb -s $PhoneSerial install -r $apk | Out-Null
& $adb -s $WearSerial install -r $apk | Out-Null
& $adb -s $WearSerial shell pm grant com.wearalarmsync android.permission.POST_NOTIFICATIONS 2>$null
& $adb -s $WearSerial shell appops set com.wearalarmsync SCHEDULE_EXACT_ALARM allow 2>$null

# Phone main screen (wait past splash)
& $adb -s $PhoneSerial shell am force-stop com.wearalarmsync
& $adb -s $PhoneSerial shell monkey -p com.wearalarmsync -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 10
Capture-Screen $PhoneSerial (Join-Path $out "phone-main.png")

# Phone sources dialog (tap button area on 1080x2400)
& $adb -s $PhoneSerial shell input tap 540 980
Start-Sleep -Seconds 2
Capture-Screen $PhoneSerial (Join-Path $out "phone-sources.png")
& $adb -s $PhoneSerial shell input keyevent 4

# Wear main
& $adb -s $WearSerial shell am force-stop com.wearalarmsync
& $adb -s $WearSerial shell monkey -p com.wearalarmsync -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 5
Capture-Screen $WearSerial (Join-Path $out "wear-main.png")

# Wear alarm (debug manifest exports AlarmActivity)
$trigger = [int64]((Get-Date).Date.AddDays(1).AddHours(7).AddMinutes(30).ToUniversalTime() - [datetime]"1970-01-01").TotalMilliseconds
& $adb -s $WearSerial shell am start -n com.wearalarmsync/.wear.AlarmActivity --el trigger_ms $trigger | Out-Null
Start-Sleep -Seconds 2
Capture-Screen $WearSerial (Join-Path $out "wear-alarm.png")

Write-Host "Saved to $out"
Get-ChildItem $out -Filter *.png | Format-Table Name, Length
