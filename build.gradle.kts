plugins {
    kotlin("jvm") version "2.2.0"
    `maven-publish`
    signing
}

group = "info.scoo-va"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // org.json is part of the Android platform, but bundling makes the SDK
    // usable from plain JVM apps + tests too.
    implementation("org.json:json:20231013")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

// ─── Publishing (Monitor-style) ───
publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "scoova-weather"
            version = project.version.toString()

            pom {
                name.set("Scoova Weather Android SDK")
                description.set(
                    "Open-meteo compatible Kotlin client for weather.scoo-va.info. " +
                    "Current conditions, hourly + daily forecasts, locale-aware."
                )
                url.set("https://github.com/Scoova/scoova-weather-android")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("scoova")
                        name.set("Scoova")
                        email.set("info@scoo-va.info")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Scoova/scoova-weather-android.git")
                    developerConnection.set("scm:git:ssh://github.com:Scoova/scoova-weather-android.git")
                    url.set("https://github.com/Scoova/scoova-weather-android")
                }
            }
        }
    }

    repositories {
        // GitHub Packages (works immediately once GITHUB_TOKEN is exported).
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Scoova/scoova-weather-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as? String ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as? String ?: ""
            }
        }

        // Maven Central via Sonatype OSSRH (when credentials are present).
        maven {
            name = "MavenCentral"
            val releasesUrl  = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: project.findProperty("ossrh.username") as? String ?: ""
                password = System.getenv("OSSRH_PASSWORD") ?: project.findProperty("ossrh.password") as? String ?: ""
            }
        }
    }
}

// GPG signing — required for Maven Central; skipped locally / on GHP.
signing {
    isRequired = gradle.taskGraph.hasTask("publishReleasePublicationToMavenCentralRepository")
    sign(publishing.publications["release"])
}
