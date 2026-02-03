package org.sinytra.adapter.transform.param;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;

public class ModifyArgsOffsetUpgrader {
    private static final MethodQualifier ARGS_GET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "get", "(I)Ljava/lang/Object;");
    private static final MethodQualifier ARGS_SET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "set", "(ILjava/lang/Object;)V");

    public static void upgradeAfterParamInsert(MethodNode methodNode, int index) {
        List<AbstractInsnNode> insns = MethodAnalyzer.analyzeMethod(methodNode,
            (insn, values) -> (ARGS_GET.matches(insn) || ARGS_SET.matches(insn)) && values.size() > 1,
            (insn, values) -> AdapterUtil.getSingleInsn(values, 1)
        );
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof IntInsnNode iinsn) {
                if (index >= iinsn.operand) {
                    iinsn.operand += 1;
                }
            } else if (insn instanceof InsnNode iinsn) {
                int insnIndex = AdapterUtil.getInsnIntConstValue(iinsn);
                if (index >= insnIndex) {
                    methodNode.instructions.set(insn, AdapterUtil.getIntConstInsn(insnIndex + 1));
                }
            } else {
                throw new UnsupportedOperationException("Whoopsie! We can't handle " + insn.getClass().getName() + " instructions just yet!");
            }
        }
    }
}
