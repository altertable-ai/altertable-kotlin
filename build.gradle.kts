plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.agp.application) apply false
}

allprojects {
    group = "ai.altertable.sdk"
    version = providers.gradleProperty("sdk.version").get()

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
    }
}
