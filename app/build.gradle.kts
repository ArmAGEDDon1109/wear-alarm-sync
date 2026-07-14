import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.math.max

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreProperties = keystorePropertiesFile.exists()
if (hasKeystoreProperties) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.wearalarmsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wearalarmsync"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "2.2.2"
    }

    signingConfigs {
        if (hasKeystoreProperties) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Без keystore.properties здесь временно используется debug-подпись только для того,
            // чтобы граф задач сконфигурировался; реальная сборка блокируется ниже
            // (см. `assembleRelease`/`bundleRelease` doFirst), если не передан allowDebugSignedRelease.
            signingConfig = if (hasKeystoreProperties) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Единственное, что осталось в baseline — 7 предупреждений GradleDependency
        // (compose-bom/wear-compose/activity-compose/lifecycle), упирающихся в потолок AGP 8.7.2:
        // более новые версии либо требуют AGP 9.x + compileSdk 37 (activity/core-ktx),
        // либо тянут androidx.compose.runtime >= 1.9, из-за чего сам lint 8.7.2 падает с
        // IncompatibleClassChangeError в RememberInCompositionDetector. Проверено эмпирически
        // 14.07.2026 — при обновлении AGP снять baseline и попробовать актуальные версии снова.
        baseline = file("lint-baseline.xml")

        // По умолчанию values/strings.xml — русский (основной язык приложения); values-ru
        // патчит только несколько случайно оставленных английских строк (например app_name — бренд,
        // намеренно на английском). Это не полноценная EN+RU локализация "шаблон + переводы",
        // поэтому проверка на полное совпадение ключей между values/ и values-ru/ здесь неприменима.
        disable += "MissingTranslation"

        // app_icon.png — единственный источник adaptive-icon foreground layer (432x432,
        // сгенерирован задачей prepareAppIcon), по дизайну без density-вариантов — это
        // рекомендованный Android подход для adaptive icons, а не ошибка.
        disable += "IconLocation"
    }
}

/**
 * Без keystore.properties release-вариант конфигурируется с debug-подписью (см. выше), чтобы не ломать
 * `./gradlew tasks` / IDE sync. Но реально СОБРАТЬ release без релизного ключа — тихая и опасная ошибка
 * (можно случайно выпустить неподписанный релизной подписью APK). Поэтому явно фейлим выполнение
 * assembleRelease/bundleRelease, если ключ не найден, если только не передан флаг для намеренной
 * debug-подписанной локальной тестовой сборки: `-PallowDebugSignedRelease=true`.
 */
val allowDebugSignedRelease = (findProperty("allowDebugSignedRelease") as String?)?.toBoolean() ?: false
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (!hasKeystoreProperties && !allowDebugSignedRelease) {
            throw GradleException(
                "keystore.properties not found — release build would be signed with the DEBUG key.\n" +
                    "Copy keystore.properties.example -> keystore.properties and fill in your signing " +
                    "credentials before building a release.\n" +
                    "To intentionally build a debug-signed release for local testing, pass " +
                    "-PallowDebugSignedRelease=true.",
            )
        }
        if (!hasKeystoreProperties) {
            logger.warn("WARNING: building release APK signed with the DEBUG key (allowDebugSignedRelease=true)")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

/**
 * Стандарт adaptive icon: слой foreground 108 dp → 432×432 px (@xxxhdpi).
 * Исходник масштабируется с center-crop, чтобы символ заполнял область (не «точка» в центре).
 * Корневой icon.jpg → `res/drawable/app_icon.png` (PNG), в XML — @mipmap/ic_launcher.
 */
val launcherIconSource = rootProject.layout.projectDirectory.file("icon.jpg")
val generatedAppIcon = layout.projectDirectory.file("src/main/res/drawable/app_icon.png")

/** 108 dp при плотности xxxhdpi (4×) — рекомендуемый размер слоя adaptive icon. */
private val adaptiveLayerPx = 432

tasks.register("prepareAppIcon") {
    group = "build"
    description = "Генерирует drawable/app_icon.png (432×432, center-crop) из корневого icon.jpg"
    inputs.file(launcherIconSource)
    outputs.file(generatedAppIcon)
    doLast {
        val src = launcherIconSource.asFile
        check(src.exists()) { "Положите icon.jpg в корень проекта (рядом с settings.gradle.kts)" }
        val original = ImageIO.read(src) ?: error("Не удалось прочитать icon.jpg")
        val w = original.width
        val h = original.height
        check(w > 0 && h > 0) { "Некорректный размер изображения" }
        val canvas = adaptiveLayerPx
        // Cover: картинка полностью заполняет квадрат, лишнее обрезается по центру
        val scale = max(canvas.toDouble() / w, canvas.toDouble() / h)
        val tw = max(1, (w * scale).toInt())
        val th = max(1, (h * scale).toInt())
        val scaled = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
        val gs = scaled.createGraphics()
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        gs.drawImage(original, 0, 0, tw, th, null)
        gs.dispose()
        val out = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val x = (canvas - tw) / 2
        val y = (canvas - th) / 2
        g.drawImage(scaled, x, y, null)
        g.dispose()
        generatedAppIcon.asFile.parentFile.mkdirs()
        ImageIO.write(out, "png", generatedAppIcon.asFile)
    }
}

tasks.named("preBuild") {
    dependsOn("prepareAppIcon")
}
