package org.sinytra.adapter.patch.transformer.operation.unit;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;

public class DisableMixin implements MethodTransform {
    public static final DisableMixin INSTANCE = new DisableMixin();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        methodContext.recordAudit(this, "Remove mixin method");
        context.postApply(() -> classNode.methods.remove(methodNode));
        return Patch.Result.APPLY;
    }
}
