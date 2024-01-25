package dev.su5ed.sinytra.adapter.patch.transformer.param;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public record RemoveParameterTransformer(int index) implements ParameterTransformer {
    public static final Codec<RemoveParameterTransformer> CODEC = Codec.intRange(0, 255)
            .fieldOf("index").xmap(RemoveParameterTransformer::new, RemoveParameterTransformer::index)
            .codec();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int target = this.index() + offset;
        final int lvtIndex = ParameterTransformer.calculateLVTIndex(parameters, !methodContext.isStatic(methodNode), target);

        // Remove the use of the param in a wrapop first to avoid the new LVT messing with the outcome of that
        extractWrapOperation(methodContext, methodNode, parameters, op -> op.removeParameter(target));

        withLVTSnapshot(methodNode, () -> {
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
