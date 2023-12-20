package dev.su5ed.sinytra.adapter.patch.api;

import dev.su5ed.sinytra.adapter.patch.PatchContextImpl;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

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
