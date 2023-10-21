package dev.su5ed.sinytra.adapter.patch.fixes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.util.Constants;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.*;

public class BytecodeFixerJarGenerator {
    private static final long ZIP_TIME = 318211200000L;
    private static final String MIXIN_CONFIG_NAME = "adapter.mixins.json";
    private static final String PACAKGE = "dev/su5ed/sinytra/connector/adapter/fieldtypepatch/mixin";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, ClassNode> generatedClasses = new HashMap<>();

    public void loadExisting(Path path) {
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(path))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    byte[] bytes = jis.readAllBytes();
                    ClassReader reader = new ClassReader(bytes);
                    ClassNode node = new ClassNode();
                    reader.accept(node, 0);
                    this.generatedClasses.put(node.name, node);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error opening jar", e);
        }
    }

    public boolean save(Path path, Attributes additionalAttributes) {
        if (this.generatedClasses.isEmpty()) {
            return false;
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(Constants.ManifestAttributes.MIXINCONFIGS, MIXIN_CONFIG_NAME);
        manifest.getMainAttributes().putAll(additionalAttributes);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(path.toFile()), manifest)) {
            for (Map.Entry<String, ClassNode> entry : this.generatedClasses.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey() + ".class");
                jarEntry.setTime(ZIP_TIME);
                jos.putNextEntry(jarEntry);

                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                entry.getValue().accept(cw);
                byte[] bytes = cw.toByteArray();
                jos.write(bytes);

                jos.closeEntry();
            }

            JarEntry configEntry = new JarEntry(MIXIN_CONFIG_NAME);
            configEntry.setTime(ZIP_TIME);
            jos.putNextEntry(configEntry);
            jos.write(generateMixinConfig());
            jos.closeEntry();
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ClassNode getOrCreateClass(String name, Function<String, ClassNode> generator) {
        return this.generatedClasses.computeIfAbsent(PACAKGE + "/" + name, generator);
    }

    private byte[] generateMixinConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("required", true);
        json.addProperty("minVersion", "0.8.5");
        json.addProperty("package", PACAKGE.replace('/', '.'));
        json.addProperty("compatibilityLevel", "JAVA_17");

        JsonArray array = new JsonArray();
        for (String name : this.generatedClasses.keySet()) {
            String shortName = name.replace(PACAKGE + "/", "");
            array.add(shortName.replace('/', '.'));
        }
        json.add("mixins", array);

        String jsonString = GSON.toJson(json);
        return jsonString.getBytes(StandardCharsets.UTF_8);
    }
}
