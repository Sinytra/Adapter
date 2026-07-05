pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/")
        }
        maven {
            name = "Minecraft"
            url = uri("https://libraries.minecraft.net")
        }
        maven {
            name = "FabricMC"
            url = uri("https://maven.fabricmc.net")
        }
    }

    val versionModDevGradle: String by settings
  
    plugins {
        id("net.neoforged.moddev") version versionModDevGradle
    }
}

rootProject.name = "Adapter"

includeBuild("core")
include("runtime", "test")

include("test:mc-1.21.1")
project(":test:mc-1.21.1").projectDir = file("test/mc-1.21.1")