plugins {
    id("net.neoforged.moddev")
}

neoForge {
    version = "21.1.234"

    unitTest {
        enable()
    }
}

dependencies {
    "neoForgeTestLibraries"("org.sinytra.adapter:core") {
        isTransitive = false
    }

    testImplementation(project(":test"))
}
