import org.sinytra.adapter.gradle.AdapterPlugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("net.neoforged.gradle") version "[6.0,6.2)"
    id("org.sinytra.adapter.gradle")
    `maven-publish`
}

val versionMc: String by project
val versionForge: String by project
val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))

version = "${AdapterPlugin.getDefinitionVersion()?.let { "$it-" } ?: ""}$versionMc-$timestamp"

println("Data version: $version")

allprojects {
    apply(plugin = "net.neoforged.gradle")
    apply(plugin = "maven-publish")

    group = "org.sinytra.adapter"

    base {
        archivesName.set(project.name.lowercase())
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
    }

    minecraft {
        mappings("official", versionMc)
    }

    repositories {
        mavenCentral()
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
    }

    dependencies {
        minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-$versionForge")
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                fg.component(this)
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

tasks {
    jar {
        from(generateAdapterData)
    }
}
