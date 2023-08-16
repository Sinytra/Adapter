plugins {
    `java-library`
    `maven-publish`
}

group = "dev.su5ed.sinytra.adapter"
version = "1.0.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven {
        name = "Minecraft"
        url = uri("https://libraries.minecraft.net")
    }
}

dependencies {
    api(group = "com.mojang", name = "datafixerupper", version = "6.0.8")
    implementation(group = "com.mojang", name = "logging", version = "1.1.1")
    implementation(group = "com.google.guava", "guava", version = "32.1.2-jre")
    implementation(group = "org.slf4j", "slf4j-api", "2.0.0")
    compileOnly(group = "org.jetbrains", name = "annotations", version = "24.0.1")

    api(platform("org.ow2.asm:asm-bom:9.5"))
    api(group = "org.ow2.asm", name = "asm")
    api(group = "org.ow2.asm", name = "asm-commons")
    api(group = "org.ow2.asm", name = "asm-tree")
    api(group = "org.ow2.asm", name = "asm-analysis")
    api(group = "org.ow2.asm", name = "asm-util")
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
                username = System.getenv("MAVEN_USERNAME") ?: "not"
                password = System.getenv("MAVEN_PASSWORD") ?: "set"
            }
        }
    }
}