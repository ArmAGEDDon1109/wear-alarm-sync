# Wear Alarm Sync

**[English version / Английская версия](README.md)**

Синхронизация следующего системного будильника с телефона на часы Wear OS и срабатывание на часах. Отклонение и отложение с часов передаются в приложение «Часы» на телефоне.

**Текущая версия:** 2.2.0 (versionCode 29)

## Возможности

- Читает **следующий системный будильник** на телефоне (`AlarmManager.getNextAlarmClock()`).
- Отправляет время на часы через **Google Wearable Data Layer** (нужна уже настроенная связь телефон ↔ часы через OHealth / Wear OS).
- На часах планирует локальный будильник и показывает полноэкранный интерфейс с вибрацией.
- **Отклонить** и **Отложить** на часах пересылаются на телефон.
- Опционально: **не будить, если часы сняты** (нужно разрешение «Датчики тела»).
- Настраиваемый **паттерн вибрации** на часах (синхронизируется с телефона).

## Архитектура

Один универсальный APK для телефона и часов (`com.wearalarmsync`):

| Модуль | Назначение |
|--------|------------|
| **core** | Общие константы (`WearSync`): пути Data Layer, ключи, команды DISMISS/SNOOZE |
| **app** | Логика телефона и часов в одном модуле |

**Точка входа:** `LauncherActivity` определяет тип устройства (`FEATURE_WATCH`) и открывает `PhoneMainActivity` или `WearMainActivity`.

**Телефон:** читает следующий будильник, пишет в Data Layer, принимает команды dismiss/snooze с часов.

**Часы:** слушает Data Layer, планирует будильники (`AlarmScheduler`), показывает `AlarmActivity`, удерживает процесс через `AlarmKeepAliveService` (нужно на некоторых часах, например OPLUS).

## Требования

- Android 8.0+ (API 26+), target SDK 35
- Телефон и часы связаны через **OHealth / Wear OS**
- **Один и тот же подписанный APK** на обоих устройствах
- Google Play services на телефоне и часах
- Разрешение **точных будильников** на часах (Android 12+)
- Разрешения **уведомлений** и **полноэкранного intent** на часах

## Установка

1. Соберите или скачайте APK (см. [Сборка](#сборка)).
2. Установите **один и тот же APK** на телефон и часы (sideload через `adb install` или файловый менеджер).
3. Откройте приложение на телефоне и нажмите **Синхронизировать**.
4. На часах выдайте разрешения на точные будильники, уведомления и (при необходимости) датчики тела.

## Сборка

Положите `icon.jpg` в корень проекта (из него генерируется иконка перед каждой сборкой).

### Debug APK

```bash
# Windows
gradlew.bat collectApks

# Linux / macOS
./gradlew collectApks
```

Результат: `build/apk/debug/wear-alarm-sync-universal-debug.apk`

Любая сборка `:app:assembleDebug` также автоматически копирует APK в `build/apk/debug/`.

### Release APK

1. Скопируйте `keystore.properties.example` → `keystore.properties` и заполните данные подписи.
2. Выполните:

```bash
gradlew.bat syncReleaseApks   # Windows
./gradlew syncReleaseApks     # Linux / macOS
```

Результат: `build/apk/release/wear-alarm-sync-universal-release.apk`

## Сборка релиза на GitHub

CI собирает подписанный release APK и публикует [GitHub Release](https://docs.github.com/en/repositories/releasing-projects-on-github) при push тега версии.

### 1. Создать репозиторий на GitHub

```bash
git init
git add .
git commit -m "Initial commit"
gh repo create wear-alarm-sync --public --source=. --push
```

Или создайте пустой репозиторий на GitHub и выполните push вручную:

```bash
git remote add origin https://github.com/YOUR_USER/wear-alarm-sync.git
git branch -M main
git push -u origin main
```

### 2. Добавить секреты подписи

В репозитории: **Settings → Secrets and variables → Actions → New repository secret**.

| Секрет | Значение |
|--------|----------|
| `KEYSTORE_BASE64` | Файл `.jks` / `.keystore` в Base64 |
| `KEYSTORE_PASSWORD` | Пароль keystore |
| `KEY_ALIAS` | Алиас ключа |
| `KEY_PASSWORD` | Пароль ключа |

Кодирование keystore (Linux / macOS / Git Bash):

```bash
base64 -w0 release.keystore
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))
```

Создать keystore, если его ещё нет:

```bash
keytool -genkeypair -v -keystore release.keystore -alias wearalarmsync -keyalg RSA -keysize 2048 -validity 10000
```

### 3. Опубликовать релиз

Поднимите `versionCode` и `versionName` в `app/build.gradle.kts`, закоммитьте, затем создайте и отправьте тег:

```bash
git tag v2.1.14
git push origin v2.1.14
```

Workflow [`.github/workflows/release.yml`](.github/workflows/release.yml):

1. Соберёт `wear-alarm-sync-universal-release.apk`
2. Прикрепит его к GitHub Release для этого тега

**Ручная сборка** (без релиза): **Actions → Release → Run workflow** — APK сохранится как артефакт workflow.

### Другие задачи Gradle

| Задача | Описание |
|--------|----------|
| `collectApks` | Собрать debug APK → `build/apk/debug/` |
| `syncDebugApks` | То же самое |
| `syncReleaseApks` | Собрать подписанный release APK → `build/apk/release/` |
| `syncAllApks` | Debug + release |

## Использование

### Телефон

- Показывает статус связи с часами и время следующего системного будильника.
- Нажмите **Синхронизировать** после смены будильника в «Часах» или если на часах ещё нет данных.
- Меню (⋯) → **Вибрация будильника** — сила, длительность импульса и пауза; настройки синхронизируются на часы.

### Часы

- Показывает статус связи и время следующего синхронизированного будильника.
- Иконка настроек → вибрация и **Не будить, если часы сняты** (по умолчанию выключено).
- При срабатывании: полноэкранный интерфейс с кнопками Отклонить / Отложить.

## Ограничения

- Доступен только **следующий** системный будильник (ограничение Android API).
- Поведение dismiss/snooze зависит от приложения «Часы» на телефоне.
- Режим «не будить, если сняты» требует совместимый датчик; при отсутствии данных будильник **сработает** (безопасный сбой).
- Универсальный APK включает библиотеки Wear Compose на телефоне — размер APK на телефоне больше, чем у «только телефон» сборки.

## Структура проекта

```
project4/
├── app/                  # Универсальный APK (телефон + часы)
├── core/                 # Общие константы WearSync
├── build/apk/            # Собранные APK (debug / release)
├── scripts/              # deploy-all.ps1 и вспомогательные скрипты
├── icon.jpg              # Исходник иконки (нужен для сборки)
└── keystore.properties   # Подпись release (в .gitignore)
```

## Разработка

- **Скрипт деплоя (Windows):** `scripts/deploy-all.ps1` — собирает debug APK, ставит на все подключённые `adb`-устройства, ставит тестовый будильник на телефоне и запускает приложение.
- **Версия:** перед каждым релизом обновляйте `versionCode` (+1) и `versionName` в `app/build.gradle.kts`.

## Лицензия

Проект распространяется по лицензии **Personal Use Only** — см. [LICENSE](LICENSE).

Разрешено использовать, изменять и запускать программу **только в личных, некоммерческих целях**. **Коммерческое использование и продажа запрещены.** Копии можно передавать бесплатно только для частного использования при условии сохранения текста лицензии.
