plugins {
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

gradlePlugin {
    plugins {
        register("adapter") {
            id = "dev.su5ed.sinytra.adapter"
            implementationClass = "dev.su5ed.sinytra.adapter.AdapterPlugin"
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/")
    }
}

dependencies {
    implementation(group = "net.neoforged", name = "NeoGradle", version = "6.0.+")
    compileOnly(group = "net.neoforged", "artifactural", version = "3.0.17")

    implementation(group = "com.google.guava", "guava", version = "32.1.2-jre")
    api(platform("org.ow2.asm:asm-bom:9.5"))
    api(group = "org.ow2.asm", name = "asm")
    api(group = "org.ow2.asm", name = "asm-commons")
    api(group = "org.ow2.asm", name = "asm-tree")
    api(group = "org.ow2.asm", name = "asm-analysis")
    api(group = "org.ow2.asm", name = "asm-util")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}