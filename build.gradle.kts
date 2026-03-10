group = "ai.altertable.sdk"
version = "0.1.0"

plugins {
    `maven-publish`
    signing
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    google()
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-client-okhttp:2.3.8")
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.ktor:ktor-client-mock:2.3.8")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}

tasks.test {
    useJUnitPlatform()
}


java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Altertable Kotlin SDK")
                description.set("Kotlin SDK for Altertable")
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
                        name.set("Altertable AI")
                        email.set("engineering@altertable.ai")
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
