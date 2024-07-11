package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;

public interface DynamicFixer<DATA> {
    @Nullable
    DATA prepare(MethodContext methodContext);
    
    Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, DATA data);
}
