package org.sinytra.adapter.api;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.pipeline.ctx.PatchContext;

public interface ClassTransformer {
    PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context);
}
