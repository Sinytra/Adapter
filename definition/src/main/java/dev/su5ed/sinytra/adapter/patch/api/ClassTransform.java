package dev.su5ed.sinytra.adapter.patch.api;

import dev.su5ed.sinytra.adapter.patch.api.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

public interface ClassTransform {
    Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context);
}
