plugins {
    `java-library`
    `maven-publish`
}

group = "dev.su5ed.sinytra.adapter"
version = "1.7.1"

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
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
}

dependencies {
    api(group = "com.mojang", name = "datafixerupper", version = "6.0.8")
    implementation(group = "com.mojang", name = "logging", version = "1.1.1")
    implementation(group = "com.google.guava", "guava", version = "32.1.2-jre")
    implementation(group = "org.slf4j", "slf4j-api", "2.0.0")
    implementation(group = "net.fabricmc", name = "sponge-mixin", version = "0.12.5+mixin.0.8.5")
    compileOnly(group = "org.jetbrains", name = "annotations", version = "24.0.1")

    api(platform("org.ow2.asm:asm-bom:9.5"))
    api(group = "org.ow2.asm", name = "asm")
    api(group = "org.ow2.asm", name = "asm-commons")
    api(group = "org.ow2.asm", name = "asm-tree")
    api(group = "org.ow2.asm", name = "asm-analysis")
    api(group = "org.ow2.asm", name = "asm-util")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    jar {
        manifest.attributes(
            "Implementation-Version" to project.version
        )
    }

    test {
        useJUnitPlatform()
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
                username = System.getenv("MAVEN_USERNAME") ?: "not"
                password = System.getenv("MAVEN_PASSWORD") ?: "set"
            }
        }
    }
}