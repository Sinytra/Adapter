package org.sinytra.adapter.patch.transformer.param;

import com.mojang.serialization.Codec;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.transformer.LVTSnapshot;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.List;

import static org.sinytra.adapter.patch.transformer.param.ParamTransformationUtil.extractWrapOperation;

public record RemoveParameterTransformer(int index) implements ParameterTransformer {
    public static final Codec<RemoveParameterTransformer> CODEC = Codec.intRange(0, 255)
            .fieldOf("index").xmap(RemoveParameterTransformer::new, RemoveParameterTransformer::index)
            .codec();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int target = this.index() + offset;
        final int lvtIndex = ParamTransformationUtil.calculateLVTIndex(parameters, !methodContext.isStatic(), target);

        // Remove the use of the param in a wrapop first to avoid the new LVT messing with the outcome of that
        extractWrapOperation(methodContext, methodNode, parameters, op -> op.removeParameter(target));

        LVTSnapshot.with(methodNode, () -> {
            LocalVariableNode lvn = methodNode.localVariables.stream()
                    .filter(v -> v.index == lvtIndex)
                    .findFirst()
                    .orElse(null);
            if (lvn != null) {
                methodNode.localVariables.remove(lvn);
                AdapterUtil.replaceLVT(methodNode, idx -> idx == lvtIndex ? -1 : idx);
            }
        });

        methodNode.parameters.remove(target);
        parameters.remove(target);

        return Patch.Result.COMPUTE_FRAMES;
    }
}
