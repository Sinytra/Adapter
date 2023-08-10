import net.minecraftforge.gradle.common.util.RunConfig

plugins {
    id("java")
    id("net.neoforged.gradle") version "[6.0,6.2)"
    id("dev.su5ed.sinytra.adapter")
}

group = "dev.su5ed.sinytra"
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

repositories {
    mavenCentral()
    maven {
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net/")
    }
}

dependencies {
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-$versionForge")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}