package dev.su5ed.sinytra.adapter;

import com.google.common.base.Stopwatch;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.slf4j.Logger;

import java.io.IOException;
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

    @TaskAction
    public void execute() {
        final Logger logger = getProject().getLogger();
        
        logger.info("Generating Adapter patch data");
        logger.info("Clean jar: " + getCleanJar().get().getAsFile().getAbsolutePath());
        logger.info("Dirty jar: " + getDirtyJar().get().getAsFile().getAbsolutePath());

        try (final ZipFile cleanJar = new ZipFile(getCleanJar().get().getAsFile());
             final ZipFile dirtyJar = new ZipFile(getDirtyJar().get().getAsFile())
        ) {
            AtomicInteger counter = new AtomicInteger();
            Stopwatch stopwatch = Stopwatch.createStarted();
            
            // TODO Parallel analysis
            dirtyJar.stream().forEach(entry -> {
                logger.info("Processing patched entry {}", entry.getName());

                final ZipEntry cleanEntry = cleanJar.getEntry(entry.getName());
                // Skip classes added by Forge
                if (cleanEntry == null) {
                    return;
                }

                try {
                    byte[] cleanData = cleanJar.getInputStream(cleanEntry).readAllBytes();
                    byte[] dirtyData = dirtyJar.getInputStream(entry).readAllBytes();
                    
                    ClassAnalyzer analyzer = ClassAnalyzer.create(cleanData, dirtyData);
                    analyzer.analyze();
                    
                    counter.getAndIncrement();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            
            stopwatch.stop();
            logger.info("Analyzed {} classes in {} ms", counter.get(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
