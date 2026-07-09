# Скриншоты Wear Alarm Sync

PNG для README и GitHub Release. Снимаются с эмуляторов через ADB.

## Быстрый способ (Windows)

```powershell
.\scripts\capture-screenshots.ps1
```

Нужны AVD:

- `Phone_API_35_GA` (или любой телефон API 26+)
- `Wear_OS_6_Round` (или любой Wear OS)

Скрипт сам поднимет эмуляторы, установит debug APK и положит файлы в `docs/screenshots/`.

## Ручной способ

### 1. Эмуляторы

```powershell
$emu = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
& $emu -avd Phone_API_35_GA -no-audio -gpu swiftshader_indirect
& $emu -avd Wear_OS_6_Round -no-audio -gpu swiftshader_indirect -port 5556
```

### 2. Установка

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\gradlew.bat assembleDebug
& $adb -s emulator-5554 install -r build\apk\debug\wear-alarm-sync-universal-debug.apk
& $adb -s emulator-5556 install -r build\apk\debug\wear-alarm-sync-universal-debug.apk
```

### 3. Снимок экрана

```powershell
& $adb -s emulator-5554 shell monkey -p com.wearalarmsync -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
Start-Process $adb -ArgumentList '-s','emulator-5554','exec-out','screencap','-p' `
  -RedirectStandardOutput docs\screenshots\phone-main.png -NoNewWindow -Wait
```

На часах — то же с `emulator-5556`. Экран будильника (только **debug** APK):

```powershell
$trigger = [int64]((Get-Date).Date.AddDays(1).AddHours(7).AddMinutes(30).ToUniversalTime() - [datetime]'1970-01-01').TotalMilliseconds
& $adb -s emulator-5556 shell am start -n com.wearalarmsync/.wear.AlarmActivity --el trigger_ms $trigger
```

### 4. С реального телефона и часов

```powershell
& $adb -s <phone-id> exec-out screencap -p > docs\screenshots\phone-main.png
& $adb -s <watch-id> exec-out screencap -p > docs\screenshots\wear-main.png
```

В Android Studio: **View → Tool Windows → Running Devices** → иконка камеры.

## Куда добавить

### GitHub Release

1. Откройте https://github.com/ArmAGEDDon1109/wear-alarm-sync/releases
2. **Edit** релиза `v2.2.0`
3. Перетащите PNG в описание или вставьте:

```markdown
## Скриншоты

| Телефон | Часы |
|---------|------|
| ![phone](https://github.com/ArmAGEDDon1109/wear-alarm-sync/raw/main/docs/screenshots/phone-main.png) | ![wear](https://github.com/ArmAGEDDon1109/wear-alarm-sync/raw/main/docs/screenshots/wear-main.png) |
```

(Сначала закоммитьте `docs/screenshots/` в репозиторий.)

### README

В `README.ru.md` / `README.md` после блока «Возможности»:

```markdown
## Скриншоты

<p float="left">
  <img src="docs/screenshots/phone-main.png" width="280" alt="Телефон" />
  <img src="docs/screenshots/wear-main.png" width="200" alt="Часы" />
</p>
```

## Рекомендуемый набор

| Файл | Экран |
|------|--------|
| `phone-main.png` | Главный экран телефона |
| `phone-sources.png` | Диалог «Источники будильников» |
| `wear-main.png` | Главный экран часов |
| `wear-alarm.png` | Срабатывание будильника (зелёная/красная кнопки) |
