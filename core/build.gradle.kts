plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.gradleutils") version("3.0.0")
}

val versionMc = project.property("versionMc") as String

group = "org.sinytra.adapter"
gradleutils.version {
    branches {
        suffixBranch()
        suffixExemptedBranches("26.1.x")
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
    api("com.mojang:datafixerupper:8.0.16")
    implementation("com.mojang:logging:1.1.1")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("org.slf4j:slf4j-api:2.0.0")
    implementation("net.fabricmc:sponge-mixin:0.14.0+mixin.0.8.6")
    implementation("io.github.llamalad7:mixinextras-common:0.3.1")
    compileOnly("org.jetbrains:annotations:24.0.1")

    api(platform("org.ow2.asm:asm-bom:9.8"))
    api("org.ow2.asm:asm")
    api("org.ow2.asm:asm-commons")
    api("org.ow2.asm:asm-tree")
    api("org.ow2.asm:asm-analysis")
    api("org.ow2.asm:asm-util")
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
