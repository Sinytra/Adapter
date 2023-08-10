package dev.su5ed.sinytra.adapter.patch;

import org.objectweb.asm.tree.ClassNode;

public interface ClassTransform {
    Result apply(ClassNode classNode);

    record Result(boolean applied, boolean computeFrames) {
    }
}
