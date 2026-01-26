package org.sinytra.adapter.next.transform.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ctx.PatchResult;

import java.util.List;

public interface ParameterTransformer {
    PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset);
}
