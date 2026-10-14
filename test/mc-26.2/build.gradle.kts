plugins {
    id("net.neoforged.moddev")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

neoForge {
    version = "26.2.0.90"

    addModdingDependenciesTo(sourceSets.getByName("testClasses"))
    
    unitTest {
        enable()
    }
}

dependencies {
    testImplementation("org.sinytra.adapter:core") {
        isTransitive = false
    }

    testImplementation(project(":test"))
}
