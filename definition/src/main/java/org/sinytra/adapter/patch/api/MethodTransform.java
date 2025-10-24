package org.sinytra.adapter.patch.api;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Set;

public interface MethodTransform {
    default Collection<String> getAcceptedAnnotations() {
        return Set.of();
    }

    default Patch.Result apply(MethodContext methodContext) {
        return apply(methodContext.getMixinClass(), methodContext.getMixinMethod(), methodContext, methodContext.patchContext());
    }

    Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context);
}
