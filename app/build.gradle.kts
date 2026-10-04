plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.25"
}

android {
    namespace = "com.aurora.browser"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.aurora.browser"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
}
