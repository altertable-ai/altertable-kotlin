@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish)
}

group = "ai.altertable.sdk"

kotlin {
    jvmToolchain(17)
    explicitApi()
    abiValidation {
        enabled = true
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)
}

tasks.test {
    useJUnitPlatform()
    exclude("**/IntegrationTest*")
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs integration tests (requires altertable-mock at localhost:15001)"
    useJUnitPlatform()
    include("**/IntegrationTest*")
}

tasks.named("check") {
    dependsOn(integrationTest, "checkLegacyAbi", "koverVerify")
}

kover {
    reports {
        verify {
            rule {
                minBound(70)
            }
        }
    }
}

tasks.register("dokkaHtml") {
    group = "documentation"
    description = "Generates API documentation in HTML format"
    dependsOn("dokkaGeneratePublicationHtml")
}

val generateVersion =
    tasks.register("generateVersion") {
        val outputDir = layout.buildDirectory.dir("generated/version/kotlin")
        val sdkVersion = project.version.toString()
        inputs.property("sdkVersion", sdkVersion)
        outputs.dir(outputDir)
        doLast {
            val packageDir = outputDir.get().asFile.resolve("ai/altertable/sdk")
            packageDir.mkdirs()
            packageDir.resolve("Version.kt").writeText(
                """
                package ai.altertable.sdk

                internal const val SDK_VERSION = "$sdkVersion"
                """.trimIndent(),
            )
        }
    }

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateVersion)
    }
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(
                tasks.register<Jar>("dokkaJavadocJar") {
                    archiveClassifier.set("javadoc")
                    from(tasks.named("dokkaGeneratePublicationHtml"))
                },
            )
            artifactId = "altertable-kotlin"
            pom {
                name.set("Altertable Kotlin SDK")
                description.set("Core Kotlin SDK for Altertable")
                url.set("https://github.com/altertable-ai/altertable-kotlin")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("altertable-ai")
                        name.set("Altertable")
                        email.set("contact@altertable.ai")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/altertable-ai/altertable-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com/altertable-ai/altertable-kotlin.git")
                    url.set("https://github.com/altertable-ai/altertable-kotlin")
                }
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
