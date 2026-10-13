package org.sinytra.adapter.api;

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

    PatchEnvironment environment();

    String remap(String reference);
}
