package org.sinytra.adapter.transform.param;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.env.util.TypeConstants;

import java.util.Set;
import java.util.LinkedHashSet;

public class ModifyArgsOffsetUpgrader {
    private static final MethodQualifier ARGS_GET = new MethodQualifier(TypeConstants.ARGS_TYPE.getDescriptor(), "get", "(I)Ljava/lang/Object;");
    private static final MethodQualifier ARGS_SET = new MethodQualifier(TypeConstants.ARGS_TYPE.getDescriptor(), "set", "(ILjava/lang/Object;)V");

    public static boolean upgradeAfterParamInsert(MethodNode methodNode, int index) {
        Set<AbstractInsnNode> insns = new LinkedHashSet<>();
        MethodAnalyzer.forEachCall(methodNode, (call, operands) -> {
            if ((ARGS_GET.matches(call) || ARGS_SET.matches(call)) && operands.size() > 1) {
                insns.add(AdapterUtil.getSingleInsn(operands.get(1)));
            }
        });
        if (insns.stream().anyMatch(insn -> insn == null || AdapterUtil.getIntConstValue(insn).isEmpty())) {
            return false;
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
