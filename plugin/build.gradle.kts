plugins {
    `java-gradle-plugin`
}

group = "org.sinytra.adapter"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

gradlePlugin {
    plugins {
        register("adapter") {
            id = "org.sinytra.adapter.gradle"
            implementationClass = "org.sinytra.adapter.gradle.AdapterPlugin"
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
    implementation(group = "org.sinytra.adapter", name = "definition")
    implementation(group = "org.sinytra.adapter", name = "userdev")
    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.13.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}