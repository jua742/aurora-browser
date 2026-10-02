// Top-level build file. All dependency/plugin versions live in gradle/libs.versions.toml.
//
// NOTE: the Google OSS-licenses Gradle plugin has no plugin-portal marker, so it
// cannot be applied via the plugins DSL / version catalog. It is applied the way
// Google documents: buildscript classpath here, then `apply(plugin = ...)` in app/.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.ksp) apply false
}
