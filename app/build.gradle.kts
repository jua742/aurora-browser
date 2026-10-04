plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.21"
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
