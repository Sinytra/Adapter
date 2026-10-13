package org.sinytra.adapter.pipeline.resolver.rewrite;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.ArrayList;
import java.util.List;

public final class InstanceofReplacement {
    @Nullable
    public static TypeInsnNode find(MixinContext context, Recipe recipe) {
        Targets targets = recipe.targets();
        if (targets == null
            || !(recipe.getCleanInjectionInsn() instanceof MethodInsnNode cleanCall)
            || !Type.getReturnType(cleanCall.desc).equals(Type.BOOLEAN_TYPE)
            || !(nextReal(cleanCall) instanceof JumpInsnNode)
        ) {
            return null;
        }

        List<TypeInsnNode> found = MethodAlignment.align(targets.clean().methodNode(), targets.dirty().methodNode()).findDirtyAround(cleanCall).stream()
            .filter(insn -> insn.getOpcode() == Opcodes.INSTANCEOF && nextReal(insn) instanceof JumpInsnNode)
            .map(TypeInsnNode.class::cast)
            .toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    public static List<AbstractInsnNode> findCondition(TypeInsnNode instanceOf) {
        List<AbstractInsnNode> insns = new ArrayList<>(InsnTraversal.untilLabel(instanceOf, InsnTraversal.BACKWARD).reversed());
        insns.add(instanceOf);
        return insns;
    }

    public static List<TypeInsnNode> findChecks(MethodNode method, String type) {
        List<TypeInsnNode> checks = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn.getOpcode() == Opcodes.INSTANCEOF && ((TypeInsnNode) insn).desc.equals(type)) {
                checks.add((TypeInsnNode) insn);
            }
        }
        return checks;
    }

    @Nullable
    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        return InsnTraversal.find(insn, InsnTraversal.FORWARD, i -> i.getOpcode() >= 0);
    }

    private InstanceofReplacement() {
    }
}
