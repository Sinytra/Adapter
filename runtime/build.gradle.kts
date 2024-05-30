val runtimeVersion: String by project
val versionMc: String by project

version = "$runtimeVersion+$versionMc"

println("Runtime version: $version")

dependencies {
    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.6")!!)
}

tasks.jar {
    manifest {
        manifest.attributes(
            "Implementation-Version" to project.version,
            "MixinConfigs" to "adapter.init.mixins.json",
            "FMLModType" to "GAMELIBRARY"
        )
    }
}
