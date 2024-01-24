plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.gradleutils").version("3.0.0-alpha.10")
}

group = "dev.su5ed.sinytra.adapter"
gradleutils.version {
    branches {
        suffixBranch()
    }
}

version = gradleutils.version
println("Definition version: $version")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

val testClasses = sourceSets.create("testClasses")

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
    "testClassesImplementation"(implementation(group = "net.fabricmc", name = "sponge-mixin", version = "0.12.5+mixin.0.8.5"))
    compileOnly(group = "org.jetbrains", name = "annotations", version = "24.0.1")
    "testClassesImplementation"(implementation(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.3.1"))

    api(platform("org.ow2.asm:asm-bom:9.5"))
    api(group = "org.ow2.asm", name = "asm")
    api(group = "org.ow2.asm", name = "asm-commons")
    api(group = "org.ow2.asm", name = "asm-tree")
    api(group = "org.ow2.asm", name = "asm-analysis")
    api(group = "org.ow2.asm", name = "asm-util")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.1")

    "testRuntimeOnly"(testClasses.output)
}

tasks {
    jar {
        manifest.attributes(
            "Implementation-Version" to project.version
        )
    }

    test {
        useJUnitPlatform()
        systemProperty("adapter.definition.paramdiff.debug", true)
        outputs.upToDateWhen { false }
    }

    named("compileTestClassesJava", JavaCompile::class.java) {
        options.compilerArgs = listOf("-parameters")
    }

    named("testClasses") {
        dependsOn("compileTestClassesJava")
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
