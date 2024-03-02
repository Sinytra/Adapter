package org.sinytra.adapter.patch.api;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.patch.PatchContextImpl;

import java.util.List;

public interface PatchContext {
    static PatchContext create(ClassNode classNode, List<Type> targetTypes, PatchEnvironment environment) {
        return new PatchContextImpl(classNode, targetTypes, environment);
    }

    ClassNode classNode();

    List<Type> targetTypes();

    PatchEnvironment environment();

    String remap(String reference);

    void postApply(Runnable consumer);
}
