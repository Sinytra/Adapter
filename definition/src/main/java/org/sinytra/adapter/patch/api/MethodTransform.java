package org.sinytra.adapter.patch.api;

import com.mojang.serialization.Codec;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Set;

public interface MethodTransform {
    default Codec<? extends MethodTransform> codec() {
        throw new UnsupportedOperationException("This transform is not serializable");
    }

    default Collection<String> getAcceptedAnnotations() {
        return Set.of();
    }

    Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context);
}
