import org.sinytra.adapter.gradle.AdapterPlugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("net.neoforged.moddev") version "1.0.15"
    id("org.sinytra.adapter.userdev")
    id("org.sinytra.adapter.gradle")
    `maven-publish`
}

val versionMc: String by project
val versionNeoForge: String by project
val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))

version = "${AdapterPlugin.getDefinitionVersion()?.let { "$it-" } ?: "$versionMc-"}$timestamp"

println("Data version: $version")

allprojects {
    apply(plugin = "net.neoforged.moddev")

    group = "org.sinytra.adapter"

    base {
        archivesName.set(project.name.lowercase())
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    neoForge {
        version = versionNeoForge
    }

    repositories {
        mavenCentral()
        maven("https://maven.su5ed.dev/releases")
    }

    if (name !== "test") {
        apply(plugin = "maven-publish")

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = project.base.archivesName.get()
                }
            }
            repositories {
                maven {
                    name = "Su5eD"
                    url = uri("https://maven.su5ed.dev/releases")
                    credentials {
                        username = System.getenv("MAVEN_USER") ?: "not"
                        password = System.getenv("MAVEN_PASSWORD") ?: "set"
                    }
                }
            }
        }
    }
}

tasks {
    jar {
        from(generateAdapterData)
    }
}
