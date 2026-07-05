plugins {
    id("net.neoforged.moddev")
    id("net.neoforged.gradleutils") version ("3.0.0")
    `maven-publish`
}

val versionMc: String by project
val versionNeoForge: String by project

gradleutils.version {
    branches {
        suffixBranch()
        suffixExemptedBranches("1.21.x")
    }
}

version = gradleutils.version.toString() + "+$versionMc"

allprojects {
    apply(plugin = "net.neoforged.moddev")

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

    if (!project.path.startsWith(":test")) {
        neoForge {
            version = versionNeoForge
        }
    }

    repositories {
        mavenCentral()
        maven("https://maven.su5ed.dev/releases")
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
