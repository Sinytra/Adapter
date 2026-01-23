package org.sinytra.adapter.patch.transformer.operation.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.api.PatchResult;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.List;

public record RemoveParameterTransformer(int index, boolean invalidateUsage) implements ParameterTransformer {
    public RemoveParameterTransformer(int index) {
        this(index, true);
    }

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int target = this.index() + offset;
        final int lvtIndex = ParamTransformationUtil.calculateLVTIndex(parameters, !methodContext.isStatic(), target);

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

        methodNode.parameters.remove(target);
        methodNode.visibleParameterAnnotations = AdapterUtil.removeArrayElement(methodNode.visibleParameterAnnotations, this.index, List[]::new);
        methodNode.invisibleParameterAnnotations = AdapterUtil.removeArrayElement(methodNode.invisibleParameterAnnotations, this.index, List[]::new);
        parameters.remove(target);

        return PatchResult.COMPUTE_FRAMES;
    }
}
