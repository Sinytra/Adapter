package org.sinytra.adapter.next.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.patch.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.next.env.ctx.PatchResult;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static org.sinytra.adapter.patch.util.AdapterUtil.MIXINPATCH;

public record InlineParameterTransformer(int target, Consumer<InstructionAdapter> adapter) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        final int index = this.target + offset;
        LOGGER.info(MIXINPATCH, "Inlining parameter {} of method {}.{}", index, classNode.name, methodNode.name);
        final int replaceIndex = -999 + index;

        LVTSnapshot.with(methodNode, () -> {
            if (index < methodNode.parameters.size()) {
                methodNode.parameters.remove(index);
            }

            methodNode.localVariables.sort(Comparator.comparingInt(lvn -> lvn.index));
            LocalVariableNode lvn = methodNode.localVariables.remove(index + (context.isStatic() ? 0 : 1));
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

        return PatchResult.COMPUTE_FRAMES;
    }
}
