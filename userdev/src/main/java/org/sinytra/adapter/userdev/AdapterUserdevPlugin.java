package org.sinytra.adapter.userdev;

import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;

public class AdapterUserdevPlugin implements Plugin<Project> {
    private static final String MDG_ID = "net.neoforged.moddev";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(MDG_ID);
        project.getPluginManager().withPlugin(MDG_ID, plugin -> applyPlugin(project));
    }

    public static void applyPlugin(Project project) {
        NeoForgeExtension neoForge = project.getExtensions().getByType(NeoForgeExtension.class);
        Property<String> neoForgeVersion = neoForge.getVersion();

        Configuration neoForgeUserdevArtifact = project.getConfigurations().create("neoForgeUserdevArtifact", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false);
            spec.withDependencies(dependencies -> dependencies.addLater(project.provider(() -> project.getDependencyFactory().create("net.neoforged:neoforge:%s:userdev".formatted(neoForgeVersion.get())))));
        });

        Configuration neoForgeBinpatchRuntime = project.getConfigurations().create("neoForgeBinpatchRuntime", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.withDependencies(dependencies -> dependencies.addLater(project.provider(() -> project.getDependencyFactory().create("net.neoforged.installertools:binarypatcher:2.1.2"))));
        });

        TaskProvider<CreateCleanArtifactTask> createCleanArtifact = project.getTasks().register("createCleanArtifact", CreateCleanArtifactTask.class, task -> {
            task.setGroup("sinytra");
            task.getNeoForgeRuntime().from(project.getConfigurations().named("neoFormRuntime"));
            task.getNeoForgeArtifact().set(neoForgeVersion.map(v -> "net.neoforged:neoforge:" + v));
            task.getWorkDir().set(project.getLayout().getBuildDirectory().dir("tmp/neoformruntime"));
            task.getOutputFile().set(project.file("build/%s/minecraft-renamed.jar".formatted(task.getName())));
        });

        TaskProvider<ExtractBinPatches> extractBinPatches = project.getTasks().register("extractBinPatches", ExtractBinPatches.class, task -> {
            task.setGroup("sinytra");
            task.getInputFile().set(neoForgeUserdevArtifact.getSingleFile());
            task.getOutputFile().set(project.file("build/%s/joined.lzma".formatted(task.getName())));
        });

        // Used by org.sinytra.adapter.gradle plugin
        TaskProvider<CreateBinpatchedArtifactTask> createBinpatchedArtifact = project.getTasks().register("createBinpatchedArtifact", CreateBinpatchedArtifactTask.class, task -> {
            task.dependsOn("extractBinPatches");
            task.setGroup("sinytra");
            task.getRuntime().from(neoForgeBinpatchRuntime);
            task.getRenamedInput().set(createCleanArtifact.flatMap(CreateCleanArtifactTask::getOutputFile));
            task.getPatches().set(extractBinPatches.flatMap(ExtractBinPatches::getOutputFile));
            task.getOutputFile().set(project.file("build/%s/minecraft-binpatched.jar".formatted(task.getName())));
        });

        // Attach clean artifact path to run configs
        neoForge.getRuns().configureEach(runModel -> {
            runModel.systemProperty("connector.clean.path", createCleanArtifact.get().getOutputFile().get().getAsFile().getAbsolutePath());
            project.getTasks().named(InternalModelHelper.nameOfRun(runModel, "prepare", "run")).configure(task -> {
                task.dependsOn(createCleanArtifact);
            });
        });
    }
}
