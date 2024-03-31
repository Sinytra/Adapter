package org.sinytra.adapter.patch.transformer.param;

import com.mojang.serialization.Codec;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;

import java.util.List;

public interface ParameterTransformer {
    Patch.Result apply(final ClassNode classNode, final MethodNode methodNode, final MethodContext methodContext, final PatchContext context, final List<Type> parameters, final int offset);

    default Codec<? extends ParameterTransformer> codec() {
        throw new UnsupportedOperationException("This transform is not serializable");
    }
}
