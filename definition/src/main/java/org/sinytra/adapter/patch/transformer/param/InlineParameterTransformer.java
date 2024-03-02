package org.sinytra.adapter.patch.transformer.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record InlineParameterTransformer(int target, Consumer<InstructionAdapter> adapter) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int index = this.target + offset;
        LOGGER.info(MIXINPATCH, "Inlining parameter {} of method {}.{}", index, classNode.name, methodNode.name);
        final int replaceIndex = -999 + index;

        withLVTSnapshot(methodNode, () -> {
            if (index < methodNode.parameters.size()) {
                methodNode.parameters.remove(index);
            }

            methodNode.localVariables.sort(Comparator.comparingInt(lvn -> lvn.index));
            LocalVariableNode lvn = methodNode.localVariables.remove(index + (methodContext.isStatic(methodNode) ? 0 : 1));
            AdapterUtil.replaceLVT(methodNode, idx -> idx == lvn.index ? replaceIndex : idx);
        });

        parameters.remove(index);

        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == replaceIndex) {
                InsnList replacementInsns = AdapterUtil.insnsWithAdapter(adapter);
                methodNode.instructions.insert(varInsn, replacementInsns);
                methodNode.instructions.remove(varInsn);
            }
        }

        return Patch.Result.COMPUTE_FRAMES;
    }
}
