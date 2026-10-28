import java.io.File
import java.net.URL
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Public TEST-only key. Never use as a trusted production signing certificate.
val sharedTestKey = file("signing/community-debug.jks")
if (!sharedTestKey.isFile) {
    sharedTestKey.parentFile.mkdirs()
    sharedTestKey.writeBytes(
        Base64.getDecoder().decode(
            file("signing/community-debug.jks.base64").readText().trim()
        )
    )
}

android {
    signingConfigs {
        create("communityDebug") {
            storeFile = sharedTestKey
            storePassword = "ocr-open-debug"
            keyAlias = "crashlogocr"
            keyPassword = "ocr-open-debug"
        }
    }
    namespace = "com.alwansan.crashlogocr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alwansan.crashlogocr"
        minSdk = 23
        targetSdk = 34
        versionCode = 6
        versionName = "2.4.0"
        // Only real phones: drops x86 libs from the bundled ML Kit model => smaller APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildFeatures { buildConfig = true }

    // Reproducible public test/debug signing; production key should remain private.
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("communityDebug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Bundled Latin model: works offline from the first launch.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
}

// Download official Arabic and English OCR data for OFFLINE use, including Android Studio builds.
// User-initiated app Settings can refresh each traineddata pack later without replacing the APK.
val trainedData = layout.projectDirectory.dir("src/main/assets/tessdata")
val prepareOcrModels = tasks.register("prepareOcrModels") {
    val codes = listOf("ara", "eng")
    outputs.files(codes.map { trainedData.file("${it}.traineddata") })
    doLast {
        val dir = trainedData.asFile
        check(dir.isDirectory || dir.mkdirs())
        for (code in codes) {
            val destination = File(dir, "${code}.traineddata")
            if (destination.length() >= 100_000L) continue
            val staging = File(dir, "${code}.part")
            val url = URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/${code}.traineddata")
            try {
                val connection = url.openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 75_000
                }
                connection.getInputStream().use { input ->
                    staging.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                check(staging.length() in 100_000L..35_000_000L) { "Invalid $code model" }
                check(staging.renameTo(destination)) { "Cannot install model $code" }
            } finally {
                staging.delete()
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(prepareOcrModels) }
// Force assets to be prepared BEFORE their consuming task, even in parallel Gradle builds.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(prepareOcrModels)
}
