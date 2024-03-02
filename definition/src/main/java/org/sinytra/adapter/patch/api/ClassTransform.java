package org.sinytra.adapter.patch.api;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.patch.api.Patch.Result;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;

public interface ClassTransform {
    Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context);
}
