import java.util.Properties

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.sinytra.adapter"
version = "1.2.1-SNAPSHOT"

val versionModDevGradle: String by Properties().also { file("../gradle.properties").bufferedReader().use(it::load) }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

gradlePlugin {
    plugins {
        register("adapter") {
            id = "org.sinytra.adapter.userdev"
            implementationClass = "org.sinytra.adapter.userdev.AdapterUserdevPlugin"
        }
    }
}

repositories { 
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.neoforged.net/releases")
}

dependencies { 
    compileOnly(group = "net.neoforged", name = "moddev-gradle", version = versionModDevGradle)
}

publishing {
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