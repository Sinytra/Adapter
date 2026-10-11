package org.sinytra.adapter.edit.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.edit.LVTSnapshot;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.List;

public record RemoveParameterTransformer(int index, boolean invalidateUsage) implements ParameterTransformer {
    public RemoveParameterTransformer(int index) {
        this(index, true);
    }

    @Override
    public RemoveParameterTransformer offset(int offset) {
        return new RemoveParameterTransformer(this.index + offset, this.invalidateUsage);
    }

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        final int target = this.index() + offset;
        if (target >= parameters.size()) {
            return PatchResult.PASS;
        }
        final int lvtIndex = ParamTransformationUtil.getLVTIndex(parameters, !context.isStatic(), target);

        LVTSnapshot.with(methodNode, () -> {
            LocalVariableNode lvn = methodNode.localVariables.stream()
                .filter(v -> v.index == lvtIndex)
                .findFirst()
                .orElse(null);
            if (lvn != null) {
                methodNode.localVariables.remove(lvn);
                if (this.invalidateUsage) {
                    AdapterUtil.replaceLVT(methodNode, idx -> idx == lvtIndex ? -1 : idx);
                }
            }
        });

        ParamTransformationUtil.updateParameterAnnotationArrays(methodNode, parameters.size(), annotations -> annotations.remove(target));
        parameters.remove(target);

        return PatchResult.COMPUTE_FRAMES;
    }
}
