package org.sinytra.adapter.userdev;

import net.neoforged.moddevgradle.internal.utils.NetworkSettingPassthrough;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

@CacheableTask
public abstract class CreateBinpatchedArtifactTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getRuntime();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRenamedInput();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPatches();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Internal
    public abstract Property<JavaLauncher> getNeoFormRuntimeLauncher();

    @Inject
    public abstract ExecOperations getExecOpertations();

    @Inject
    public abstract JavaToolchainService getJavaToolchainService();

    public CreateBinpatchedArtifactTask() {
        getNeoFormRuntimeLauncher().convention(getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
    }

    @TaskAction
    public void execute() {
        getExecOpertations().javaexec(spec -> {
            spec.systemProperties(NetworkSettingPassthrough.getNetworkSystemProperties());
            spec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");
            spec.executable(getNeoFormRuntimeLauncher().get().getExecutablePath().getAsFile());
            spec.classpath(getRuntime());
            spec.getMainClass().set("net.neoforged.binarypatcher.ConsoleTool");
            spec.args("--clean", getRenamedInput().get().getAsFile().getAbsolutePath());
            spec.args("--output", getOutputFile().get().getAsFile().getAbsolutePath());
            spec.args("--apply", getPatches().get().getAsFile().getAbsolutePath());
        });
    }
}
