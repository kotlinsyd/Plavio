import java.security.MessageDigest

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
        versionCode = 91
        versionName = "0.11.4"
    }

    // Publishing is explicit and must never silently use an unsigned/debug artifact.
    if (providers.gradleProperty("plavioPublish").orNull == "true") {
        listOf("PLAVIO_SIGNING_STORE", "PLAVIO_SIGNING_STORE_PASSWORD",
            "PLAVIO_SIGNING_KEY_ALIAS", "PLAVIO_SIGNING_KEY_PASSWORD").forEach { key ->
            require(!providers.gradleProperty(key).orNull.isNullOrBlank()) { "Publishing requires $key" }
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false // Update advisories are reviewed, not silently auto-upgraded.
    }
    signingConfigs {
        // Optional owner signing; no machine-specific keystore is required to build.
        val signingStore = providers.gradleProperty("PLAVIO_SIGNING_STORE").orNull
        if (!signingStore.isNullOrBlank()) {
            create("plavioOwner") {
                storeFile = file(signingStore)
                storePassword = providers.gradleProperty("PLAVIO_SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("PLAVIO_SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("PLAVIO_SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("plavioOwner") ?: signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.findByName("plavioOwner")
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
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("org.checkerframework:checker-qual:3.42.0")
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
    implementation(libs.media3.common)
    implementation(libs.media3.decoder)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    // TEST: okhttp-datasource removed for offline build
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.nanohttpd)
    // Background episode-release checks (WorkManager, battery-friendly periodic work).
    implementation(libs.androidx.work.runtime.ktx)
    // Google Drive sync via AccountManager + REST API (no Play Services needed)
    // (user's order: replaces Supabase with Google login + Drive sync)
    // Optional crash reporting; disabled until explicit device-local consent.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    // Optional analytics; disabled until explicit device-local consent.
    implementation("com.google.firebase:firebase-analytics")
    // YouTube trailer extraction for the Trailers tab (same as ShortsBox).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    ksp(libs.room.compiler)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Exact artifact inventory complements notices and the owner's license review.
tasks.register("writeMobileDependencyInventory") {
    group = "verification"
    description = "Write mobile runtime dependency coordinates and SHA-256 checksums"
    doLast {
        val output = layout.buildDirectory.file("reports/mobile-dependencies.tsv").get().asFile
        output.parentFile.mkdirs()
        val artifacts = configurations.getByName("mobileDebugRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
        output.bufferedWriter().use { writer ->
            writer.appendLine("coordinate\tartifact\tsha256")
            artifacts.forEach { artifact ->
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(artifact.file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                writer.appendLine("${artifact.moduleVersion.id}\t${artifact.file.name}\t$digest")
            }
        }
        println("Mobile dependency inventory: ${output.relativeTo(project.rootDir)}")
    }
}
