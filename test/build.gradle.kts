plugins {
    id("net.neoforged.moddev")
}

neoForge {
    version = "21.1.234"

    unitTest {
        enable()
    }
}

allprojects {
    apply(plugin = "net.neoforged.moddev")
    
    val testClasses: SourceSet by sourceSets.creating

    configurations {
        named("testClassesCompileClasspath") {
            extendsFrom(testCompileClasspath.get())
        }
    }

    val requestedOutput = file("build/createCleanArtifact/minecraft-renamed.jar")
    
    neoForge {
        additionalMinecraftArtifacts.put("vanillaDeobfuscated", requestedOutput)
    }

    dependencies {
        implementation(group = "org.sinytra.adapter", name = "core")

        implementation(platform("org.junit:junit-bom:5.9.1"))
        implementation("org.junit.jupiter:junit-jupiter")
        implementation("org.assertj:assertj-core:3.25.1")

        testImplementation(group = "org.sinytra.adapter", name = "core")

        testImplementation(project(":runtime"))
        testImplementation(platform("org.junit:junit-bom:5.9.1"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.assertj:assertj-core:3.25.1")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        testRuntimeOnly(testClasses.output)
    }

    tasks {
        test {
            useJUnitPlatform()
            systemProperty("adapter.core.paramdiff.debug", true)
            systemProperty("adapter.clean.path", requestedOutput)
            systemProperty("forge.logging.console.level", "debug")
            outputs.upToDateWhen { false }
        }

        named("compileTestClassesJava", JavaCompile::class.java) {
            options.compilerArgs = listOf("-parameters")
        }

        named("testClasses") {
            dependsOn("compileTestClassesJava")
        }
    }
}

subprojects {
    neoForge {
        mods {
            create("adapterTest$name") {
                sourceSet(sourceSets.test.get())
            }
        }

        unitTest {
            testedMod.set(mods.named("adapterTest$name"))
        }
    }
}