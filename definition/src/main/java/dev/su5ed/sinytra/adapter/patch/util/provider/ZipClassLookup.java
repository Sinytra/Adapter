package dev.su5ed.sinytra.adapter.patch.util.provider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipClassLookup implements ClassLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZipClassLookup");

    private final ZipFile zipFile;
    private final Map<String, Optional<ClassNode>> classCache = new ConcurrentHashMap<>();

    public ZipClassLookup(ZipFile zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public Optional<ClassNode> getClass(String name) {
        return this.classCache.computeIfAbsent(name, this::computeClass);
    }

    protected Optional<ClassNode> computeClass(String name) {
        ZipEntry entry = this.zipFile.getEntry(name + ".class");
        if (entry != null) {
            ClassReader reader;
            try (InputStream is = this.zipFile.getInputStream(entry)) {
                reader = new ClassReader(is);
            } catch (IOException e) {
                LOGGER.error("Error getting class entry {}", name, e);
                return Optional.empty();
            }
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            return Optional.of(node);
        }
        return Optional.empty();
    }
}
