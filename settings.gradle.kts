pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/")
        }
    }
}

rootProject.name = "adapter"

includeBuild("plugin")