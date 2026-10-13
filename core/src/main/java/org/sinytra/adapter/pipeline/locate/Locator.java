package org.sinytra.adapter.pipeline.locate;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public interface Locator {
    List<Candidate> locate(MixinContext context, Recipe recipe, Scope scope, AbstractInsnNode cleanInsn);
}
