package org.sinytra.adapter.edit.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public interface ParameterTransformer {
    PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset);

    ParameterTransformer offset(int offset);
}
