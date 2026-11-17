import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            afterEvaluate {
                from(components["release"])
            }
            artifact(
                tasks.register<Jar>("dokkaJavadocJar") {
                    archiveClassifier.set("javadoc")
                    from(tasks.named("dokkaGeneratePublicationHtml"))
                },
            )
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
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

tasks.register("dokkaHtml") {
    group = "documentation"
    description = "Generates API documentation in HTML format"
    dependsOn("dokkaGeneratePublicationHtml")
}
