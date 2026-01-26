package org.sinytra.adapter.transform.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;

import java.util.List;

public interface ParameterTransformer {
    PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset);
}
