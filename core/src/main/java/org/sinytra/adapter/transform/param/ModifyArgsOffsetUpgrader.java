package org.sinytra.adapter.transform.param;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModifyArgsOffsetUpgrader {
    private static final MethodQualifier ARGS_GET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "get", "(I)Ljava/lang/Object;");
    private static final MethodQualifier ARGS_SET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "set", "(ILjava/lang/Object;)V");

    public static boolean upgradeAfterParamInsert(MethodNode methodNode, int index) {
        List<AbstractInsnNode> results = MethodAnalyzer.analyzeMethod(methodNode,
            (insn, values) -> (ARGS_GET.matches(insn) || ARGS_SET.matches(insn)) && values.size() > 1,
            (insn, values) -> AdapterUtil.getSingleInsn(values, 1)
        );
        Set<AbstractInsnNode> insns = new HashSet<>();
        for (AbstractInsnNode insn : results) {
            if (insn == null || AdapterUtil.getIntConstValue(insn).isEmpty()) {
                return false;
            }
            insns.add(insn);
        }
        for (AbstractInsnNode insn : insns) {
            int argIndex = AdapterUtil.getIntConstValue(insn).getAsInt();
            if (argIndex >= index) {
                methodNode.instructions.set(insn, AdapterUtil.getIntConstInsn(argIndex + 1));
            }
        }
        return true;
    }
}
