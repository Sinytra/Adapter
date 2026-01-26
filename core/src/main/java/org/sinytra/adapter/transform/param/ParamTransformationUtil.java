package org.sinytra.adapter.transform.param;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

public final class ParamTransformationUtil {
    private static final MethodQualifier WO_ORIGINAL_CALL = new MethodQualifier("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    public static int calculateLVTIndex(List<Type> parameters, boolean nonStatic, int index) {
        int lvt = nonStatic ? 1 : 0;
        for (int i = 0; i < index; i++) {
            lvt += parameters.get(i).getSize();
        }
        return lvt;
    }

    public static List<AbstractInsnNode> findWrapOperationOriginalCall(MethodNode methodNode, MixinContext context) {
        if (context.methodAnnotation().matchesDesc(MixinAnnotations.WRAP_OPERATION)) {
            List<AbstractInsnNode> list = new ArrayList<>();
            outer:
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof MethodInsnNode minsn && WO_ORIGINAL_CALL.matches(minsn)) {
                    for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                        if (prev instanceof LabelNode) {
                            continue outer;
                        }
                        if (AdapterUtil.canHandleLocalVarInsnValue(prev)) {
                            list.add(prev);
                        }
                    }
                }
            }
            return List.copyOf(list);
        }
        return List.of();
    }

    public static List<AbstractInsnNode> findWrapOperationOriginalCallArgs(MethodNode methodNode, MixinContext context) {
        if (context.methodAnnotation().matchesDesc(MixinAnnotations.WRAP_OPERATION)) {
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof MethodInsnNode minsn && WO_ORIGINAL_CALL.matches(minsn)) {
                    return MethodCallAnalyzer.getMethodCallInsns(methodNode, minsn);
                }
            }
        }
        return List.of();
    }
}
