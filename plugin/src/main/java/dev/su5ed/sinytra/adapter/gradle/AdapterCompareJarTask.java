package dev.su5ed.sinytra.adapter.gradle;

import com.google.common.base.Stopwatch;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.su5ed.sinytra.adapter.patch.PatchImpl;
import dev.su5ed.sinytra.adapter.patch.PatchSerialization;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CacheableTask
public abstract class AdapterCompareJarTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCleanJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDirtyJar();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    public AdapterCompareJarTask() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("output.json")));
    }

    @TaskAction
    public void execute() throws IOException {
        final Logger logger = getProject().getLogger();

        logger.info("Generating Adapter patch data");
        logger.info("Clean jar: " + getCleanJar().get().getAsFile().getAbsolutePath());
        logger.info("Dirty jar: " + getDirtyJar().get().getAsFile().getAbsolutePath());

        List<PatchImpl> patches = new ArrayList<>();

        try (final ZipFile cleanJar = new ZipFile(getCleanJar().get().getAsFile());
             final ZipFile dirtyJar = new ZipFile(getDirtyJar().get().getAsFile())
        ) {
            AtomicInteger counter = new AtomicInteger();
            Stopwatch stopwatch = Stopwatch.createStarted();

            // TODO Parallel analysis
            dirtyJar.stream().forEach(entry -> {
                logger.debug("Processing patched entry {}", entry.getName());

                final ZipEntry cleanEntry = cleanJar.getEntry(entry.getName());
                // Skip classes added by Forge
                if (cleanEntry == null) {
                    return;
                }

                try {
                    byte[] cleanData = cleanJar.getInputStream(cleanEntry).readAllBytes();
                    byte[] dirtyData = dirtyJar.getInputStream(entry).readAllBytes();

                    ClassAnalyzer analyzer = ClassAnalyzer.create(cleanData, dirtyData);
                    ClassAnalyzer.AnalysisResults analysis = analyzer.analyze();
                    patches.addAll(analysis.patches());

                    counter.getAndIncrement();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            stopwatch.stop();
            logger.info("Analyzed {} classes in {} ms", counter.get(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

            logger.info("Found {} overloaded methods", patches.size());
        }

        JsonElement object = PatchSerialization.serialize(patches, JsonOps.INSTANCE);
        String jsonStr = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(object);
        Files.writeString(getOutput().get().getAsFile().toPath(), jsonStr, StandardCharsets.UTF_8);
    }
}
