package org.sinytra.adapter.userdev;

import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import net.neoforged.moddevgradle.internal.utils.NetworkSettingPassthrough;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

@DisableCachingByDefault(because = "Uses its own cache")
public abstract class CreateCleanArtifactTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getNeoForgeRuntime();
    
    @Input
    public abstract Property<String> getNeoForgeArtifact();
    
    @OutputDirectory
    public abstract DirectoryProperty getWorkDir();
    
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Internal
    public abstract Property<JavaLauncher> getNeoFormRuntimeLauncher();

    @Inject
    public abstract ExecOperations getExecOpertations();

    @Inject
    public abstract JavaToolchainService getJavaToolchainService();

    public CreateCleanArtifactTask() {
        getNeoFormRuntimeLauncher().convention(getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
    }

    @TaskAction
    public void execute() {
        getExecOpertations().javaexec(spec -> {
            spec.systemProperties(NetworkSettingPassthrough.getNetworkSystemProperties());
            spec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");
            spec.executable(getNeoFormRuntimeLauncher().get().getExecutablePath().getAsFile());
            spec.classpath(getNeoForgeRuntime());
            spec.args("run", "--dist=joined", "--neoforge", getNeoForgeArtifact().get() + ":userdev");
            spec.args("--write-result=vanillaDeobfuscated:" + getOutputFile().get().getAsFile().getAbsolutePath());
            spec.args("--work-dir", getWorkDir().get().getAsFile().getAbsolutePath());
            if (IdeDetection.isIntelliJ() || IdeDetection.isEclipse()) {
                spec.args("--emojis");
            }
        });
    }
}
