package org.sinytra.adapter.userdev;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.*;

@CacheableTask
public abstract class ExtractBinPatches extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getPatchFileName();

    public ExtractBinPatches() {
        getPatchFileName().convention("joined.lzma");
    }

    @TaskAction
    public void execute() throws IOException {
        FileSystem fs = FileSystems.newFileSystem(getInputFile().get().getAsFile().toPath());
        Path patches = fs.getPath(getPatchFileName().get());
        Files.copy(patches, getOutputFile().get().getAsFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
