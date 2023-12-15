package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.api.PatchEnvironment;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;

public class PatchContextImpl implements PatchContext {
    private final ClassNode classNode;
    private final PatchEnvironment environment;
    private final List<Runnable> postApply = new ArrayList<>();

    public PatchContextImpl(ClassNode classNode, PatchEnvironment environment) {
        this.classNode = classNode;
        this.environment = environment;
    }

    @Override
    public ClassNode classNode() {
        return this.classNode;
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
