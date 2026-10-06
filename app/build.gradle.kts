import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // PLACEHOLDER — replace with your own before any public release (spec A-5).
    namespace = "com.aurora.browser"
    // compileSdk 34: AGP 8.5.2 supports up to API 34 (8.6.0+ is required for 35).
    compileSdk = 34

    defaultConfig {
        // PLACEHOLDER — replace with your own before any public release (spec A-5).
        applicationId = "com.aurora.browser"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing reads local.properties (NEVER committed). See README "Release signing".
    // In CI (no local.properties) we skip signing config entirely — an incomplete
    // signing config assigned to the release build type fails AGP configuration.
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val hasReleaseKeystore = !localProperties.getProperty("storeFile").isNullOrBlank()
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(localProperties.getProperty("storeFile")!!)
                storePassword = localProperties.getProperty("storePassword")
                keyAlias = localProperties.getProperty("keyAlias")
                keyPassword = localProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Debuggable, auto-signed with the debug key. Installs straight onto a phone.
        }
        getByName("release") {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Lifecycle + ViewModel (StateFlow UI state, no Hilt in v1)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // System WebView compat (algorithmic darkening, safe browsing backports)
    implementation(libs.androidx.webkit)

    // Room (bookmarks, history, downloads, tabs, site permissions)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // DataStore Preferences (all user settings)
    implementation(libs.androidx.datastore.preferences)

    // Coil — favicons only
    implementation(libs.coil.compose)

    // Compose BOM: material3, ui, icons versions all managed here
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    // Full icon set (core lacks several icons the app uses, e.g. InsertDriveFile,
    // PictureAsPdf, Movie — unresolved references would fail the build).
    implementation(libs.androidx.material.icons.extended)
}
