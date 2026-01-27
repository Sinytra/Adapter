plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.gradleutils") version("3.0.0")
}

val versionMc: String by project

group = "org.sinytra.adapter"
gradleutils.version {
    branches {
        suffixBranch()
        suffixExemptedBranches("1.21.x")
    }
}

version = gradleutils.version.toString() + "+$versionMc"
println("Core version: $version")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven {
        name = "Minecraft"
        url = uri("https://libraries.minecraft.net")
    }
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
}

dependencies {
    api(group = "com.mojang", name = "datafixerupper", version = "8.0.16")
    implementation(group = "com.mojang", name = "logging", version = "1.1.1")
    implementation(group = "com.google.guava", "guava", version = "32.1.2-jre")
    implementation(group = "org.slf4j", "slf4j-api", "2.0.0")
    implementation(group = "net.fabricmc", name = "sponge-mixin", version = "0.14.0+mixin.0.8.6")
    compileOnly(group = "org.jetbrains", name = "annotations", version = "24.0.1")
    implementation(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.3.1")

    api(platform("org.ow2.asm:asm-bom:9.8"))
    api(group = "org.ow2.asm", name = "asm")
    api(group = "org.ow2.asm", name = "asm-commons")
    api(group = "org.ow2.asm", name = "asm-tree")
    api(group = "org.ow2.asm", name = "asm-analysis")
    api(group = "org.ow2.asm", name = "asm-util")
}

tasks {
    jar {
        manifest.attributes(
            "Implementation-Version" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
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
