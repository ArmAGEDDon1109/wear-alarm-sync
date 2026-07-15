# Собирает debug APK в контейнере — не требует установленного Android SDK на хосте
# (полезно для CI/раннеров без предустановленного SDK, см. improvements.md #12).
#
# Сборка образа:
#   docker build -t wear-alarm-sync-build .
#
# Сборка APK (результат появится в ./build/apk/debug на хосте):
#   docker run --rm -v "${PWD}/build:/workspace/build" wear-alarm-sync-build
#
# Для release-сборки нужен keystore.properties + keystore-файл, смонтированные тем же способом,
# и другая команда, например:
#   docker run --rm -v "${PWD}:/workspace" wear-alarm-sync-build ./gradlew syncReleaseApks --no-daemon
FROM eclipse-temurin:17-jdk

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT}
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Номер сборки cmdline-tools с https://developer.android.com/studio#command-line-tools-only
# (актуален на момент написания; при желании обновить — проверить текущий номер на странице выше).
ARG CMDLINE_TOOLS_BUILD=11076708
# compileSdk/targetSdk и build-tools совпадают с app/build.gradle.kts и core/build.gradle.kts.
ARG ANDROID_PLATFORM=35
ARG ANDROID_BUILD_TOOLS=35.0.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
       "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
       "platform-tools" \
       "platforms;android-${ANDROID_PLATFORM}" \
       "build-tools;${ANDROID_BUILD_TOOLS}"

WORKDIR /workspace
COPY . .
# На Windows gradlew может быть чекаутнут с CRLF (git core.autocrlf) — тогда шебанг
# "#!/bin/sh\r" не распознаётся в Linux-контейнере ("bad interpreter"). Нормализуем.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# По умолчанию — debug APK (без ключа подписи), см. описание release-варианта выше.
CMD ["./gradlew", "collectApks", "--no-daemon"]
