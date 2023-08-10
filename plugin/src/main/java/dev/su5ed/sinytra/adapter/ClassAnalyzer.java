package dev.su5ed.sinytra.adapter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClassAnalyzer");

    private final ClassNode cleanNode;
    private final ClassNode dirtyNode;

    public static ClassAnalyzer create(byte[] cleanData, byte[] dirtyData) {
        return new ClassAnalyzer(readClassNode(cleanData), readClassNode(dirtyData));
    }

    private static ClassNode readClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    public ClassAnalyzer(ClassNode cleanNode, ClassNode dirtyNode) {
        this.cleanNode = cleanNode;
        this.dirtyNode = dirtyNode;
    }

    public void analyze() {
        // TODO implement
    }
}
