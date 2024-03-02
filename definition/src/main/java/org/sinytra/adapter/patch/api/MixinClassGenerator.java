package org.sinytra.adapter.patch.api;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public interface MixinClassGenerator {
    record GeneratedClass(String originalName, String generatedName, ClassNode node) {}

    Map<String, GeneratedClass> getGeneratedMixinClasses();

    ClassNode getOrGenerateMixinClass(ClassNode original, String targetClass, @Nullable String parent);
}
