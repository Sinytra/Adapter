package org.sinytra.adapter.pipeline.ctx;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.api.PatchEnvironment;

import java.util.ArrayList;
import java.util.List;

public class PatchContextImpl implements PatchContext {
    private final ClassNode classNode;
    private final List<Type> targetTypes;
    private final PatchEnvironment environment;
    private final List<Runnable> postApply = new ArrayList<>();
    private final List<Runnable> undo = new ArrayList<>();

    public record Checkpoint(int postApply, int undo) {
    }

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

    @Override
    public void onRollback(Runnable undo) {
        this.undo.add(undo);
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(this.postApply.size(), this.undo.size());
    }

    public void rollback(Checkpoint checkpoint) {
        this.postApply.subList(checkpoint.postApply(), this.postApply.size()).clear();
        List<Runnable> undone = this.undo.subList(checkpoint.undo(), this.undo.size());
        undone.reversed().forEach(Runnable::run);
        undone.clear();
    }

    public void run() {
        this.postApply.forEach(Runnable::run);
    }
}
