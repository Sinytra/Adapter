package org.sinytra.adapter.patch;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.api.PatchEnvironment;

import java.util.ArrayList;
import java.util.List;

public class PatchContextImpl implements PatchContext {
    private final ClassNode classNode;
    private final List<Type> targetTypes;
    private final PatchEnvironment environment;
    private final List<Runnable> postApply = new ArrayList<>();

    public PatchContextImpl(ClassNode classNode, List<Type> targetTypes, PatchEnvironment environment) {
        this.classNode = classNode;
        this.targetTypes = targetTypes;
        this.environment = environment;
    }

    @Override
    public ClassNode classNode() {
        return this.classNode;
    }

    @Override
    public List<Type> targetTypes() {
        return this.targetTypes;
    }

    @Override
    public PatchEnvironment environment() {
        return this.environment;
    }

    @Override
    public String remap(String reference) {
        return this.environment.refmapHolder().remap(this.classNode.name, reference);
    }

    @Override
    public void postApply(Runnable consumer) {
        this.postApply.add(consumer);
    }

    public void run() {
        this.postApply.forEach(Runnable::run);
    }
}
