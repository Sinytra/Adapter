package org.sinytra.adapter.api;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.pipeline.ctx.PatchContextImpl;

import java.util.List;

public interface ClassPatchContext {
    static ClassPatchContext of(ClassNode classNode, List<Type> targetTypes, PatchEnvironment environment) {
        return new PatchContextImpl(classNode, null, targetTypes, environment);
    }

    ClassNode classNode();

    List<Type> targetTypes();

    @Nullable
    default Type singleTarget() {
        List<Type> types = targetTypes();
        return types.size() == 1 ? types.getFirst() : null;
    }

    PatchEnvironment environment();

    String remap(String reference);
}
