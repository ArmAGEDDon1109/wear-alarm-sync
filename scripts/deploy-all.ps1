# Сборка debug APK, установка на все подключённые adb-устройства,
# постановка системного будильника на телефоне (~+3 мин) и открытие приложения для синка.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root "adb\adb.exe"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

Set-Location $root
& (Join-Path $root "gradlew.bat") ":app:assembleDebug" --no-daemon -q

$devices = (& $adb devices) | Where-Object { $_ -match "`tdevice$" } | ForEach-Object { ($_ -split "`t")[0].Trim() }
foreach ($s in $devices) {
    Write-Host "install -> $s"
    & $adb -s $s install -r $apk
}

function Test-AdbWatch([string]$serial) {
    $c = (& $adb -s $serial shell getprop ro.build.characteristics 2>&1).ToString().Trim()
    return $c -match "watch"
}
$phone = $devices | Where-Object { -not (Test-AdbWatch $_) } | Select-Object -First 1
if (-not $phone) { $phone = $devices | Select-Object -First 1 }
$watch = $devices | Where-Object { Test-AdbWatch $_ } | Select-Object -First 1

if ($phone) {
    $H = [int](& $adb -s $phone shell date +%H).Trim()
    $M = [int](& $adb -s $phone shell date +%M).Trim()
    $totalMin = $H * 60 + $M + 3
    $nh = [int]([Math]::Floor($totalMin / 60)) % 24
    $nm = $totalMin % 60
    Write-Host "SET_ALARM on $phone at ${nh}:$nm (local)"
    & $adb -s $phone shell am start -a android.intent.action.SET_ALARM `
        --ei android.intent.extra.alarm.HOUR $nh `
        --ei android.intent.extra.alarm.MINUTES $nm `
        --ez android.intent.extra.alarm.SKIP_UI true | Out-Host
    Start-Sleep -Seconds 1
    & $adb -s $phone shell am start -n com.wearalarmsync/.LauncherActivity | Out-Host
}

if ($watch) {
    Start-Sleep -Seconds 2
    & $adb -s $watch shell monkey -p com.wearalarmsync -c android.intent.category.LAUNCHER 1 | Out-Null
}

Write-Host "Done. devices:" ($devices -join ", ")
