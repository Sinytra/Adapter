package dev.su5ed.sinytra.adapter.gradle;

import dev.su5ed.sinytra.adapter.patch.Patch;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public class AdapterPlugin implements Plugin<Project> {
    private static final String NEOGRADLE_ID = "net.neoforged.gradle";
    private static final String CLEAN_ARTIFACT = "net.minecraft:joined:%s:srg";

    public static String getDefinitionVersion() {
        return Patch.class.getPackage().getImplementationVersion();
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin(NEOGRADLE_ID, plugin -> applyPlugin(project, plugin));
    }

    private static void applyPlugin(Project project, AppliedPlugin neoGradle) {
        project.getLogger().lifecycle("Applying Sinytra Adapter plugin for {}", neoGradle.getId());

        project.getTasks().register("generateAdapterData", AdapterCompareJarTask.class, task -> {
            task.getCleanJar().fileProvider(project.provider(() -> {
                String mcpVersion = (String) project.getExtensions().getExtraProperties().get("MCP_VERSION");
                return MavenArtifactDownloader.generate(project, CLEAN_ARTIFACT.formatted(mcpVersion), true);
            }));
            task.getDirtyJar().fileProvider(project.provider(() -> getBinpatchedArtifact(project)));
            TaskProvider<GenerateSRG> createSrgToMcp = project.getTasks().named("createSrgToMcp", GenerateSRG.class);
            task.getSrgToMcpMappings().set(createSrgToMcp.flatMap(GenerateSRG::getOutput));
        });
    }

    private static File getBinpatchedArtifact(Project project) {
        String forgeVersion = project.getConfigurations().getByName("minecraft").getDependencies().iterator().next().getVersion();
        String[] parts = forgeVersion.split("_mapped_");
        String path = Artifact.from("net.minecraftforge", "forge", parts[0], "binpatched", "jar").getLocalPath();
        File file = Utils.getCache(project, "minecraft_user_repo").toPath().resolve(path).toFile();
        if (!file.exists()) {
            throw new IllegalStateException("Missing binpatched artifact.");
        }
        return file;
    }
}
