package org.sinytra.adapter.patch.transformer.operation.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;

import java.util.List;

public interface ParameterTransformer {
    Patch.Result apply(final ClassNode classNode, final MethodNode methodNode, final MethodContext methodContext, final PatchContext context, final List<Type> parameters, final int offset);
}
