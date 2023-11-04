package dev.su5ed.sinytra.adapter.patch;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;

public class PatchContext {
    private final ClassNode classNode;
    private final PatchEnvironment environment;
    private final List<Runnable> postApply = new ArrayList<>();

    public PatchContext(ClassNode classNode, PatchEnvironment environment) {
        this.classNode = classNode;
        this.environment = environment;
    }

    public ClassNode getClassNode() {
        return this.classNode;
    }

    public PatchEnvironment getEnvironment() {
        return this.environment;
    }

    public String remap(String reference) {
        return this.environment.remap(this.classNode.name, reference);
    }

    public void postApply(Runnable consumer) {
        this.postApply.add(consumer);
    }

    public void run() {
        this.postApply.forEach(Runnable::run);
    }
}
