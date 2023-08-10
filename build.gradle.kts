plugins {
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