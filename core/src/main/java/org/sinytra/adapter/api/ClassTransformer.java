package org.sinytra.adapter.api;

import org.objectweb.asm.tree.ClassNode;

public interface ClassTransformer {
    PatchResult apply(ClassNode classNode, ClassPatchContext context);
}
