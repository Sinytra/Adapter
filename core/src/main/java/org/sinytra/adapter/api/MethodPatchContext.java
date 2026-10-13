package org.sinytra.adapter.api;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public interface MethodPatchContext {
    ClassNode classNode();

    MethodNode methodNode();

    AnnotationNode injectorAnnotation();

    PatchEnvironment environment();

    void recordAudit(Object actor, String message, Object... args);
}
