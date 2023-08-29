package dev.su5ed.sinytra.adapter.gradle.provider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipClassProvider implements ClassProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZipClassProvider");
    
    private final ZipFile zipFile;
    private final Map<String, ClassNode> classCache = new HashMap<>();

    public ZipClassProvider(ZipFile zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public Optional<ClassNode> getClass(String name) {
        ClassNode classNode = this.classCache.computeIfAbsent(name, str -> {
            ZipEntry entry = this.zipFile.getEntry(str + ".class");
            if (entry != null) {
                ClassReader reader;
                try (InputStream is = this.zipFile.getInputStream(entry)) {
                    reader = new ClassReader(is);
                } catch (IOException e) {
                    LOGGER.error("Error getting class entry {}", str, e);
                    return null;
                }
                ClassNode node = new ClassNode();
                reader.accept(node, 0);
                return node;
            }
            return null;
        });
        return Optional.ofNullable(classNode);
    }
}
