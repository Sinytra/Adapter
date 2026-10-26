package org.sinytra.adapter.model;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public record TargetPair(ClassNode classNode, MethodNode methodNode) {
    /// {@return whether both name the same method of the same class, which may come from different class lookups}
    public boolean isSameMethod(TargetPair other) {
        return this.classNode.name.equals(other.classNode.name) && this.methodNode.name.equals(other.methodNode.name)
            && this.methodNode.desc.equals(other.methodNode.desc);
    }
}
