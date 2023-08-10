import net.minecraftforge.gradle.common.util.RunConfig

plugins {
    `java-library`
    id("net.neoforged.gradle") version "[6.0,6.2)"
    `maven-publish`
}

group = "dev.su5ed.sinytra.adapter"
version = "1.0-SNAPSHOT"

val versionMc: String by project
val versionForge: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

minecraft {
    mappings("official", versionMc)

    runs {
        val config = Action<RunConfig> {
            property("forge.logging.console.level", "debug")
            workingDirectory = project.file("run").canonicalPath

            mods {
                create("adapter") {
                    sources(sourceSets.main.get())
                }
            }
        }

        create("client", config)
        create("server", config)
    }
}

configurations {
    runtimeElements {
        outgoing { 
            exclude("net.minecraftforge", "forge")
        }
    }
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
    
    api(group = "com.mojang", name = "datafixerupper", version = "6.0.8")
    implementation(group = "com.mojang", name = "logging", version = "1.1.1")
}

publishing {
    publications { 
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}