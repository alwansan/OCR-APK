plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alwansan.crashlogocr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alwansan.crashlogocr"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"
        // Only real phones: drops x86 libs from the bundled ML Kit model => smaller APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildFeatures { buildConfig = true }

    // Debug builds use Android Gradle Plugin's standard debug signing.
    // Never store release signing keys or passwords in a public repository.
    buildTypes {
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
}
