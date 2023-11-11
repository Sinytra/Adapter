plugins {
    `java-gradle-plugin`
}

group = "dev.su5ed.sinytra.adapter"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

gradlePlugin {
    plugins {
        register("adapter") {
            id = "dev.su5ed.sinytra.adapter.gradle"
            implementationClass = "dev.su5ed.sinytra.adapter.gradle.AdapterPlugin"
        }
    }
}

repositories {
    mavenCentral()
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

dependencies {
    implementation(group = "net.neoforged", name = "NeoGradle", version = "6.0.+")
    implementation(group = "net.minecraftforge", name = "srgutils", version = "0.5.3")
    compileOnly(group = "net.neoforged", "artifactural", version = "3.0.17")

    implementation(group = "dev.su5ed.sinytra.adapter", name = "definition")
    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.13.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}