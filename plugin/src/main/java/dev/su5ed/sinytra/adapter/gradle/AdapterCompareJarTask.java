package dev.su5ed.sinytra.adapter.gradle;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.su5ed.sinytra.adapter.gradle.provider.ClassProvider;
import dev.su5ed.sinytra.adapter.gradle.provider.ZipClassProvider;
import dev.su5ed.sinytra.adapter.patch.LVTOffsets;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.serialization.PatchSerialization;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
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

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSrgToMcpMappings();

    @OutputFile
    public abstract RegularFileProperty getPatchDataOutput();

    @OutputFile
    public abstract RegularFileProperty getLVTOffsetDataOutput();

    public AdapterCompareJarTask() {
        Provider<Directory> outputDir = getProject().getLayout().getBuildDirectory().dir(getName());
        getPatchDataOutput().convention(outputDir.map(dir -> dir.file("patch_data.json")));
        getLVTOffsetDataOutput().convention(outputDir.map(dir -> dir.file("lvt_offsets.json")));
    }

    @TaskAction
    public void execute() throws IOException {
        final Logger logger = getProject().getLogger();

        logger.info("Generating Adapter patch data");
        logger.info("Clean jar: " + getCleanJar().get().getAsFile().getAbsolutePath());
        logger.info("Dirty jar: " + getDirtyJar().get().getAsFile().getAbsolutePath());
        logger.info("Mappings : " + getSrgToMcpMappings().get().getAsFile().getAbsolutePath());

        List<PatchInstance> patches = new ArrayList<>();
        Multimap<ChangeCategory, String> info = HashMultimap.create();
        Map<String, String> replacementCalls = new HashMap<>();
        Map<String, Map<MethodQualifier, List<LVTOffsets.Offset>>> offsets = new HashMap<>();
        Map<String, Map<MethodQualifier, List<LVTOffsets.Swap>>> reorders = new HashMap<>();

        IMappingFile mappings = IMappingFile.load(getSrgToMcpMappings().get().getAsFile());

        try (final ZipFile cleanJar = new ZipFile(getCleanJar().get().getAsFile());
             final ZipFile dirtyJar = new ZipFile(getDirtyJar().get().getAsFile())
        ) {
            ClassProvider cleanClassProvider = new ZipClassProvider(cleanJar);
            ClassProvider dirtyClassProvider = new ZipClassProvider(dirtyJar);
            AtomicInteger counter = new AtomicInteger();
            Stopwatch stopwatch = Stopwatch.createStarted();

            Collection<ClassAnalyzer> analyzers = new ArrayList<>();
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

                    ClassAnalyzer analyzer = ClassAnalyzer.create(cleanData, dirtyData, mappings, cleanClassProvider, dirtyClassProvider);
                    analyzers.add(analyzer);
                    analyzer.analyze(patches, info, replacementCalls, offsets, reorders);

                    counter.getAndIncrement();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            List<PatchInstance> postPatches = new ArrayList<>();
            logger.info("");
            logger.info("===== Running post-analysis =====");
            for (ClassAnalyzer analyzer : analyzers) {
                analyzer.postAnalyze(postPatches, replacementCalls);
            }
            logger.info("Adding additonal {} patches from post-analysis", postPatches.size());
            patches.addAll(postPatches);

            stopwatch.stop();
            logger.info("Analyzed {} classes in {} ms", counter.get(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

            logger.info("Generated {} patches", patches.size());

            logger.info("\n{} fields had their type changed", info.get(ChangeCategory.MODIFY_FIELD).size());
            info.get(ChangeCategory.MODIFY_FIELD).forEach(logger::info);
            logger.info("\n{} fields were added", info.get(ChangeCategory.ADD_FIELD).size());
            info.get(ChangeCategory.ADD_FIELD).forEach(logger::info);
            logger.info("\n{} fields were removed", info.get(ChangeCategory.REMOVE_FIELD).size());
            info.get(ChangeCategory.REMOVE_FIELD).forEach(logger::info);
        }

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonElement patchDataJson = PatchSerialization.serialize(patches, JsonOps.INSTANCE);
        String patchDataJsonStr = gson.toJson(patchDataJson);
        Files.writeString(getPatchDataOutput().get().getAsFile().toPath(), patchDataJsonStr, StandardCharsets.UTF_8);

        LVTOffsets lvtOffsets = new LVTOffsets(offsets, reorders);
        JsonElement offsetJson = lvtOffsets.toJson();
        String offsetJsonStr = gson.toJson(offsetJson);
        Files.writeString(getLVTOffsetDataOutput().get().getAsFile().toPath(), offsetJsonStr, StandardCharsets.UTF_8);
    }
}
