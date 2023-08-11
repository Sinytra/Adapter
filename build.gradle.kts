@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

plugins {
    id("net.neoforged.gradle") version "[6.0,6.2)"
    id("dev.su5ed.sinytra.adapter.gradle")
}

val versionMc: String by project
val versionForge: String by project
val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))

group = "dev.su5ed.sinytra"
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
        doLast { 
            val fs = FileSystems.newFileSystem(archiveFile.get().asFile.toPath(), mapOf<String, Any>())
            val path = fs.getPath("META-INF")
            path.deleteRecursively()
        }
    }
}