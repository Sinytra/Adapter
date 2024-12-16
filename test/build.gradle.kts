plugins {
    id("net.neoforged.moddev")
    id("org.sinytra.adapter.userdev")
    idea
}

val testClasses: SourceSet by sourceSets.creating

neoForge {
    mods {
        create("adapterTest")
    }

    unitTest {
        enable()
        testedMod.set(mods.named("adapterTest"))
    }
}

configurations {
    named("testClassesCompileClasspath") {
        extendsFrom(testCompileClasspath.get())
    }
}

dependencies {
    testCompileOnly(group = "org.sinytra.adapter", name = "definition")
    "neoForgeTestLibraries"(group = "org.sinytra.adapter", name = "definition") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testRuntimeOnly(testClasses.output)
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("adapter.definition.paramdiff.debug", true)
        systemProperty("adapter.clean.path", neoForge.additionalMinecraftArtifacts.getting("vanillaDeobfuscated").get().absolutePath)
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
