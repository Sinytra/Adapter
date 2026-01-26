package org.sinytra.adapter.next.transform;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ctx.PatchContext;
import org.sinytra.adapter.next.env.ctx.PatchResult;

public interface ClassTransformer {
    PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context);
}
