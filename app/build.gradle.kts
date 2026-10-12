plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.pulsestream.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.pulsestream.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 59
        versionName = "0.8.29"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
        }
        create("tv") {
            dimension = "device"
            // Plavio TV: separate Leanback app identity for Android TV / Google TV.
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
            // TV's own version sequence (com.pulsestream.app.tv).
            versionCode = 61
            versionName = "0.8.31"
            resValue("string", "app_name", "Plavio TV")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.nanohttpd)
    // Crash reporting (Firebase Crashlytics) — automatic, no extra permissions.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    // Usage stats (Firebase Analytics) — active/total users dashboard, automatic.
    implementation("com.google.firebase:firebase-analytics")
    // YouTube trailer extraction for the Trailers tab (same as ShortsBox).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    ksp(libs.room.compiler)
}
