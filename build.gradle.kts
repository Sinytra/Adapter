plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.gradleutils") version ("3.0.0")
}

val versionMc: String by project

gradleutils.version {
    branches {
        suffixBranch()
        suffixExemptedBranches("26.1.x")
    }
}

version = gradleutils.version.toString() + "+$versionMc"

allprojects {
    apply(plugin = "java-library")

    group = "org.sinytra.adapter"

    base {
        archivesName.set(project.name.lowercase())
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    repositories {
        mavenCentral()
        maven("https://maven.su5ed.dev/releases")
        maven("https://maven.fabricmc.net")
    }
    
    dependencies {
        implementation(platform("org.ow2.asm:asm-bom:9.8"))
        implementation("org.ow2.asm:asm")
        implementation("org.ow2.asm:asm-analysis")
        implementation("org.ow2.asm:asm-commons")
        implementation("org.ow2.asm:asm-tree")
        implementation("org.ow2.asm:asm-util")
    }

    if (name != "test") {
        apply(plugin = "maven-publish")

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = project.base.archivesName.get()
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
    }
}
