package dev.su5ed.sinytra.adapter.patch.api;

import dev.su5ed.sinytra.adapter.patch.PatchContextImpl;
import org.objectweb.asm.tree.ClassNode;

public interface PatchContext {
    static PatchContext create(ClassNode classNode, PatchEnvironment environment) {
        return new PatchContextImpl(classNode, environment);
    }
    
    ClassNode classNode();
    
    PatchEnvironment environment();
    
    String remap(String reference);
    
    void postApply(Runnable consumer);
}
