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
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.wearalarmsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wearalarmsync"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "2.1.14"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
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
            signingConfig = if (keystorePropertiesFile.exists()) {
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
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
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
