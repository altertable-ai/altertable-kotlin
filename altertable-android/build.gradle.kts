import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

group = "ai.altertable.sdk"

android {
    namespace = "ai.altertable.sdk.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

spotless {
    kotlin {
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    api(project(":altertable"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)

    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation(libs.kotlin.test)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            afterEvaluate {
                from(components["release"])
            }
            artifactId = "altertable-android"
            pom {
                name.set("Altertable Android SDK")
                description.set("Android SDK for Altertable")
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
    repositories {
        maven {
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKeyId = System.getenv("SIGNING_KEY_ID")
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

tasks.register("dokkaHtml") {
    group = "documentation"
    description = "Generates API documentation in HTML format"
    dependsOn("dokkaGeneratePublicationHtml")
}
