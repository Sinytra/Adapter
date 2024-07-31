package org.sinytra.adapter.gradle;

import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.userdev.CreateBinpatchedArtifactTask;

public class AdapterPlugin implements Plugin<Project> {
    private static final String ADAPTER_USERDEV_ID = "org.sinytra.adapter.gradle";

    public static String getDefinitionVersion() {
        return Patch.class.getPackage().getImplementationVersion();
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ADAPTER_USERDEV_ID);
        project.getPluginManager().withPlugin(ADAPTER_USERDEV_ID, plugin -> applyPlugin(project, plugin));
    }

    private static void applyPlugin(Project project, AppliedPlugin plugin) {
        project.getLogger().lifecycle("Applying Sinytra Adapter plugin for {}", plugin.getId());

        NeoForgeExtension neoForge = project.getExtensions().getByType(NeoForgeExtension.class);
        TaskProvider<CreateBinpatchedArtifactTask> createBinpatchedArtifactTask = project.getTasks().named("createBinpatchedArtifact", CreateBinpatchedArtifactTask.class); 

        project.getTasks().register("generateAdapterData", AdapterCompareJarTask.class, task -> {
            task.getCleanJar().fileProvider(neoForge.getNeoFormRuntime().getAdditionalResults().map(p -> p.get("vanillaDeobfuscated")));
            task.getDirtyJar().set(createBinpatchedArtifactTask.flatMap(CreateBinpatchedArtifactTask::getOutputFile));
        });
    }
}
