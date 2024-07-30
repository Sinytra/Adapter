plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.sinytra.adapter"
version = "1.1-SNAPSHOT"

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
    maven("https://maven.neoforged.net/releases")
}

dependencies { 
    compileOnly("net.neoforged:moddev-gradle:1.0.15")
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