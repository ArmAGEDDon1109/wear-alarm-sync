plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

/** Один универсальный APK (телефон + часы) → `build/apk/…`. */
tasks.register<Sync>("syncDebugApks") {
    group = "distribution"
    description = "Собирает debug и кладёт универсальный APK в build/apk/debug/"
    dependsOn(":app:assembleDebug")
    from("$rootDir/app/build/outputs/apk/debug") {
        include("*.apk")
        rename { "wear-alarm-sync-universal-debug.apk" }
    }
    into(layout.projectDirectory.dir("build/apk/debug"))
}

tasks.register<Sync>("syncReleaseApks") {
    group = "distribution"
    description = "Собирает release и кладёт APK в build/apk/release/ (нужен keystore.properties для подписи release)"
    dependsOn(":app:assembleRelease")
    from("$rootDir/app/build/outputs/apk/release") {
        include("*.apk")
        rename { "wear-alarm-sync-universal-release.apk" }
    }
    into(layout.projectDirectory.dir("build/apk/release"))
}

tasks.register("syncAllApks") {
    group = "distribution"
    description = "Debug + release APK в build/apk/debug и build/apk/release"
    dependsOn("syncDebugApks", "syncReleaseApks")
}

tasks.register("collectApks") {
    group = "distribution"
    description = "Синоним: только debug в build/apk/debug (частый сценарий)"
    dependsOn("syncDebugApks")
}

/** Явный вызов: то же, что syncDebugApks (копия debug APK в корень `build/`). */
tasks.register("copyBuiltApkToRoot") {
    group = "distribution"
    description = "Копирует собранный debug APK в build/apk/debug/ (вызывает syncDebugApks)"
    dependsOn("syncDebugApks")
}

/**
 * После успешной `:app:assembleDebug` / `assembleRelease` автоматически копируем APK
 * в корневую папку `build/apk/…` (удобно забирать без отдельной команды).
 */
gradle.projectsEvaluated {
    val syncDebug = rootProject.tasks.named("syncDebugApks")
    val syncRelease = rootProject.tasks.named("syncReleaseApks")
    project(":app").tasks.named("assembleDebug").configure { finalizedBy(syncDebug) }
    project(":app").tasks.named("assembleRelease").configure { finalizedBy(syncRelease) }
}
