package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;

public interface ClassTransform {
    Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchEnvironment environment);
}
