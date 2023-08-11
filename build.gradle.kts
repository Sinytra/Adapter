import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("net.neoforged.gradle") version "[6.0,6.2)"
    id("dev.su5ed.sinytra.adapter.gradle")
    `maven-publish`
}

val versionMc: String by project
val versionForge: String by project
val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))

group = "dev.su5ed.sinytra.adapter"
version = "$versionMc-$timestamp"

println("Project version: $version")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
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

tasks {
    jar {
        from(generateAdapterData.flatMap { it.output })
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            fg.component(this)
        }
    }
    repositories {
        maven {
            name = "Su5eD"
            url = uri("https://maven.su5ed.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: "not"
                password = System.getenv("MAVEN_PASSWORD") ?: "set"
            }
        }
    }
}