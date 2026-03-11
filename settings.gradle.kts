rootProject.name = "altertable-kotlin"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":altertable")

val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true
if (hasAndroidSdk) {
    include(":altertable-android")
}

include(":example-app")
project(":example-app").projectDir = file("Examples/app")
