package org.sinytra.adapter.pipeline.ctx;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.model.ann.RefMapper;

import java.util.List;

public interface PatchContext extends RefMapper {
    static PatchContext create(ClassNode classNode, List<Type> targetTypes, PatchEnvironment environment) {
        return new PatchContextImpl(classNode, targetTypes, environment);
    }

    ClassNode classNode();

    List<Type> targetTypes();

    PatchEnvironment environment();

    void postApply(Runnable consumer);

    void onRollback(Runnable undo);
}
