package org.sinytra.adapter.next.env;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;

public class MixinContext {
    private final ClassNode classNode;
    private final MethodNode methodNode;
    private final MethodContext methodContext;

    public MixinContext(ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.methodContext = methodContext;
    }

    public ClassNode getClassNode() {
        return this.classNode;
    }

    public MethodNode getMethodNode() {
        return this.methodNode;
    }

    public MethodContext getMethodContext() {
        return this.methodContext;
    }
}
