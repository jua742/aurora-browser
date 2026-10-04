buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

apply(plugin = "com.android.application")
apply(plugin = "kotlin-android")

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
